package com.triiiplox.iptv;

import com.triiiplox.iptv.json.JsonReader;
import com.triiiplox.iptv.json.JsonToken;
import com.triiiplox.iptv.json.JsonWriter;
import com.triiiplox.iptv.model.Entry.Kind;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the user watched and kept: the progress of movies and episodes for "continue watching",
 * the last channels, and "my list" (favorites). A record carries its title and picture, so the
 * rows show even before the catalog is loaded. Saved to one JSON file, written to a temporary
 * file and renamed, so a crash never leaves it half written. Thread safe.
 */
public final class UserLibrary {
	private static final int MAX_HISTORY = 300;
	// A video counts as finished from here, and is not offered to continue
	private static final float WATCHED = 0.93f;
	// Below this the start is a peek, not worth continuing
	private static final long MIN_PROGRESS = 60_000;
	private final File file;
	// Most recent first
	private final LinkedHashMap<String, Record> history = new LinkedHashMap<>();
	private final LinkedHashMap<String, Record> favorites = new LinkedHashMap<>();
	private boolean loaded;

	public UserLibrary(File file) {
		this.file = file;
	}

	/** Records a playback: a new position, or a channel opened. Moves it to the top. */
	public synchronized void played(Record r) {
		load();
		history.remove(r.key());
		LinkedHashMap<String, Record> m = new LinkedHashMap<>();
		m.put(r.key(), r);
		m.putAll(history);
		history.clear();
		int n = 0;
		for (Map.Entry<String, Record> e : m.entrySet()) {
			if (n++ == MAX_HISTORY) break;
			history.put(e.getKey(), e.getValue());
		}
		save();
	}

	public synchronized Record get(String account, Kind kind, String id) {
		load();
		return history.get(Record.key(account, kind, id));
	}

	public synchronized void forget(String account, Kind kind, String id) {
		load();
		if (history.remove(Record.key(account, kind, id)) != null) save();
	}

	/**
	 * Movies and episodes started and not finished, most recent first, one per series (its last
	 * episode).
	 */
	public synchronized List<Record> continueWatching(String account, int limit) {
		load();
		List<Record> l = new ArrayList<>();
		Set<String> series = new HashSet<>();
		for (Record r : history.values()) {
			if (!r.account.equals(account)) continue;
			if ((r.kind != Kind.MOVIE) && (r.kind != Kind.EPISODE)) continue;
			if (r.seriesId != null) {
				if (!series.add(r.seriesId)) continue;
			}
			if (!r.isInProgress()) continue;
			l.add(r);
			if (l.size() == limit) break;
		}
		return l;
	}

	/** The last episode played of a series, finished or not, or null. */
	public synchronized Record lastEpisode(String account, String seriesId) {
		load();
		for (Record r : history.values()) {
			if (r.account.equals(account) && (r.kind == Kind.EPISODE) && seriesId.equals(r.seriesId)) {
				return r;
			}
		}
		return null;
	}

	public synchronized List<Record> recentChannels(String account, int limit) {
		load();
		List<Record> l = new ArrayList<>();
		for (Record r : history.values()) {
			if (r.account.equals(account) && (r.kind == Kind.LIVE)) {
				l.add(r);
				if (l.size() == limit) break;
			}
		}
		return l;
	}

	public synchronized boolean isFavorite(String account, Kind kind, String id) {
		load();
		return favorites.containsKey(Record.key(account, kind, id));
	}

	/** @return true if it is a favorite now */
	public synchronized boolean toggleFavorite(Record r) {
		load();
		boolean fav;
		if (favorites.remove(r.key()) != null) {
			fav = false;
		} else {
			LinkedHashMap<String, Record> m = new LinkedHashMap<>();
			m.put(r.key(), r);
			m.putAll(favorites);
			favorites.clear();
			favorites.putAll(m);
			fav = true;
		}
		save();
		return fav;
	}

	public synchronized List<Record> favorites(String account, Kind kind) {
		load();
		List<Record> l = new ArrayList<>();
		for (Record r : favorites.values()) {
			if (r.account.equals(account) && ((kind == null) || (r.kind == kind))) l.add(r);
		}
		return l;
	}

	private void load() {
		if (loaded) return;
		loaded = true;
		if (!file.isFile()) return;
		try (JsonReader r = new JsonReader(new InputStreamReader(new FileInputStream(file),
				StandardCharsets.UTF_8))) {
			r.beginObject();
			while (r.hasNext()) {
				String n = r.nextName();
				LinkedHashMap<String, Record> m = n.equals("history") ? history :
						n.equals("favorites") ? favorites : null;
				if ((m == null) || (r.peek() != JsonToken.BEGIN_ARRAY)) {
					r.skipValue();
					continue;
				}
				r.beginArray();
				while (r.hasNext()) {
					Record rec = Record.read(r);
					if (rec != null) m.put(rec.key(), rec);
				}
				r.endArray();
			}
			r.endObject();
		} catch (IOException | RuntimeException ex) {
			// A broken file starts a new library rather than breaking the app
			history.clear();
			favorites.clear();
		}
	}

	private void save() {
		File dir = file.getParentFile();
		if ((dir != null) && !dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
		File tmp = new File(file.getPath() + ".tmp");
		try (JsonWriter w = new JsonWriter(new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(tmp), StandardCharsets.UTF_8)))) {
			w.beginObject();
			w.name("history").beginArray();
			for (Record r : history.values()) r.write(w);
			w.endArray();
			w.name("favorites").beginArray();
			for (Record r : favorites.values()) r.write(w);
			w.endArray();
			w.endObject();
		} catch (IOException ex) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			return;
		}
		if (!tmp.renameTo(file)) {
			//noinspection ResultOfMethodCallIgnored
			file.delete();
			//noinspection ResultOfMethodCallIgnored
			tmp.renameTo(file);
		}
	}

	/** A movie, an episode, a series or a channel of an account, as the user last saw it. */
	public static final class Record {
		public final String account;
		public final Kind kind;
		public final String id;
		public final String title;
		/** E.g. the series and episode of an episode, or null. */
		public final String subtitle;
		public final String image;
		public final String extension;
		/** The series of an episode, or null. */
		public final String seriesId;
		public final int season;
		public final int episode;
		/** Milliseconds; 0 for a channel. */
		public final long position;
		public final long duration;
		public final long updated;

		public Record(String account, Kind kind, String id, String title, String subtitle, String image,
									String extension, String seriesId, int season, int episode, long position,
									long duration, long updated) {
			this.account = account;
			this.kind = kind;
			this.id = id;
			this.title = title;
			this.subtitle = subtitle;
			this.image = image;
			this.extension = extension;
			this.seriesId = seriesId;
			this.season = season;
			this.episode = episode;
			this.position = position;
			this.duration = duration;
			this.updated = updated;
		}

		public String key() {
			return key(account, kind, id);
		}

		static String key(String account, Kind kind, String id) {
			return account + '|' + kind.code + ':' + id;
		}

		/** 0..1, or 0 if the duration is unknown. */
		public float progress() {
			return (duration > 0) ? Math.min(1f, position / (float) duration) : 0f;
		}

		public boolean isInProgress() {
			if (position < MIN_PROGRESS) return false;
			return (duration <= 0) || (progress() < WATCHED);
		}

		public boolean isFinished() {
			return (duration > 0) && (progress() >= WATCHED);
		}

		public Record withPosition(long position, long duration, long updated) {
			return new Record(account, kind, id, title, subtitle, image, extension, seriesId, season,
					episode, position, duration, updated);
		}

		void write(JsonWriter w) throws IOException {
			w.beginObject().field("a", account).field("k", kind.code).field("i", id).field("t", title)
					.field("s", subtitle).field("img", image).field("x", extension).field("sid", seriesId)
					.field("se", season).field("ep", episode).field("p", position).field("d", duration)
					.field("u", updated).endObject();
		}

		static Record read(JsonReader r) throws IOException {
			if (r.peek() != JsonToken.BEGIN_OBJECT) {
				r.skipValue();
				return null;
			}
			String a = null, k = null, i = null, t = null, s = null, img = null, x = null, sid = null;
			long se = 0, ep = 0, p = 0, d = 0, u = 0;
			r.beginObject();
			while (r.hasNext()) {
				switch (r.nextName()) {
					case "a" -> a = r.nextStringOrNull();
					case "k" -> k = r.nextStringOrNull();
					case "i" -> i = r.nextStringOrNull();
					case "t" -> t = r.nextStringOrNull();
					case "s" -> s = r.nextStringOrNull();
					case "img" -> img = r.nextStringOrNull();
					case "x" -> x = r.nextStringOrNull();
					case "sid" -> sid = r.nextStringOrNull();
					case "se" -> se = XtreamClient.toLong(r.nextStringOrNull());
					case "ep" -> ep = XtreamClient.toLong(r.nextStringOrNull());
					case "p" -> p = XtreamClient.toLong(r.nextStringOrNull());
					case "d" -> d = XtreamClient.toLong(r.nextStringOrNull());
					case "u" -> u = XtreamClient.toLong(r.nextStringOrNull());
					default -> r.skipValue();
				}
			}
			r.endObject();
			Kind kind = Kind.of(k);
			if ((a == null) || (kind == null) || (i == null)) return null;
			return new Record(a, kind, i, (t == null) ? "" : t, s, img, x, sid, (int) se, (int) ep, p, d, u);
		}
	}
}
