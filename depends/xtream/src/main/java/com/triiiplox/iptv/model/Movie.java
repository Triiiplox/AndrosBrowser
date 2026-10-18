package com.triiiplox.iptv.model;

public final class Movie extends Entry {
	/** The container of the file: mp4, mkv... */
	public final String extension;

	public Movie(String id, String name, String image, String categoryId, long added, float rating,
							 String extension) {
		this(id, name, image, categoryId, added, rating, extension, null);
	}

	/** @param url the stream of a playlist movie, null for an Xtream one */
	public Movie(String id, String name, String image, String categoryId, long added, float rating,
							 String extension, String url) {
		super(Kind.MOVIE, id, name, image, categoryId, added, rating, url);
		this.extension = extension;
	}
}
