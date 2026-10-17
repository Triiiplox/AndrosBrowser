package com.triiiplox.iptv.model;

/**
 * An entry of the catalog list: a live channel, a movie or a series. Only what the lists of the
 * panel carry; the details (plot, cast, seasons) are loaded on demand.
 */
public class Entry {
	public final Kind kind;
	public final String id;
	public final String name;
	/** The logo of a channel, the poster of a movie or a series, or null. */
	public final String image;
	public final String categoryId;
	/** When it was added to the panel, epoch seconds, 0 if unknown. */
	public final long added;
	/** 0..10, 0 if unknown. */
	public final float rating;

	public Entry(Kind kind, String id, String name, String image, String categoryId, long added,
							 float rating) {
		this.kind = kind;
		this.id = id;
		this.name = name;
		this.image = image;
		this.categoryId = categoryId;
		this.added = added;
		this.rating = rating;
	}

	/** Unique within an account, e.g. "movie:123". */
	public String key() {
		return key(kind, id);
	}

	public static String key(Kind kind, String id) {
		return kind.code + ':' + id;
	}

	@Override
	public String toString() {
		return key() + ' ' + name;
	}

	public enum Kind {
		LIVE("live"), MOVIE("movie"), SERIES("series"), EPISODE("episode");

		public final String code;

		Kind(String code) {
			this.code = code;
		}

		public static Kind of(String code) {
			for (Kind k : values()) if (k.code.equals(code)) return k;
			return null;
		}
	}
}
