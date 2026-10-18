package com.triiiplox.iptv;

import com.triiiplox.iptv.net.HttpException;
import com.triiiplox.iptv.net.Transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake Xtream panel answering by action, with the quirks of real ones: ids as numbers or
 * strings, empty strings, nulls, lists as objects, base64 EPG.
 */
final class FakePanel implements Transport {
	final Map<String, String> responses = new HashMap<>();
	final Map<String, Integer> errors = new HashMap<>();
	final List<String> requests = new ArrayList<>();
	boolean offline;

	FakePanel() {
		responses.put(null, "{\"user_info\":{\"username\":\"u\",\"status\":\"Active\",\"exp_date\":\"1792270540\"," +
				"\"is_trial\":\"1\",\"active_cons\":0,\"max_connections\":\"1\"}," +
				"\"server_info\":{\"timezone\":\"America/Sao_Paulo\",\"url\":\"panel\"}}");
		responses.put("get_live_categories", "[{\"category_id\":\"1\",\"category_name\":\"Abertos\",\"parent_id\":0}," +
				"{\"category_id\":2,\"category_name\":\" Esportes \"},{\"category_id\":\"3\",\"category_name\":\"Vazia\"}]");
		responses.put("get_live_streams", "[" +
				"{\"num\":1,\"name\":\"Globo SP\",\"stream_type\":\"live\",\"stream_id\":101,\"stream_icon\":\"http://img/globo.png\"," +
				"\"epg_channel_id\":\"globo.br\",\"added\":\"1700000000\",\"category_id\":\"1\",\"tv_archive\":1,\"tv_archive_duration\":\"3\"}," +
				"{\"num\":\"2\",\"name\":\"SporTV\",\"stream_id\":\"102\",\"stream_icon\":\"\",\"epg_channel_id\":null,\"category_id\":2}," +
				"{\"name\":\"Sem id\",\"category_id\":\"1\"}," +
				"{\"num\":3,\"name\":\"Canal \\\"Aspas\\\" \\u00e7\",\"stream_id\":103,\"category_id\":\"9\",\"tv_archive\":0}]");
		responses.put("get_vod_categories", "[{\"category_id\":\"10\",\"category_name\":\"Ação\"},{\"category_id\":\"11\",\"category_name\":\"Comédia\"}]");
		responses.put("get_vod_streams", "[" +
				"{\"num\":1,\"name\":\"Duna: Parte Dois\",\"stream_id\":201,\"stream_icon\":\"https://img/duna.jpg\",\"rating\":\"8.5\"," +
				"\"rating_5based\":4.3,\"added\":\"1760000000\",\"category_id\":\"10\",\"container_extension\":\"mkv\"}," +
				"{\"name\":\"O Auto da Compadecida\",\"stream_id\":\"202\",\"rating\":\"\",\"rating_5based\":\"4.5\",\"added\":1750000000," +
				"\"category_id\":\"11\",\"container_extension\":\"mp4\"}," +
				"{\"name\":\"Velho\",\"stream_id\":203,\"rating\":null,\"added\":\"\",\"category_id\":\"10\"}]");
		responses.put("get_series_categories", "{\"0\":{\"category_id\":\"20\",\"category_name\":\"Drama\"}}");
		responses.put("get_series", "[" +
				"{\"num\":1,\"name\":\"Cidade Invisível\",\"series_id\":301,\"cover\":\"http://img/ci.jpg\",\"plot\":\"Folclore\"," +
				"\"genre\":\"Fantasia\",\"releaseDate\":\"2021-02-05\",\"last_modified\":\"1765000000\",\"rating\":\"7.4\"," +
				"\"backdrop_path\":[\"http://img/ci_bd.jpg\"],\"category_id\":\"20\"}," +
				"{\"name\":\"Sem capa\",\"series_id\":\"302\",\"backdrop_path\":[],\"last_modified\":\"1700000000\",\"category_id\":\"20\"}]");
		responses.put("get_vod_info", "{\"info\":{\"movie_image\":\"https://img/duna_big.jpg\",\"plot\":\"Paul Atreides...\"," +
				"\"cast\":\"Timothée Chalamet\",\"director\":\"Denis Villeneuve\",\"genre\":\"Ficção\",\"releasedate\":\"2024-02-29\"," +
				"\"duration_secs\":9960,\"duration\":\"02:46:00\",\"rating\":\"8.5\",\"backdrop_path\":[\"https://img/duna_bd.jpg\"]," +
				"\"youtube_trailer\":\"Way9Dexny3w\"},\"movie_data\":{\"stream_id\":201,\"container_extension\":\"mkv\"}}");
		responses.put("get_series_info", "{\"seasons\":[{\"season_number\":1,\"name\":\"Temporada 1\",\"cover\":\"http://img/s1.jpg\"}," +
				"{\"season_number\":2,\"name\":\"Temporada 2\",\"cover_big\":\"http://img/s2b.jpg\"}]," +
				"\"info\":{\"name\":\"Cidade Invisível\",\"plot\":\"Folclore\",\"episode_run_time\":\"45\",\"backdrop_path\":\"http://img/bd.jpg\"}," +
				"\"episodes\":{\"2\":[{\"id\":\"402\",\"episode_num\":1,\"title\":\"S2E1\",\"container_extension\":\"mp4\",\"season\":2," +
				"\"info\":{\"duration_secs\":\"2700\",\"plot\":\"Volta\"}}]," +
				"\"1\":[{\"id\":\"401b\",\"episode_num\":\"2\",\"title\":\"S1E2\",\"container_extension\":\"mkv\",\"info\":[]}," +
				"{\"id\":401,\"episode_num\":1,\"title\":\"S1E1\",\"container_extension\":\"mkv\",\"info\":{\"duration\":\"00:44:10\"," +
				"\"movie_image\":\"http://img/e1.jpg\"}}]}}");
		responses.put("get_simple_data_table", "{\"epg_listings\":[" +
				"{\"title\":\"" + b64("Futebol") + "\",\"description\":\"" + b64("Final") + "\"," +
				"\"start\":\"2026-10-17 21:30:00\",\"end\":\"2026-10-17 23:20:00\"," +
				"\"start_timestamp\":\"1792283400\",\"stop_timestamp\":\"1792290000\",\"has_archive\":1}," +
				"{\"title\":\"" + b64("Sem gravação") + "\",\"start_timestamp\":\"1792290000\"," +
				"\"stop_timestamp\":\"1792293600\",\"has_archive\":\"0\"}]}");
		responses.put("get_short_epg", "{\"epg_listings\":[" +
				"{\"title\":\"" + b64("Jornal Nacional") + "\",\"description\":\"" + b64("Notícias") + "\"," +
				"\"start_timestamp\":\"2000\",\"stop_timestamp\":\"3000\",\"has_archive\":0}," +
				"{\"title\":\"" + b64("Novela") + "\",\"description\":\"\",\"start_timestamp\":\"1000\",\"stop_timestamp\":\"2000\"," +
				"\"has_archive\":1}]}");
	}

	static XtreamAccount account() {
		return new XtreamAccount("http://panel.test:8080/", "user", "p@ss word", "ts");
	}

	@Override
	public InputStream open(String url) throws IOException {
		requests.add(url);
		if (offline) throw new java.net.SocketTimeoutException("offline");
		String action = action(url);
		Integer err = errors.get(action);
		if (err != null) throw new HttpException(err, null);
		String body = responses.get(action);
		if (body == null) throw new HttpException(404, "Not Found");
		return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
	}

	int count(String action) {
		int n = 0;
		for (String u : requests) {
			String a = action(u);
			if ((a == null) ? (action == null) : a.equals(action)) n++;
		}
		return n;
	}

	static String action(String url) {
		int i = url.indexOf("&action=");
		if (i < 0) return null;
		String a = url.substring(i + 8);
		int j = a.indexOf('&');
		return (j < 0) ? a : a.substring(0, j);
	}

	private static String b64(String s) {
		return java.util.Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}
}
