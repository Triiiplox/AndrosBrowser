package com.triiiplox.iptv.model;

/** The account status of player_api.php without an action. */
public final class AccountInfo {
	/** Active, Expired, Banned, Disabled... as sent by the panel. */
	public final String status;
	/** Epoch seconds, 0 for no expiry. */
	public final long expires;
	public final boolean trial;
	public final int activeConnections;
	public final int maxConnections;
	/** The panel's time zone, e.g. America/Sao_Paulo, or null. */
	public final String timezone;

	public AccountInfo(String status, long expires, boolean trial, int activeConnections,
										 int maxConnections, String timezone) {
		this.status = status;
		this.expires = expires;
		this.trial = trial;
		this.activeConnections = activeConnections;
		this.maxConnections = maxConnections;
		this.timezone = timezone;
	}

	public boolean isActive() {
		return "Active".equalsIgnoreCase(status);
	}
}
