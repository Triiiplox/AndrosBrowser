package com.triiiplox.iptv;

import com.triiiplox.iptv.json.JsonReader;
import com.triiiplox.iptv.json.JsonToken;
import com.triiiplox.iptv.model.AccountInfo;
import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.LiveChannel;
import com.triiiplox.iptv.model.Movie;
import com.triiiplox.iptv.model.Program;
import com.triiiplox.iptv.model.Series;
import com.triiiplox.iptv.net.Transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Xtream Codes player API (player_api.php). Every call is blocking: run it off the main
 * thread. The parsers accept the loose JSON of real panels: ids as numbers or strings, lists as
 * arrays or objects, missing or empty values.
 */
public final class XtreamClient {
	private final XtreamAccount account;
	private final Transport transport;

	public XtreamClient(XtreamAccount account, Transport transport) {
		this.account = account;
		this.transport = transport;
	}

	public XtreamAccount getAccount() {
		return account;
	}

	public AccountInfo accountInfo() throws IOException {
		try (JsonReader r = open(account.apiUrl(null))) {
			String status = null, tz = null;
			long exp = 0;
			boolean trial = false;
			int active = 0, max = 0;
			if (r.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("Not an Xtream panel");
			r.beginObject();
			while (r.hasNext()) {
				String n = r.nextName();
				if (n.equals("user_info") && (r.peek() == JsonToken.BEGIN_OBJECT)) {
					r.beginObject();
					while (r.hasNext()) {
						switch (r.nextName()) {
							case "status" -> status = r.nextStringOrNull();
							case "exp_date" -> exp = toLong(r.nextStringOrNull());
							case "is_trial" -> trial = "1".equals(r.nextStringOrNull());
							case "active_cons" -> active = (int) toLong(r.nextStringOrNull());
							case "max_connections" -> max = (int) toLong(r.nextStringOrNull());
							default -> r.skipValue();
						}
					}
					r.endObject();
				} else if (n.equals("server_info") && (r.peek() == JsonToken.BEGIN_OBJECT)) {
					r.beginObject();
					while (r.hasNext()) {
						if (r.nextName().equals("timezone")) tz = r.nextStringOrNull();
						else r.skipValue();
					}
					r.endObject();
				} else {
					r.skipValue();
				}
			}
			r.endObject();
			if (status == null) throw new IOException("Login refused by the panel");
			return new AccountInfo(status, exp, trial, active, max, tz);
		}
	}

	public List<Category> categories(Kind kind) throws IOException {
		String action = switch (kind) {
			case LIVE -> "get_live_categories";
			case MOVIE -> "get_vod_categories";
			case SERIES -> "get_series_categories";
			default -> throw new IllegalArgumentException(kind.code);
		};
		List<Category> list = new ArrayList<>();
		try (JsonReader r = open(account.apiUrl(action))) {
			if (!beginList(r)) return list;
			while (r.hasNext()) {
				if (r.peek() != JsonToken.BEGIN_OBJECT) {
					r.skipValue();
					continue;
				}
				String id = null, name = null;
				r.beginObject();
				while (r.hasNext()) {
					switch (r.nextName()) {
						case "category_id" -> id = r.nextStringOrNull();
						case "category_name" -> name = r.nextStringOrNull();
						default -> r.skipValue();
					}
				}
				r.endObject();
				if ((id != null) && (name != null)) list.add(new Category(kind, id, name.trim()));
			}
			endList(r);
		}
		return list;
	}

	public List<LiveChannel> liveChannels() throws IOException {
		List<LiveChannel> list = new ArrayList<>();
		try (JsonReader r = open(account.apiUrl("get_live_streams"))) {
			if (!beginList(r)) return list;
			while (r.hasNext()) {
				if (r.peek() != JsonToken.BEGIN_OBJECT) {
					r.skipValue();
					continue;
				}
				String id = null, name = null, icon = null, cat = null, epg = null;
				long added = 0;
				int num = 0, archive = 0, days = 0;
				r.beginObject();
				while (r.hasNext()) {
					switch (r.nextName()) {
						case "stream_id" -> id = r.nextStringOrNull();
						case "name" -> name = r.nextStringOrNull();
						case "stream_icon" -> icon = url(r.nextStringOrNull());
						case "category_id" -> cat = r.nextStringOrNull();
						case "epg_channel_id" -> epg = r.nextStringOrNull();
						case "added" -> added = toLong(r.nextStringOrNull());
						case "num" -> num = (int) toLong(r.nextStringOrNull());
						case "tv_archive" -> archive = (int) toLong(r.nextStringOrNull());
						case "tv_archive_duration" -> days = (int) toLong(r.nextStringOrNull());
						default -> r.skipValue();
					}
				}
				r.endObject();
				if ((id == null) || (name == null)) continue;
				list.add(new LiveChannel(id, name.trim(), icon, cat, added, epg, num,
						(archive != 0) ? Math.max(days, 1) : 0));
			}
			endList(r);
		}
		return list;
	}

	public List<Movie> movies() throws IOException {
		List<Movie> list = new ArrayList<>();
		try (JsonReader r = open(account.apiUrl("get_vod_streams"))) {
			if (!beginList(r)) return list;
			while (r.hasNext()) {
				if (r.peek() != JsonToken.BEGIN_OBJECT) {
					r.skipValue();
					continue;
				}
				String id = null, name = null, icon = null, cat = null, ext = null;
				long added = 0;
				float rating = 0, rating5 = 0;
				r.beginObject();
				while (r.hasNext()) {
					switch (r.nextName()) {
						case "stream_id" -> id = r.nextStringOrNull();
						case "name" -> name = r.nextStringOrNull();
						case "stream_icon" -> icon = url(r.nextStringOrNull());
						case "category_id" -> cat = r.nextStringOrNull();
						case "added" -> added = toLong(r.nextStringOrNull());
						case "rating" -> rating = toFloat(r.nextStringOrNull());
						case "rating_5based" -> rating5 = toFloat(r.nextStringOrNull());
						case "container_extension" -> ext = r.nextStringOrNull();
						default -> r.skipValue();
					}
				}
				r.endObject();
				if ((id == null) || (name == null)) continue;
				list.add(new Movie(id, name.trim(), icon, cat, added, rating(rating, rating5), ext));
			}
			endList(r);
		}
		return list;
	}

	public List<Series> series() throws IOException {
		List<Series> list = new ArrayList<>();
		try (JsonReader r = open(account.apiUrl("get_series"))) {
			if (!beginList(r)) return list;
			while (r.hasNext()) {
				if (r.peek() != JsonToken.BEGIN_OBJECT) {
					r.skipValue();
					continue;
				}
				String id = null, name = null, cover = null, cat = null, plot = null, genre = null;
				String date = null, backdrop = null;
				long modified = 0;
				float rating = 0, rating5 = 0;
				r.beginObject();
				while (r.hasNext()) {
					switch (r.nextName()) {
						case "series_id" -> id = r.nextStringOrNull();
						case "name" -> name = r.nextStringOrNull();
						case "cover" -> cover = url(r.nextStringOrNull());
						case "category_id" -> cat = r.nextStringOrNull();
						case "plot" -> plot = r.nextStringOrNull();
						case "genre" -> genre = r.nextStringOrNull();
						case "releaseDate", "release_date" -> {
							String d = r.nextStringOrNull();
							if (date == null) date = d;
						}
						case "last_modified" -> modified = toLong(r.nextStringOrNull());
						case "rating" -> rating = toFloat(r.nextStringOrNull());
						case "rating_5based" -> rating5 = toFloat(r.nextStringOrNull());
						case "backdrop_path" -> backdrop = firstUrl(r);
						default -> r.skipValue();
					}
				}
				r.endObject();
				if ((id == null) || (name == null)) continue;
				list.add(new Series(id, name.trim(), cover, cat, modified, rating(rating, rating5),
						plot, genre, year(date), backdrop));
			}
			endList(r);
		}
		return list;
	}

	public Details movieDetails(String movieId) throws IOException {
		try (JsonReader r = open(account.apiUrl("get_vod_info", "vod_id", movieId))) {
			DetailsBuilder b = new DetailsBuilder();
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				return b.build(null);
			}
			r.beginObject();
			while (r.hasNext()) {
				if (r.nextName().equals("info") && (r.peek() == JsonToken.BEGIN_OBJECT)) readInfo(r, b);
				else r.skipValue();
			}
			r.endObject();
			return b.build(null);
		}
	}

	public Details seriesDetails(String seriesId) throws IOException {
		try (JsonReader r = open(account.apiUrl("get_series_info", "series_id", seriesId))) {
			DetailsBuilder b = new DetailsBuilder();
			Map<Integer, String[]> seasonInfo = new TreeMap<>();
			Map<Integer, List<Details.Episode>> episodes = new TreeMap<>();
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				return b.build(null);
			}
			r.beginObject();
			while (r.hasNext()) {
				switch (r.nextName()) {
					case "info" -> {
						if (r.peek() == JsonToken.BEGIN_OBJECT) readInfo(r, b);
						else r.skipValue();
					}
					case "seasons" -> readSeasons(r, seasonInfo);
					case "episodes" -> readEpisodes(r, episodes);
					default -> r.skipValue();
				}
			}
			r.endObject();

			List<Details.Season> seasons = new ArrayList<>(episodes.size());
			for (Map.Entry<Integer, List<Details.Episode>> e : episodes.entrySet()) {
				List<Details.Episode> eps = e.getValue();
				eps.sort((a, c) -> Integer.compare(a.number, c.number));
				String[] si = seasonInfo.get(e.getKey());
				seasons.add(new Details.Season(e.getKey(), (si != null) ? si[0] : null,
						(si != null) ? si[1] : null, Collections.unmodifiableList(eps)));
			}
			return b.build(seasons);
		}
	}

	/** Now and next of a channel: get_short_epg, titles and descriptions are base64. */
	public List<Program> shortEpg(String streamId, int limit) throws IOException {
		List<Program> list = new ArrayList<>();
		try (JsonReader r = open(account.apiUrl("get_short_epg", "stream_id", streamId, "limit",
				String.valueOf(limit)))) {
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				return list;
			}
			r.beginObject();
			while (r.hasNext()) {
				if (!r.nextName().equals("epg_listings") || !beginList(r)) {
					r.skipValue();
					continue;
				}
				while (r.hasNext()) {
					if (r.peek() != JsonToken.BEGIN_OBJECT) {
						r.skipValue();
						continue;
					}
					String title = null, desc = null;
					long start = 0, end = 0;
					boolean archive = false;
					r.beginObject();
					while (r.hasNext()) {
						switch (r.nextName()) {
							case "title" -> title = base64(r.nextStringOrNull());
							case "description" -> desc = base64(r.nextStringOrNull());
							case "start_timestamp" -> start = toLong(r.nextStringOrNull());
							case "stop_timestamp" -> end = toLong(r.nextStringOrNull());
							case "has_archive" -> archive = "1".equals(r.nextStringOrNull());
							default -> r.skipValue();
						}
					}
					r.endObject();
					if ((title != null) && (end > start)) list.add(new Program(title, desc, start, end, archive));
				}
				endList(r);
			}
			r.endObject();
		}
		list.sort((a, b) -> Long.compare(a.start, b.start));
		return list;
	}

	private void readInfo(JsonReader r, DetailsBuilder b) throws IOException {
		r.beginObject();
		while (r.hasNext()) {
			switch (r.nextName()) {
				case "plot", "description" -> b.plot = first(b.plot, r.nextStringOrNull());
				case "cast", "actors" -> b.cast = first(b.cast, r.nextStringOrNull());
				case "director" -> b.director = first(b.director, r.nextStringOrNull());
				case "genre" -> b.genre = first(b.genre, r.nextStringOrNull());
				case "releasedate", "releaseDate", "release_date" ->
						b.releaseDate = first(b.releaseDate, r.nextStringOrNull());
				case "duration_secs" -> b.duration = Math.max(b.duration, (int) toLong(r.nextStringOrNull()));
				case "duration" -> {
					int d = parseDuration(r.nextStringOrNull());
					if (b.duration == 0) b.duration = d;
				}
				case "episode_run_time" -> {
					long m = toLong(r.nextStringOrNull());
					if (b.duration == 0) b.duration = (int) (m * 60);
				}
				case "rating" -> b.rating = Math.max(b.rating, toFloat(r.nextStringOrNull()));
				case "rating_5based" -> {
					float f = toFloat(r.nextStringOrNull()) * 2;
					if (b.rating == 0) b.rating = f;
				}
				case "movie_image", "cover_big", "cover" -> b.poster = first(b.poster, url(r.nextStringOrNull()));
				case "backdrop_path" -> readUrls(r, b.backdrops);
				case "youtube_trailer" -> b.trailer = first(b.trailer, r.nextStringOrNull());
				default -> r.skipValue();
			}
		}
		r.endObject();
	}

	// [{season_number, name, cover, cover_big}]
	private void readSeasons(JsonReader r, Map<Integer, String[]> out) throws IOException {
		if (!beginList(r)) {
			r.skipValue();
			return;
		}
		while (r.hasNext()) {
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				continue;
			}
			int num = -1;
			String name = null, cover = null;
			r.beginObject();
			while (r.hasNext()) {
				switch (r.nextName()) {
					case "season_number" -> num = (int) toLong(r.nextStringOrNull());
					case "name" -> name = r.nextStringOrNull();
					case "cover_big" -> {
						String c = url(r.nextStringOrNull());
						if (c != null) cover = c;
					}
					case "cover" -> cover = first(cover, url(r.nextStringOrNull()));
					default -> r.skipValue();
				}
			}
			r.endObject();
			if (num >= 0) out.put(num, new String[]{name, cover});
		}
		endList(r);
	}

	// {"1": [episode...], "2": [...]} or [[episode...], ...]
	private void readEpisodes(JsonReader r, Map<Integer, List<Details.Episode>> out)
			throws IOException {
		JsonToken t = r.peek();
		if (t == JsonToken.BEGIN_OBJECT) {
			r.beginObject();
			while (r.hasNext()) {
				int season = (int) toLong(r.nextName());
				readEpisodeList(r, season, out);
			}
			r.endObject();
		} else if (t == JsonToken.BEGIN_ARRAY) {
			r.beginArray();
			int idx = 1;
			while (r.hasNext()) readEpisodeList(r, idx++, out);
			r.endArray();
		} else {
			r.skipValue();
		}
	}

	private void readEpisodeList(JsonReader r, int season, Map<Integer, List<Details.Episode>> out)
			throws IOException {
		if (!beginList(r)) {
			r.skipValue();
			return;
		}
		while (r.hasNext()) {
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				continue;
			}
			String id = null, title = null, ext = null, plot = null, image = null;
			int num = 0, duration = 0, s = season;
			r.beginObject();
			while (r.hasNext()) {
				switch (r.nextName()) {
					case "id" -> id = r.nextStringOrNull();
					case "episode_num" -> num = (int) toLong(r.nextStringOrNull());
					case "title" -> title = r.nextStringOrNull();
					case "container_extension" -> ext = r.nextStringOrNull();
					case "season" -> {
						long v = toLong(r.nextStringOrNull());
						if (v > 0) s = (int) v;
					}
					case "info" -> {
						if (r.peek() != JsonToken.BEGIN_OBJECT) {
							r.skipValue();
							break;
						}
						r.beginObject();
						while (r.hasNext()) {
							switch (r.nextName()) {
								case "plot" -> plot = r.nextStringOrNull();
								case "movie_image" -> image = url(r.nextStringOrNull());
								case "duration_secs" -> duration = (int) toLong(r.nextStringOrNull());
								case "duration" -> {
									int d = parseDuration(r.nextStringOrNull());
									if (duration == 0) duration = d;
								}
								default -> r.skipValue();
							}
						}
						r.endObject();
					}
					default -> r.skipValue();
				}
			}
			r.endObject();
			if (id == null) continue;
			out.computeIfAbsent(s, k -> new ArrayList<>())
					.add(new Details.Episode(id, s, num, title, ext, plot, duration, image));
		}
		endList(r);
	}

	private JsonReader open(String url) throws IOException {
		InputStream in = transport.open(url);
		return new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	// A list may come as an array or as an object of id -> value; anything else is empty
	private static boolean beginList(JsonReader r) throws IOException {
		JsonToken t = r.peek();
		if (t == JsonToken.BEGIN_ARRAY) {
			r.beginArray();
			return true;
		}
		if (t == JsonToken.BEGIN_OBJECT) {
			// The names are skipped as values by the callers: they check for an object first
			r.beginObject();
			return true;
		}
		return false;
	}

	private static void endList(JsonReader r) throws IOException {
		if (r.peek() == JsonToken.END_OBJECT) r.endObject();
		else r.endArray();
	}

	private static String firstUrl(JsonReader r) throws IOException {
		List<String> l = new ArrayList<>(1);
		readUrls(r, l);
		return l.isEmpty() ? null : l.get(0);
	}

	private static void readUrls(JsonReader r, List<String> out) throws IOException {
		if (r.peek() == JsonToken.BEGIN_ARRAY) {
			r.beginArray();
			while (r.hasNext()) {
				String u = url(r.nextStringOrNull());
				if (u != null) out.add(u);
			}
			r.endArray();
		} else {
			String u = url(r.nextStringOrNull());
			if (u != null) out.add(u);
		}
	}

	private static String url(String s) {
		if (s == null) return null;
		s = s.trim();
		return (s.startsWith("http://") || s.startsWith("https://")) ? s : null;
	}

	private static String first(String a, String b) {
		return (a != null) ? a : b;
	}

	private static float rating(float r10, float r5) {
		if (r10 > 0) return Math.min(r10, 10f);
		return Math.min(r5 * 2, 10f);
	}

	private static String year(String date) {
		if ((date == null) || (date.length() < 4)) return null;
		for (int i = 0; i < 4; i++) if (!Character.isDigit(date.charAt(i))) return null;
		return date.substring(0, 4);
	}

	static long toLong(String s) {
		if (s == null) return 0;
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException ex) {
			try {
				return (long) Double.parseDouble(s.trim());
			} catch (NumberFormatException ex2) {
				return 0;
			}
		}
	}

	static float toFloat(String s) {
		if (s == null) return 0;
		try {
			float f = Float.parseFloat(s.trim().replace(',', '.'));
			return (Float.isNaN(f) || (f < 0)) ? 0 : f;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	// "01:52:10", "52:10" or minutes
	static int parseDuration(String s) {
		if (s == null) return 0;
		String[] p = s.trim().split(":");
		try {
			if (p.length == 3) {
				return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
			}
			if (p.length == 2) return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
			return (int) toLong(s) * 60;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static String base64(String s) {
		if (s == null) return null;
		try {
			String d = new String(Base64.getDecoder().decode(s.trim()), StandardCharsets.UTF_8).trim();
			return d.isEmpty() ? null : d;
		} catch (IllegalArgumentException ex) {
			return s; // already plain text
		}
	}

	private static final class DetailsBuilder {
		String plot, cast, director, genre, releaseDate, poster, trailer;
		int duration;
		float rating;
		final List<String> backdrops = new ArrayList<>(2);

		Details build(List<Details.Season> seasons) {
			return new Details(plot, cast, director, genre, releaseDate, duration, Math.min(rating, 10f),
					poster, backdrops, trailer, seasons);
		}
	}
}
