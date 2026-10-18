package com.triiiplox.iptv;

import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.Program;
import com.triiiplox.iptv.net.CachingTransport;
import com.triiiplox.iptv.net.Transport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The catalog of an account, cached on disk: an Xtream panel, or a plain M3U playlist read as a
 * catalog. The first {@link #snapshot()} reads the cached lists (instant) or downloads them;
 * {@link #refresh()} downloads them again. A list the panel fails to send keeps its cached copy;
 * a kind the panel does not have (no series) is empty. Blocking: call it off the main thread.
 */
public final class Catalog {
	public static final long LIST_TTL = 6 * 3600_000L;
	public static final long DETAILS_TTL = 7 * 24 * 3600_000L;
	public static final long EPG_TTL = 10 * 60_000L;
	private static final Details NO_DETAILS =
			new Details(null, null, null, null, null, 0, 0f, null, null, null, null);
	private final XtreamAccount account;
	private final String playlistUrl;
	private final CachingTransport cache;
	private final XtreamClient client;
	private final XtreamClient refreshing;
	private volatile Snapshot snapshot;
	private volatile Map<String, Details> playlistSeries = Collections.emptyMap();

	public Catalog(XtreamAccount account, Transport net, File cacheDir) {
		this.account = account;
		playlistUrl = null;
		cache = new CachingTransport(net, cacheDir, Catalog::ttl);
		client = new XtreamClient(account, cache);
		refreshing = new XtreamClient(account, url -> cache.open(url, true));
	}

	private Catalog(String playlistUrl, Transport net, File cacheDir) {
		account = null;
		this.playlistUrl = playlistUrl;
		cache = new CachingTransport(net, cacheDir, url -> LIST_TTL);
		client = null;
		refreshing = null;
	}

	/** The catalog of a plain M3U playlist: no login, no details beyond the list, no guide. */
	public static Catalog playlist(String url, Transport net, File cacheDir) {
		return new Catalog(url, net, cacheDir);
	}

	public boolean isPlaylist() {
		return playlistUrl != null;
	}

	/** The Xtream account, null for a playlist. */
	public XtreamAccount getAccount() {
		return account;
	}

	/** The Xtream client, null for a playlist. */
	public XtreamClient getClient() {
		return client;
	}

	/** The loaded snapshot, or null before the first load. */
	public Snapshot peek() {
		return snapshot;
	}

	public Snapshot snapshot() throws IOException {
		Snapshot s = snapshot;
		if (s != null) return s;
		synchronized (this) {
			if (snapshot == null) snapshot = isPlaylist() ? loadPlaylist(false) : load(client);
			return snapshot;
		}
	}

	public synchronized Snapshot refresh() throws IOException {
		return snapshot = isPlaylist() ? loadPlaylist(true) : load(refreshing);
	}

	/** True if the lists on disk are older than their freshness: worth a refresh in background. */
	public boolean isStale() {
		long age = cache.age(isPlaylist() ? playlistUrl : account.apiUrl("get_vod_streams"));
		return (age < 0) || (age > LIST_TTL);
	}

	public Details details(Entry e) throws IOException {
		if (isPlaylist()) {
			if (e.kind == Kind.SERIES) {
				snapshot();
				Details d = playlistSeries.get(e.id);
				return (d != null) ? d : NO_DETAILS;
			}
			return NO_DETAILS;
		}
		return switch (e.kind) {
			case MOVIE -> client.movieDetails(e.id);
			case SERIES -> client.seriesDetails(e.id);
			default -> throw new IllegalArgumentException(e.kind.code);
		};
	}

	/** The guide of a channel: empty for a playlist, which has none of its own. */
	public List<Program> epg(String streamId, int limit) throws IOException {
		return isPlaylist() ? Collections.emptyList() : client.shortEpg(streamId, limit);
	}

	public void clearCache() {
		cache.clear();
		snapshot = null;
		playlistSeries = Collections.emptyMap();
	}

	private Snapshot loadPlaylist(boolean refresh) throws IOException {
		M3uPlaylist p;
		try (InputStream in = cache.open(playlistUrl, refresh)) {
			p = M3uPlaylist.parse(in);
		}
		playlistSeries = p.series;
		return p.snapshot(System.currentTimeMillis());
	}

	/**
	 * Loads the three kinds at once: on a first load the lists are big downloads from a slow panel,
	 * and one after the other they add up. A kind that fails is empty; all failing is an error.
	 */
	private static Snapshot load(XtreamClient c) throws IOException {
		Kind[] kinds = {Kind.LIVE, Kind.MOVIE, Kind.SERIES};
		ExecutorService exec = Executors.newFixedThreadPool(kinds.length, r -> {
			Thread t = new Thread(r, "Xtream catalog");
			t.setDaemon(true);
			return t;
		});
		Map<Kind, Future<Part>> parts = new EnumMap<>(Kind.class);
		for (Kind k : kinds) parts.put(k, exec.submit(() -> loadKind(c, k)));
		exec.shutdown();

		Map<Kind, List<Category>> cats = new EnumMap<>(Kind.class);
		Map<Kind, List<? extends Entry>> entries = new EnumMap<>(Kind.class);
		IOException failure = null;
		int loaded = 0;

		for (Kind k : kinds) {
			Part p;
			try {
				p = parts.get(k).get();
				loaded++;
			} catch (InterruptedException ex) {
				exec.shutdownNow();
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("Catalog load interrupted");
			} catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof IOException io) {
					if (failure == null) failure = io;
					p = new Part(new ArrayList<>(), new ArrayList<>());
				} else if (cause instanceof RuntimeException re) {
					throw re;
				} else {
					throw new IllegalStateException(cause);
				}
			}
			cats.put(k, p.categories);
			entries.put(k, p.entries);
		}

		// Nothing at all: the panel refused or cannot be reached, and nothing is cached
		if (loaded == 0) throw failure;
		return new Snapshot(cats, entries, System.currentTimeMillis());
	}

	private static Part loadKind(XtreamClient c, Kind k) throws IOException {
		List<Category> cats = c.categories(k);
		List<? extends Entry> entries = switch (k) {
			case LIVE -> c.liveChannels();
			case MOVIE -> c.movies();
			default -> c.series();
		};
		return new Part(cats, entries);
	}

	private static final class Part {
		final List<Category> categories;
		final List<? extends Entry> entries;

		Part(List<Category> categories, List<? extends Entry> entries) {
			this.categories = categories;
			this.entries = entries;
		}
	}

	private static long ttl(String url) {
		int i = url.indexOf("&action=");
		if (i < 0) return 0; // account info: always fresh
		String a = url.substring(i + 8);
		int j = a.indexOf('&');
		if (j >= 0) a = a.substring(0, j);
		return switch (a) {
			case "get_vod_info", "get_series_info" -> DETAILS_TTL;
			case "get_short_epg", "get_simple_data_table" -> EPG_TTL;
			default -> LIST_TTL;
		};
	}
}
