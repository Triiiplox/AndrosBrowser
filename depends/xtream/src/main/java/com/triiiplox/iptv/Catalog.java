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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The catalog of an account, cached on disk. The first {@link #snapshot()} reads the cached
 * lists (instant) or downloads them; {@link #refresh()} downloads them again. A list the panel
 * fails to send keeps its cached copy; a kind the panel does not have (no series) is empty.
 * Blocking: call it off the main thread.
 */
public final class Catalog {
	public static final long LIST_TTL = 6 * 3600_000L;
	public static final long DETAILS_TTL = 7 * 24 * 3600_000L;
	public static final long EPG_TTL = 10 * 60_000L;
	private final XtreamAccount account;
	private final CachingTransport cache;
	private final XtreamClient client;
	private final XtreamClient refreshing;
	private volatile Snapshot snapshot;

	public Catalog(XtreamAccount account, Transport net, File cacheDir) {
		this.account = account;
		cache = new CachingTransport(net, cacheDir, Catalog::ttl);
		client = new XtreamClient(account, cache);
		refreshing = new XtreamClient(account, url -> cache.open(url, true));
	}

	public XtreamAccount getAccount() {
		return account;
	}

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
			if (snapshot == null) snapshot = load(client);
			return snapshot;
		}
	}

	public synchronized Snapshot refresh() throws IOException {
		return snapshot = load(refreshing);
	}

	/** True if the lists on disk are older than their freshness: worth a refresh in background. */
	public boolean isStale() {
		long age = cache.age(account.apiUrl("get_vod_streams"));
		return (age < 0) || (age > LIST_TTL);
	}

	public Details details(Entry e) throws IOException {
		return switch (e.kind) {
			case MOVIE -> client.movieDetails(e.id);
			case SERIES -> client.seriesDetails(e.id);
			default -> throw new IllegalArgumentException(e.kind.code);
		};
	}

	public List<Program> epg(String streamId, int limit) throws IOException {
		return client.shortEpg(streamId, limit);
	}

	public void clearCache() {
		cache.clear();
		snapshot = null;
	}

	private static Snapshot load(XtreamClient c) throws IOException {
		Map<Kind, List<Category>> cats = new EnumMap<>(Kind.class);
		Map<Kind, List<? extends Entry>> entries = new EnumMap<>(Kind.class);
		IOException failure = null;
		int loaded = 0;

		for (Kind k : new Kind[]{Kind.LIVE, Kind.MOVIE, Kind.SERIES}) {
			try {
				cats.put(k, c.categories(k));
				entries.put(k, switch (k) {
					case LIVE -> c.liveChannels();
					case MOVIE -> c.movies();
					default -> c.series();
				});
				loaded++;
			} catch (IOException ex) {
				if (failure == null) failure = ex;
				cats.put(k, new ArrayList<>());
				entries.put(k, new ArrayList<>());
			}
		}

		// Nothing at all: the panel refused or cannot be reached, and nothing is cached
		if (loaded == 0) throw failure;
		return new Snapshot(cats, entries, System.currentTimeMillis());
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
