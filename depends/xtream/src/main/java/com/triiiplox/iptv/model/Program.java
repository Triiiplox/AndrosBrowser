package com.triiiplox.iptv.model;

/** A program of the guide of a live channel. */
public final class Program {
	public final String title;
	public final String description;
	/** Epoch seconds. */
	public final long start;
	public final long end;
	/** The panel keeps it for catch-up. */
	public final boolean archived;
	/** The start as the panel writes it, in its own time zone (yyyy-MM-dd HH:mm:ss), if it does. */
	public final String panelStart;

	public Program(String title, String description, long start, long end, boolean archived) {
		this(title, description, start, end, archived, null);
	}

	public Program(String title, String description, long start, long end, boolean archived,
								 String panelStart) {
		this.title = title;
		this.description = description;
		this.start = start;
		this.end = end;
		this.archived = archived;
		this.panelStart = panelStart;
	}

	/** Over, and kept by the panel: it can be watched again. */
	public boolean canCatchUp(long nowSec) {
		return archived && (end <= nowSec);
	}

	public boolean isOnAir(long nowSec) {
		return (start <= nowSec) && (nowSec < end);
	}
}
