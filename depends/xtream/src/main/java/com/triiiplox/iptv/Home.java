package com.triiiplox.iptv;

import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.Series;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The rows of the screens, Netflix style: what the user is watching first, then what is new and
 * best rated, then a row per category. The core knows no language: a row has a type, and the
 * name of its category if it is one; the UI words it.
 */
public final class Home {
	public static final int ROW_LIMIT = 40;

	private Home() {
	}

	/** The start page: a mix of every kind. */
	public static Page start(Snapshot s, UserLibrary lib, String account) {
		List<Row> rows = new ArrayList<>();
		addContinue(rows, lib, account);
		addRecords(rows, Row.Type.MY_LIST, lib.favorites(account, null));
		addRecords(rows, Row.Type.RECENT_CHANNELS, lib.recentChannels(account, ROW_LIMIT));
		add(rows, Row.Type.NEW_MOVIES, null, s.newest(Kind.MOVIE, ROW_LIMIT));
		add(rows, Row.Type.NEW_SERIES, null, s.newest(Kind.SERIES, ROW_LIMIT));
		add(rows, Row.Type.TOP_MOVIES, null, s.topRated(Kind.MOVIE, ROW_LIMIT));
		add(rows, Row.Type.TOP_SERIES, null, s.topRated(Kind.SERIES, ROW_LIMIT));
		return new Page(hero(s), rows);
	}

	/** The page of a kind: its continue/my list rows, the new ones, then every category. */
	public static Page of(Kind kind, Snapshot s, UserLibrary lib, String account) {
		List<Row> rows = new ArrayList<>();
		if (kind == Kind.LIVE) {
			// The favorite channels first: the ones to find at once in the car
			addRecords(rows, Row.Type.MY_LIST, lib.favorites(account, Kind.LIVE));
			addRecords(rows, Row.Type.RECENT_CHANNELS, lib.recentChannels(account, ROW_LIMIT));
		} else {
			List<UserLibrary.Record> cont = new ArrayList<>();
			for (UserLibrary.Record r : lib.continueWatching(account, ROW_LIMIT)) {
				if ((kind == Kind.MOVIE) == (r.kind == Kind.MOVIE)) cont.add(r);
			}
			addRecords(rows, Row.Type.CONTINUE, cont);
			addRecords(rows, Row.Type.MY_LIST, lib.favorites(account, kind));
			add(rows, (kind == Kind.MOVIE) ? Row.Type.NEW_MOVIES : Row.Type.NEW_SERIES, null,
					s.newest(kind, ROW_LIMIT));
		}
		for (Map.Entry<Category, List<Entry>> e : s.rows(kind).entrySet()) {
			add(rows, Row.Type.CATEGORY, e.getKey(), e.getValue());
		}
		return new Page((kind == Kind.LIVE) ? null : hero(s, kind), rows);
	}

	private static void addContinue(List<Row> rows, UserLibrary lib, String account) {
		addRecords(rows, Row.Type.CONTINUE, lib.continueWatching(account, ROW_LIMIT));
	}

	private static void addRecords(List<Row> rows, Row.Type type, List<UserLibrary.Record> l) {
		if (!l.isEmpty()) rows.add(new Row(type, null, Collections.emptyList(), l));
	}

	private static void add(List<Row> rows, Row.Type type, Category c, List<Entry> l) {
		if (!l.isEmpty()) rows.add(new Row(type, c, l, Collections.emptyList()));
	}

	// A series with a wide picture makes the best hero; else the newest movie with a poster
	private static Entry hero(Snapshot s) {
		Entry e = hero(s, Kind.SERIES);
		return (e != null) ? e : hero(s, Kind.MOVIE);
	}

	private static Entry hero(Snapshot s, Kind kind) {
		List<Entry> l = s.newest(kind, 60);
		// A playlist has no dates: its first titles, in the order of the list
		if (l.isEmpty()) {
			List<Entry> all = s.entries(kind);
			l = all.subList(0, Math.min(60, all.size()));
		}
		if (kind == Kind.SERIES) {
			for (Entry e : l) if ((e instanceof Series se) && (se.backdrop != null)) return e;
		}
		for (Entry e : l) if (e.image != null) return e;
		return null;
	}

	public static final class Page {
		/** The highlighted title at the top, or null. */
		public final Entry hero;
		public final List<Row> rows;

		Page(Entry hero, List<Row> rows) {
			this.hero = hero;
			this.rows = Collections.unmodifiableList(rows);
		}
	}

	/** A row of either catalog entries or library records. */
	public static final class Row {
		public final Type type;
		public final Category category;
		public final List<Entry> entries;
		public final List<UserLibrary.Record> records;

		Row(Type type, Category category, List<Entry> entries, List<UserLibrary.Record> records) {
			this.type = type;
			this.category = category;
			this.entries = Collections.unmodifiableList(entries);
			this.records = Collections.unmodifiableList(records);
		}

		public int size() {
			return entries.isEmpty() ? records.size() : entries.size();
		}

		public enum Type {
			CONTINUE, MY_LIST, RECENT_CHANNELS, NEW_MOVIES, NEW_SERIES, TOP_MOVIES, TOP_SERIES, CATEGORY
		}
	}
}
