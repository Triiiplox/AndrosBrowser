package me.aap.fermata.addon.tv.xtream;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.model.Entry.Kind;

/**
 * The media library ids of the Xtream items. They describe the item fully, so it can be rebuilt
 * after a restart (last played, favorites, Android Auto) from the cached catalog alone:
 * <pre>
 * xtream                                   the root
 * xtream:a:ACC                             an account
 * xtream:s:ACC:KIND                        live / movie / series of an account
 * xtream:c:ACC:KIND:CAT                    a category
 * xtream:S:ACC:SERIES                      a series (its episodes)
 * xtream:p:ACC:KIND:ID:EXT                 a channel or a movie
 * xtream:e:ACC:SERIES:SEASON:ID:EXT        an episode
 * </pre>
 * ACC is the account id (a hash, no credential). Parts are escaped, ':' never appears in one.
 */
final class XtreamIds {
	static final String SCHEME = "xtream";
	static final String ROOT = SCHEME;

	private XtreamIds() {
	}

	static String account(String acc) {
		return join("a", acc);
	}

	static String section(String acc, Kind kind) {
		return join("s", acc, kind.code);
	}

	static String category(String acc, Kind kind, String cat) {
		return join("c", acc, kind.code, cat);
	}

	static String series(String acc, String seriesId) {
		return join("S", acc, seriesId);
	}

	static String playable(String acc, Kind kind, String id, @Nullable String ext) {
		return join("p", acc, kind.code, id, (ext == null) ? "" : ext);
	}

	static String episode(String acc, String seriesId, int season, String id, @Nullable String ext) {
		return join("e", acc, seriesId, String.valueOf(season), id, (ext == null) ? "" : ext);
	}

	/** A past program of a channel: its start by the panel's clock, its length in minutes. */
	static String catchup(String acc, String streamId, String start, int minutes) {
		return join("t", acc, streamId, start, String.valueOf(minutes));
	}

	/** The parts after the scheme: [type, acc, ...], or null if not an Xtream id. */
	@Nullable
	static String[] parse(String id) {
		if (!id.startsWith(SCHEME + ':')) return null;
		String[] p = id.substring(SCHEME.length() + 1).split(":", -1);
		for (int i = 0; i < p.length; i++) p[i] = unescape(p[i]);
		return (p.length >= 2) ? p : null;
	}

	private static String join(String type, String... parts) {
		StringBuilder sb = new StringBuilder(64).append(SCHEME).append(':').append(type);
		for (String p : parts) sb.append(':').append(escape(p));
		return sb.toString();
	}

	private static String escape(String s) {
		return (s.indexOf(':') < 0) && (s.indexOf('%') < 0) ? s :
				s.replace("%", "%25").replace(":", "%3A");
	}

	private static String unescape(String s) {
		return (s.indexOf('%') < 0) ? s : s.replace("%3A", ":").replace("%25", "%");
	}
}
