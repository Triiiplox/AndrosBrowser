package com.triiiplox.iptv.model;

public final class LiveChannel extends Entry {
	/** The channel id of the XMLTV guide, or null. */
	public final String epgId;
	/** The panel's channel number, 0 if none. */
	public final int number;
	/** Days of catch-up kept by the panel, 0 without catch-up. */
	public final int archiveDays;

	public LiveChannel(String id, String name, String image, String categoryId, long added,
										 String epgId, int number, int archiveDays) {
		super(Kind.LIVE, id, name, image, categoryId, added, 0f);
		this.epgId = epgId;
		this.number = number;
		this.archiveDays = archiveDays;
	}
}
