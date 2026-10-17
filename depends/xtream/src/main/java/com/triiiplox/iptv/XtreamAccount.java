package com.triiiplox.iptv;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * An Xtream Codes account: the panel address, the user and the password. The password goes into
 * the API and stream URLs only, never into {@link #toString()} or {@link #id()}, which are safe
 * to log and to use as file names.
 */
public final class XtreamAccount {
	private final String server;
	private final String user;
	private final String password;
	private final String liveFormat;

	/**
	 * @param server     scheme, host and port, e.g. http://panel.example:8080
	 * @param liveFormat ts or m3u8, the container of the live streams
	 */
	public XtreamAccount(String server, String user, String password, String liveFormat) {
		this.server = normalizeServer(server);
		this.user = user;
		this.password = password;
		this.liveFormat = "m3u8".equals(liveFormat) ? "m3u8" : "ts";
	}

	/**
	 * The account of an Xtream M3U link (get.php?username=..&password=..[&output=ts|m3u8]), or
	 * null when the link is not one.
	 */
	public static XtreamAccount fromM3uUrl(String url) {
		if (url == null) return null;
		URI u;
		try {
			u = new URI(url.trim());
		} catch (Exception ex) {
			return null;
		}
		String path = u.getRawPath();
		String query = u.getRawQuery();
		if ((u.getHost() == null) || (path == null) || !path.endsWith("/get.php") || (query == null)) {
			return null;
		}
		String user = null, pass = null, out = null;
		for (String p : query.split("&")) {
			int i = p.indexOf('=');
			if (i <= 0) continue;
			String k = p.substring(0, i);
			String v = dec(p.substring(i + 1));
			switch (k) {
				case "username" -> user = v;
				case "password" -> pass = v;
				case "output" -> out = v;
			}
		}
		if ((user == null) || (pass == null) || user.isEmpty()) return null;
		String server = u.getScheme() + "://" + u.getHost() + ((u.getPort() > 0) ? ":" + u.getPort() : "");
		return new XtreamAccount(server, user, pass,
				("m3u8".equals(out) || "hls".equals(out)) ? "m3u8" : "ts");
	}

	public String getServer() {
		return server;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public String getLiveFormat() {
		return liveFormat;
	}

	/** Host without scheme and port, a default display name. */
	public String getHost() {
		try {
			return new URI(server).getHost();
		} catch (Exception ex) {
			return server;
		}
	}

	/** A stable id of the account (server + user), without the password. */
	public String id() {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] h = md.digest((server + '\n' + user).getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++) sb.append(String.format(Locale.ROOT, "%02x", h[i]));
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			return Integer.toHexString((server + user).hashCode());
		}
	}

	public String apiUrl(String action, String... params) {
		StringBuilder sb = new StringBuilder(server.length() + 96).append(server)
				.append("/player_api.php?username=").append(enc(user)).append("&password=").append(enc(password));
		if (action != null) sb.append("&action=").append(action);
		for (int i = 0; i + 1 < params.length; i += 2) {
			sb.append('&').append(params[i]).append('=').append(enc(params[i + 1]));
		}
		return sb.toString();
	}

	public String epgUrl() {
		return server + "/xmltv.php?username=" + enc(user) + "&password=" + enc(password);
	}

	public String liveUrl(String streamId) {
		return streamUrl("live", streamId, liveFormat);
	}

	public String movieUrl(String streamId, String ext) {
		return streamUrl("movie", streamId, (ext == null) || ext.isEmpty() ? "mp4" : ext);
	}

	public String episodeUrl(String episodeId, String ext) {
		return streamUrl("series", episodeId, (ext == null) || ext.isEmpty() ? "mp4" : ext);
	}

	/**
	 * The catch-up (timeshift) URL of a past program of a live channel.
	 *
	 * @param start   the program start, in the panel's time zone, as yyyy-MM-dd:HH-mm
	 * @param minutes the program duration
	 */
	public String timeshiftUrl(String streamId, String start, int minutes) {
		return server + "/timeshift/" + enc(user) + '/' + enc(password) + '/' + minutes + '/' + start +
				'/' + streamId + ".ts";
	}

	private String streamUrl(String kind, String id, String ext) {
		return server + '/' + kind + '/' + enc(user) + '/' + enc(password) + '/' + id + '.' + ext;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof XtreamAccount a)) return false;
		return server.equals(a.server) && user.equals(a.user) && password.equals(a.password) &&
				liveFormat.equals(a.liveFormat);
	}

	@Override
	public int hashCode() {
		return server.hashCode() * 31 + user.hashCode();
	}

	@Override
	public String toString() {
		return "XtreamAccount{" + getHost() + ", " + id() + '}';
	}

	private static String normalizeServer(String s) {
		s = s.trim();
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
		return s;
	}

	// The Charset overloads are Android 13+ only
	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String dec(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (UnsupportedEncodingException | IllegalArgumentException ex) {
			return s;
		}
	}
}
