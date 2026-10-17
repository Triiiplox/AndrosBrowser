package com.triiiplox.iptv.model;

public final class Movie extends Entry {
	/** The container of the file: mp4, mkv... */
	public final String extension;

	public Movie(String id, String name, String image, String categoryId, long added, float rating,
							 String extension) {
		super(Kind.MOVIE, id, name, image, categoryId, added, rating);
		this.extension = extension;
	}
}
