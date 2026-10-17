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

	public Program(String title, String description, long start, long end, boolean archived) {
		this.title = title;
		this.description = description;
		this.start = start;
		this.end = end;
		this.archived = archived;
	}

	public boolean isOnAir(long nowSec) {
		return (start <= nowSec) && (nowSec < end);
	}
}
