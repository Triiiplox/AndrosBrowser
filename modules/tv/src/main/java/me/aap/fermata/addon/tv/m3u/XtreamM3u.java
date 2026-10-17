package me.aap.fermata.addon.tv.m3u;

import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import me.aap.utils.log.Log;

/**
 * Builds an M3U playlist from the Xtream Codes API of an IPTV panel (player_api.php), for panels
 * that refuse the M3U download (get.php) but serve the apps that log in with a user and password.
 * Live channels keep their category as the group, movies go to "Movies | category" groups.
 */
final class XtreamM3u {
	private static final String AGENT = "VLC/3.0.20 LibVLC/3.0.20";
	private static final int TIMEOUT = 60000;
	private final String base;
	private final String user;
	private final String pass;
	private final String ext;

	private XtreamM3u(String base, String user, String pass, String ext) {
		this.base = base;
		this.user = user;
		this.pass = pass;
		this.ext = ext;
	}

	// An Xtream M3U link: http://host[:port]/get.php?username=...&password=...[&output=ts|m3u8]
	@Nullable
	static XtreamM3u of(@Nullable String url) {
		if (url == null) return null;
		Uri u = Uri.parse(url.trim());
		String path = u.getPath();
		if ((path == null) || !path.endsWith("/get.php") || (u.getHost() == null)) return null;
		String user = u.getQueryParameter("username");
		String pass = u.getQueryParameter("password");
		if ((user == null) || (pass == null)) return null;
		String out = u.getQueryParameter("output");
		String ext = ((out != null) && (out.equals("m3u8") || out.equals("hls"))) ? "m3u8" : "ts";
		String base = u.getScheme() + "://" + u.getHost() + ((u.getPort() > 0) ? ":" + u.getPort() : "");
		return new XtreamM3u(base, user, pass, ext);
	}

	String epgUrl() {
		return base + "/xmltv.php?username=" + Uri.encode(user) + "&password=" + Uri.encode(pass);
	}

	// Blocking: call it off the main thread. The file is replaced only when the build succeeds.
	void build(File dst, String movieGroup) throws IOException {
		long start = System.currentTimeMillis();
		File tmp = new File(dst.getPath() + ".xtream");
		int live;
		int vod;

		try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),
				StandardCharsets.UTF_8))) {
			w.write("#EXTM3U\n");
			live = writeStreams(w, categories("get_live_categories"), "get_live_streams", "live", null);
			vod = writeStreams(w, categories("get_vod_categories"), "get_vod_streams", "movie",
					movieGroup);
		} catch (IOException ex) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw ex;
		}

		if ((live + vod) == 0) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw new IOException("Xtream API: no channels or movies");
		}
		if (!tmp.renameTo(dst)) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw new IOException("Failed to rename " + tmp + " to " + dst);
		}
		Log.i("Xtream API list: ", live, " channels, ", vod, " movies in ",
				System.currentTimeMillis() - start, " ms");
	}

	private Map<String, String> categories(String action) throws IOException {
		Map<String, String> cats = new HashMap<>();
		try (JsonReader r = open(action)) {
			if (r.peek() != JsonToken.BEGIN_ARRAY) {
				r.skipValue();
				return cats;
			}
			r.beginArray();
			while (r.hasNext()) {
				String id = null;
				String name = null;
				r.beginObject();
				while (r.hasNext()) {
					String k = r.nextName();
					if (k.equals("category_id")) id = value(r);
					else if (k.equals("category_name")) name = value(r);
					else r.skipValue();
				}
				r.endObject();
				if ((id != null) && (name != null)) cats.put(id, name);
			}
			r.endArray();
		}
		return cats;
	}

	private int writeStreams(Writer w, Map<String, String> cats, String action, String kind,
													 @Nullable String groupPrefix) throws IOException {
		int count = 0;
		try (JsonReader r = open(action)) {
			if (r.peek() != JsonToken.BEGIN_ARRAY) {
				r.skipValue();
				return 0;
			}
			r.beginArray();
			while (r.hasNext()) {
				String id = null, name = null, icon = null, cat = null, epg = null, cext = null;
				r.beginObject();
				while (r.hasNext()) {
					switch (r.nextName()) {
						case "stream_id" -> id = value(r);
						case "name" -> name = value(r);
						case "stream_icon" -> icon = value(r);
						case "category_id" -> cat = value(r);
						case "epg_channel_id" -> epg = value(r);
						case "container_extension" -> cext = value(r);
						default -> r.skipValue();
					}
				}
				r.endObject();
				if ((id == null) || (name == null)) continue;

				String group = (cat != null) ? cats.get(cat) : null;
				if (group == null) group = "";
				if (groupPrefix != null) group = group.isEmpty() ? groupPrefix : groupPrefix + " | " + group;
				String e = (cext != null) && !cext.isEmpty() ? cext : ext;

				w.write("#EXTINF:-1");
				if ((epg != null) && !epg.isEmpty()) attr(w, "tvg-id", epg);
				attr(w, "tvg-name", name);
				if ((icon != null) && !icon.isEmpty()) attr(w, "tvg-logo", icon);
				if (!group.isEmpty()) attr(w, "group-title", group);
				w.write(',');
				w.write(name.replace('\n', ' '));
				w.write('\n');
				w.write(base + '/' + kind + '/' + user + '/' + pass + '/' + id + '.' + e);
				w.write('\n');
				count++;
			}
			r.endArray();
		}
		return count;
	}

	private static void attr(Writer w, String name, String value) throws IOException {
		w.write(' ');
		w.write(name);
		w.write("=\"");
		w.write(value.replace('"', '\'').replace('\n', ' '));
		w.write('"');
	}

	// Numbers, strings and booleans as text; null and nested values as null
	@Nullable
	private static String value(JsonReader r) throws IOException {
		switch (r.peek()) {
			case STRING:
			case NUMBER:
				return r.nextString();
			case BOOLEAN:
				return String.valueOf(r.nextBoolean());
			case NULL:
				r.nextNull();
				return null;
			default:
				r.skipValue();
				return null;
		}
	}

	private JsonReader open(String action) throws IOException {
		URL url = new URL(base + "/player_api.php?username=" + Uri.encode(user) + "&password=" +
				Uri.encode(pass) + "&action=" + action);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setConnectTimeout(TIMEOUT);
		c.setReadTimeout(TIMEOUT);
		c.setRequestProperty("User-Agent", AGENT);
		c.setRequestProperty("Accept", "*/*");
		int code = c.getResponseCode();
		if (code != HttpURLConnection.HTTP_OK) {
			c.disconnect();
			throw new IOException("Xtream API " + action + ": HTTP " + code);
		}
		InputStream in = c.getInputStream();
		return new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}
}
