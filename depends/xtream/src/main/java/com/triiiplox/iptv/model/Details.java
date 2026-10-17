package com.triiiplox.iptv.model;

import java.util.Collections;
import java.util.List;

/**
 * The details page of a movie or a series (get_vod_info / get_series_info). Every field may be
 * missing: panels fill them from TMDB, some not at all.
 */
public final class Details {
	public final String plot;
	public final String cast;
	public final String director;
	public final String genre;
	public final String releaseDate;
	/** Seconds, 0 if unknown. */
	public final int duration;
	public final float rating;
	public final String poster;
	public final List<String> backdrops;
	/** A YouTube video id or URL, or null. */
	public final String trailer;
	/** The seasons of a series, in order; empty for a movie. */
	public final List<Season> seasons;

	public Details(String plot, String cast, String director, String genre, String releaseDate,
								 int duration, float rating, String poster, List<String> backdrops, String trailer,
								 List<Season> seasons) {
		this.plot = plot;
		this.cast = cast;
		this.director = director;
		this.genre = genre;
		this.releaseDate = releaseDate;
		this.duration = duration;
		this.rating = rating;
		this.poster = poster;
		this.backdrops = (backdrops == null) ? Collections.emptyList() : backdrops;
		this.trailer = trailer;
		this.seasons = (seasons == null) ? Collections.emptyList() : seasons;
	}

	public String backdrop() {
		return backdrops.isEmpty() ? null : backdrops.get(0);
	}

	/** The release year, or null. */
	public String year() {
		if ((releaseDate == null) || (releaseDate.length() < 4)) return null;
		String y = releaseDate.substring(0, 4);
		for (int i = 0; i < 4; i++) if (!Character.isDigit(y.charAt(i))) return null;
		return y;
	}

	public static final class Season {
		public final int number;
		public final String name;
		public final String cover;
		public final List<Episode> episodes;

		public Season(int number, String name, String cover, List<Episode> episodes) {
			this.number = number;
			this.name = name;
			this.cover = cover;
			this.episodes = episodes;
		}
	}

	public static final class Episode {
		public final String id;
		public final int season;
		public final int number;
		public final String title;
		public final String extension;
		public final String plot;
		/** Seconds, 0 if unknown. */
		public final int duration;
		public final String image;

		public Episode(String id, int season, int number, String title, String extension, String plot,
									 int duration, String image) {
			this.id = id;
			this.season = season;
			this.number = number;
			this.title = title;
			this.extension = extension;
			this.plot = plot;
			this.duration = duration;
			this.image = image;
		}
	}
}
