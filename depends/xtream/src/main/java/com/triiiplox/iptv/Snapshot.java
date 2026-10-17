package com.triiiplox.iptv;

import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The whole catalog of an account at one moment: categories and entries of every kind, indexed
 * by id and by category, with a search over the names. Immutable, safe to share between threads.
 */
public final class Snapshot {
	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private final Map<Kind, List<Category>> categories = new EnumMap<>(Kind.class);
	private final Map<Kind, List<? extends Entry>> entries = new EnumMap<>(Kind.class);
	private final Map<String, Entry> byKey;
	private final Map<String, List<Entry>> byCategory;
	private final Map<String, Category> categoryByKey;
	// The search keys of all the entries, in the same order as all()
	private final List<Entry> all;
	private final String[] searchKeys;
	public final long loadedAt;

	public Snapshot(Map<Kind, List<Category>> categories, Map<Kind, List<? extends Entry>> entries,
									long loadedAt) {
		this.loadedAt = loadedAt;
		int total = 0;
		for (Kind k : new Kind[]{Kind.LIVE, Kind.MOVIE, Kind.SERIES}) {
			List<Category> c = categories.get(k);
			List<? extends Entry> e = entries.get(k);
			this.categories.put(k, (c == null) ? Collections.emptyList() : Collections.unmodifiableList(c));
			this.entries.put(k, (e == null) ? Collections.emptyList() : Collections.unmodifiableList(e));
			total += (e == null) ? 0 : e.size();
		}

		byKey = new HashMap<>(total * 4 / 3 + 1);
		byCategory = new HashMap<>();
		categoryByKey = new HashMap<>();
		all = new ArrayList<>(total);
		searchKeys = new String[total];
		int i = 0;

		for (Kind k : new Kind[]{Kind.LIVE, Kind.MOVIE, Kind.SERIES}) {
			for (Category c : this.categories.get(k)) categoryByKey.put(catKey(k, c.id), c);
			for (Entry e : this.entries.get(k)) {
				byKey.put(e.key(), e);
				byCategory.computeIfAbsent(catKey(k, e.categoryId), x -> new ArrayList<>()).add(e);
				all.add(e);
				searchKeys[i++] = normalize(e.name);
			}
		}
	}

	public List<Category> categories(Kind kind) {
		return categories.get(kind);
	}

	@SuppressWarnings("unchecked")
	public <E extends Entry> List<E> entries(Kind kind) {
		return (List<E>) entries.get(kind);
	}

	/** The entries of a category, in the panel's order. */
	public List<Entry> inCategory(Kind kind, String categoryId) {
		List<Entry> l = byCategory.get(catKey(kind, categoryId));
		return (l == null) ? Collections.emptyList() : Collections.unmodifiableList(l);
	}

	public Category category(Kind kind, String id) {
		return categoryByKey.get(catKey(kind, id));
	}

	public Entry get(String key) {
		return byKey.get(key);
	}

	public Entry get(Kind kind, String id) {
		return byKey.get(Entry.key(kind, id));
	}

	public int size(Kind kind) {
		return entries.get(kind).size();
	}

	public boolean isEmpty() {
		return all.isEmpty();
	}

	/** The newest entries of a kind, by the date added (series: by the newest episode). */
	public List<Entry> newest(Kind kind, int limit) {
		List<Entry> l = new ArrayList<>(entries.get(kind));
		l.removeIf(e -> e.added <= 0);
		l.sort((a, b) -> Long.compare(b.added, a.added));
		return (l.size() > limit) ? new ArrayList<>(l.subList(0, limit)) : l;
	}

	/** The best rated entries of a kind. */
	public List<Entry> topRated(Kind kind, int limit) {
		List<Entry> l = new ArrayList<>(entries.get(kind));
		l.removeIf(e -> e.rating < 6f);
		l.sort((a, b) -> {
			int c = Float.compare(b.rating, a.rating);
			return (c != 0) ? c : Long.compare(b.added, a.added);
		});
		return (l.size() > limit) ? new ArrayList<>(l.subList(0, limit)) : l;
	}

	/**
	 * The entries whose name contains every word of the query, ignoring case and accents. Names
	 * starting with the query come first, then live channels, movies and series.
	 */
	public List<Entry> search(String query, int limit) {
		String q = normalize(query);
		if (q.isEmpty()) return Collections.emptyList();
		String[] words = q.split(" ");
		List<Entry> first = new ArrayList<>();
		List<Entry> rest = new ArrayList<>();

		for (int i = 0; i < searchKeys.length; i++) {
			String k = searchKeys[i];
			boolean match = true;
			for (String w : words) {
				if (!k.contains(w)) {
					match = false;
					break;
				}
			}
			if (!match) continue;
			if (k.startsWith(q)) {
				first.add(all.get(i));
				if (first.size() >= limit) break;
			} else if (rest.size() < limit) {
				rest.add(all.get(i));
			}
		}

		first.addAll(rest);
		return (first.size() > limit) ? new ArrayList<>(first.subList(0, limit)) : first;
	}

	/** The categories of a kind with their entries, empty ones left out, in the panel's order. */
	public Map<Category, List<Entry>> rows(Kind kind) {
		Map<Category, List<Entry>> m = new LinkedHashMap<>();
		for (Category c : categories.get(kind)) {
			List<Entry> l = inCategory(kind, c.id);
			if (!l.isEmpty()) m.put(c, l);
		}
		return m;
	}

	/** Lower case, no accents, no punctuation, single spaces: "Ação 2!" -> "acao 2". */
	public static String normalize(String s) {
		if (s == null) return "";
		String n = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
		StringBuilder sb = new StringBuilder(n.length());
		boolean space = true;
		for (int i = 0, len = n.length(); i < len; i++) {
			char c = n.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
				space = false;
			} else if (!space) {
				sb.append(' ');
				space = true;
			}
		}
		int end = sb.length();
		if ((end > 0) && (sb.charAt(end - 1) == ' ')) sb.setLength(end - 1);
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	private static String catKey(Kind k, String id) {
		return k.code + ':' + id;
	}
}
