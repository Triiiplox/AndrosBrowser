package com.triiiplox.iptv;

import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Details.Episode;
import com.triiiplox.iptv.model.Details.Season;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.LiveChannel;
import com.triiiplox.iptv.model.Movie;
import com.triiiplox.iptv.model.Series;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An M3U playlist read as a catalog: its channels, movies and series, by the group of each line.
 * A line is a movie when the list says so (tvg-type, or a /movie/ path) or when it points to a
 * video file; a line named like "Show S01E02" (or under a /series/ path) is an episode, and the
 * episodes of a show make one series with its seasons. Everything else is a live channel.
 * <p>
 * The ids are made from the stream URLs and the show names, so they stay the same from one
 * download of the list to the next: the history and "My list" keep pointing at the right items.
 */
public final class M3uPlaylist {
	/** The category of the lines without a group. */
	public static final String NO_GROUP = "Outros";
	private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");
	private static final Pattern EPISODE = Pattern.compile(
			"^(.*?)[\\s._\\-:|(\\[]*[Ss](\\d{1,2})\\s*[._\\-]?\\s*[EeXx](\\d{1,3})\\b[\\s._\\-:|)\\]]*(.*)$");
	private static final String[] VIDEO_EXT = {"mp4", "mkv", "avi", "mov", "m4v", "wmv", "webm"};
	public final Map<Kind, List<Category>> categories = new EnumMap<>(Kind.class);
	public final Map<Kind, List<? extends Entry>> entries = new EnumMap<>(Kind.class);
	/** The seasons and episodes of the series, by series id. */
	public final Map<String, Details> series;

	private M3uPlaylist(Map<Kind, List<Category>> cats, Map<Kind, List<? extends Entry>> entries,
											Map<String, Details> series) {
		categories.putAll(cats);
		this.entries.putAll(entries);
		this.series = Collections.unmodifiableMap(series);
	}

	public Snapshot snapshot(long loadedAt) {
		return new Snapshot(categories, entries, loadedAt);
	}

	/** Reads the list, UTF-8, from a stream the caller closes. */
	public static M3uPlaylist parse(InputStream in) throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536);
		Builder b = new Builder();
		String info = null;
		String group = null;

		for (String line; (line = r.readLine()) != null; ) {
			// The byte order mark, at the start of the file or of lists joined together
			if (!line.isEmpty() && (line.charAt(0) == '\uFEFF')) line = line.substring(1);
			line = line.trim();
			if (line.isEmpty()) continue;

			if (line.startsWith("#")) {
				if (startsWithIgnoreCase(line, "#EXTINF")) {
					info = line;
					group = null;
				} else if (startsWithIgnoreCase(line, "#EXTGRP:")) {
					group = line.substring(8).trim();
				}
				continue;
			}

			// A URL: the stream of the last #EXTINF, or a bare line without one. Text that is no URL
			// (an HTML login page sent instead of the list) is not a stream.
			if (!line.contains("://")) {
				info = null;
				continue;
			}
			b.add(line, info, group);
			info = null;
			group = null;
		}

		if (b.lines == 0) throw new IOException("Not an M3U playlist: no stream found");
		return b.build();
	}

	/** True if the start of a download looks like an M3U playlist. */
	public static boolean looksLikePlaylist(String head) {
		String h = head.startsWith("\uFEFF") ? head.substring(1) : head;
		h = h.trim();
		return startsWithIgnoreCase(h, "#EXTM3U") || startsWithIgnoreCase(h, "#EXTINF");
	}

	/** The id of the account of a playlist link: stable, and not the link itself. */
	public static String accountId(String url) {
		return "m3u" + hash(url.trim());
	}

	static String hash(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i]));
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static boolean startsWithIgnoreCase(String s, String prefix) {
		return s.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	private static final class Builder {
		final Map<Kind, Map<String, Category>> cats = new EnumMap<>(Kind.class);
		final List<LiveChannel> live = new ArrayList<>();
		final List<Movie> movies = new ArrayList<>();
		final Map<String, Show> shows = new LinkedHashMap<>();
		final Map<String, Boolean> seen = new HashMap<>();
		int lines;

		void add(String url, String info, String extGroup) {
			lines++;
			if (seen.put(url, Boolean.TRUE) != null) return; // The same stream twice

			Map<String, String> attrs = new HashMap<>();
			String title = null;
			if (info != null) {
				int comma = titleComma(info);
				if (comma >= 0) title = info.substring(comma + 1).trim();
				Matcher m = ATTR.matcher((comma >= 0) ? info.substring(0, comma) : info);
				while (m.find()) attrs.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
			}

			String name = nonEmpty(title, attrs.get("tvg-name"));
			if (name == null) name = fileName(url);
			String group = nonEmpty(attrs.get("group-title"), extGroup);
			String logo = nonEmpty(attrs.get("tvg-logo"), attrs.get("logo"));
			String id = hash(url);
			String type = attrs.get("tvg-type");
			if (type == null) type = attrs.get("type");
			String path = path(url);
			String ext = extension(path);
			Matcher ep = EPISODE.matcher(name);
			boolean episodeName = ep.matches();
			boolean series = "series".equalsIgnoreCase(type) || path.contains("/series/") ||
					(episodeName && isVideo(ext) && !"movie".equalsIgnoreCase(type));
			boolean movie = !series && ("movie".equalsIgnoreCase(type) || "vod".equalsIgnoreCase(type) ||
					path.contains("/movie/") || isVideo(ext));

			if (series) {
				String show = episodeName ? clean(ep.group(1)) : null;
				if ((show == null) || show.isEmpty()) show = name;
				int season = episodeName ? Integer.parseInt(ep.group(2)) : 1;
				String showKey = show.toLowerCase(Locale.ROOT) + '\n' + ((group == null) ? "" : group);
				Show s = shows.get(showKey);
				if (s == null) {
					s = new Show(hash("series\n" + showKey), show, logo, category(Kind.SERIES, group).id);
					shows.put(showKey, s);
				}
				if ((s.logo == null) && (logo != null)) s.logo = logo;
				TreeMap<Integer, Episode> eps = s.seasons.computeIfAbsent(season, x -> new TreeMap<>());
				int number = episodeName ? Integer.parseInt(ep.group(3)) : eps.size() + 1;
				while (eps.containsKey(number)) number++;
				String epTitle = episodeName ? clean(ep.group(4)) : null;
				if ((epTitle == null) || epTitle.isEmpty()) epTitle = name;
				eps.put(number, new Episode(id, season, number, epTitle, ext, null, 0, logo, url));
			} else if (movie) {
				movies.add(new Movie(id, name, logo, category(Kind.MOVIE, group).id, 0, 0f, ext, url));
			} else {
				int number = 0;
				String chno = attrs.get("tvg-chno");
				if (chno != null) {
					try {
						number = Integer.parseInt(chno.trim());
					} catch (NumberFormatException ignore) {
					}
				}
				live.add(new LiveChannel(id, name, logo, category(Kind.LIVE, group).id, 0,
						nonEmpty(attrs.get("tvg-id"), null), number, 0, url));
			}
		}

		Category category(Kind k, String group) {
			String name = (group == null) ? NO_GROUP : group;
			Map<String, Category> m = cats.computeIfAbsent(k, x -> new LinkedHashMap<>());
			Category c = m.get(name);
			if (c == null) {
				c = new Category(k, String.valueOf(m.size() + 1), name);
				m.put(name, c);
			}
			return c;
		}

		M3uPlaylist build() {
			Map<Kind, List<Category>> categories = new EnumMap<>(Kind.class);
			for (Kind k : new Kind[]{Kind.LIVE, Kind.MOVIE, Kind.SERIES}) {
				Map<String, Category> m = cats.get(k);
				categories.put(k, (m == null) ? new ArrayList<>() : new ArrayList<>(m.values()));
			}

			List<Series> seriesList = new ArrayList<>(shows.size());
			Map<String, Details> details = new HashMap<>(shows.size() * 4 / 3 + 1);
			for (Show s : shows.values()) {
				seriesList.add(new Series(s.id, s.name, s.logo, s.categoryId, 0, 0f, null, null, null,
						null));
				List<Season> seasons = new ArrayList<>(s.seasons.size());
				for (Map.Entry<Integer, TreeMap<Integer, Episode>> e : s.seasons.entrySet()) {
					seasons.add(new Season(e.getKey(), null, null, new ArrayList<>(e.getValue().values())));
				}
				details.put(s.id, new Details(null, null, null, null, null, 0, 0f, s.logo, null, null,
						seasons));
			}

			Map<Kind, List<? extends Entry>> entries = new EnumMap<>(Kind.class);
			entries.put(Kind.LIVE, live);
			entries.put(Kind.MOVIE, movies);
			entries.put(Kind.SERIES, seriesList);
			return new M3uPlaylist(categories, entries, details);
		}
	}

	private static final class Show {
		final String id;
		final String name;
		final String categoryId;
		final TreeMap<Integer, TreeMap<Integer, Episode>> seasons = new TreeMap<>();
		String logo;

		Show(String id, String name, String logo, String categoryId) {
			this.id = id;
			this.name = name;
			this.logo = logo;
			this.categoryId = categoryId;
		}
	}

	/** The comma before the title: the first one outside the quoted attribute values. */
	private static int titleComma(String info) {
		boolean quoted = false;
		for (int i = 0, n = info.length(); i < n; i++) {
			char c = info.charAt(i);
			if (c == '"') quoted = !quoted;
			else if ((c == ',') && !quoted) return i;
		}
		return -1;
	}

	private static String nonEmpty(String a, String b) {
		if ((a != null) && !a.isEmpty()) return a;
		return ((b != null) && !b.isEmpty()) ? b : null;
	}

	private static String clean(String s) {
		if (s == null) return null;
		return s.replaceAll("[\\s._\\-:|(\\[]+$", "").replaceAll("^[\\s._\\-:|)\\]]+", "").trim();
	}

	private static String path(String url) {
		int q = url.indexOf('?');
		String p = (q >= 0) ? url.substring(0, q) : url;
		int h = p.indexOf('#');
		return ((h >= 0) ? p.substring(0, h) : p).toLowerCase(Locale.ROOT);
	}

	private static String extension(String path) {
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		return (dot > slash) ? path.substring(dot + 1) : null;
	}

	private static boolean isVideo(String ext) {
		if (ext == null) return false;
		for (String e : VIDEO_EXT) if (e.equals(ext)) return true;
		return false;
	}

	private static String fileName(String url) {
		String p = url;
		int q = p.indexOf('?');
		if (q >= 0) p = p.substring(0, q);
		int slash = p.lastIndexOf('/');
		String n = (slash >= 0) ? p.substring(slash + 1) : p;
		return n.isEmpty() ? url : n;
	}
}
