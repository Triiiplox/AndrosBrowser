package com.triiiplox.iptv.model;

public final class Series extends Entry {
	public final String plot;
	public final String genre;
	public final String year;
	/** A wide picture for the hero and the details page, or null. */
	public final String backdrop;

	/** @param added the last modification: the newest episode, epoch seconds */
	public Series(String id, String name, String image, String categoryId, long added, float rating,
								String plot, String genre, String year, String backdrop) {
		super(Kind.SERIES, id, name, image, categoryId, added, rating);
		this.plot = plot;
		this.genre = genre;
		this.year = year;
		this.backdrop = backdrop;
	}
}
