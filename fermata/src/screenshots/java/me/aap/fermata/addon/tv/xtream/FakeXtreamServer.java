package me.aap.fermata.addon.tv.xtream;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An Xtream panel on localhost with a catalog like a real one: categories, channels with logos,
 * movies and series with posters and backdrops drawn on the fly, details and a guide.
 */
final class FakeXtreamServer implements AutoCloseable {
	private static final String[] LIVE_CATS = {"Abertos", "Esportes", "Notícias", "Filmes 24h"};
	private static final String[] CHANNELS = {"Globo SP HD", "SBT", "Record", "Band", "SporTV",
			"ESPN", "Premiere 1", "GloboNews", "CNN Brasil", "BandNews", "Telecine Action",
			"HBO", "Megapix", "TNT", "Space", "Cultura"};
	private static final String[] MOVIE_CATS = {"Lançamentos", "Ação", "Comédia", "Drama", "Infantil"};
	private static final String[] MOVIES = {"Duna: Parte Dois", "Oppenheimer", "O Auto da Compadecida 2",
			"Divertida Mente 2", "Ainda Estou Aqui", "Gladiador II", "Deadpool & Wolverine",
			"Coringa: Delírio a Dois", "Wicked", "Moana 2", "Alien: Romulus", "Twisters",
			"Furiosa", "Bad Boys: Até o Fim", "Meu Malvado Favorito 4", "Planeta dos Macacos: O Reinado",
			"Tropa de Elite", "Cidade de Deus", "Central do Brasil", "Bacurau"};
	private static final String[] SERIES_CATS = {"Séries Netflix", "HBO", "Nacionais"};
	private static final String[] SERIES = {"Cidade Invisível", "The Last of Us", "Sintonia",
			"Round 6", "Bom Dia, Verônica", "House of the Dragon", "Stranger Things", "Dark",
			"Sessão de Terapia", "Os Donos do Jogo"};
	private static final int[] PALETTE = {0xFF8E2DE2, 0xFFE52D27, 0xFF1D976C, 0xFFF7971E, 0xFF2193B0,
			0xFFCC2B5E, 0xFF4568DC, 0xFF11998E, 0xFFFC4A1A, 0xFF834D9B};

	private final ServerSocket server;
	private final Map<String, byte[]> images = new HashMap<>();
	final String url;

	FakeXtreamServer() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
		url = "http://127.0.0.1:" + server.getLocalPort() + "/";
		Thread t = new Thread(() -> {
			while (!server.isClosed()) {
				try {
					Socket c = server.accept();
					Thread h = new Thread(() -> handle(c), "Fake Xtream request");
					h.setDaemon(true);
					h.start();
				} catch (IOException ignore) {
				}
			}
		}, "Fake Xtream");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void close() throws IOException {
		server.close();
	}

	// One request per connection: the request line, the headers skipped, the answer, closed
	private void handle(Socket c) {
		try (c; OutputStream out = c.getOutputStream()) {
			BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(),
					StandardCharsets.ISO_8859_1));
			String line = in.readLine();
			if (line == null) return;
			for (String h = in.readLine(); (h != null) && !h.isEmpty(); h = in.readLine()) ;
			URI u = URI.create(line.split(" ")[1]);
			String path = u.getPath();
			byte[] body;
			String type;
			int code = 200;
			if (path.startsWith("/img/")) {
				body = image(path.substring(5));
				type = "image/png";
			} else if (path.endsWith("player_api.php")) {
				body = api(query(u.getRawQuery()).get("action")).getBytes(StandardCharsets.UTF_8);
				type = "application/json";
			} else {
				body = new byte[0];
				type = "text/plain";
				code = 404;
			}
			String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") +
					"\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length +
					"\r\nConnection: close\r\n\r\n";
			out.write(head.getBytes(StandardCharsets.ISO_8859_1));
			out.write(body);
		} catch (IOException ignore) {
		}
	}

	private static Map<String, String> query(String q) {
		Map<String, String> m = new HashMap<>();
		if (q == null) return m;
		for (String kv : q.split("&")) {
			int i = kv.indexOf('=');
			if (i > 0) m.put(kv.substring(0, i), java.net.URLDecoder.decode(kv.substring(i + 1),
					StandardCharsets.UTF_8));
		}
		return m;
	}

	private String api(String action) {
		long now = System.currentTimeMillis() / 1000;
		if (action == null) {
			return "{\"user_info\":{\"username\":\"andro\",\"status\":\"Active\",\"exp_date\":\"" +
					(now + 40L * 86400) + "\",\"is_trial\":\"0\",\"active_cons\":\"0\"," +
					"\"max_connections\":\"1\"},\"server_info\":{\"timezone\":\"America/Sao_Paulo\"}}";
		}
		StringBuilder sb = new StringBuilder("[");
		switch (action) {
			case "get_live_categories" -> cats(sb, LIVE_CATS, 1);
			case "get_vod_categories" -> cats(sb, MOVIE_CATS, 10);
			case "get_series_categories" -> cats(sb, SERIES_CATS, 20);
			case "get_live_streams" -> {
				for (int i = 0; i < CHANNELS.length; i++) {
					if (i > 0) sb.append(',');
					sb.append("{\"num\":").append(i + 1).append(",\"name\":\"").append(CHANNELS[i])
							.append("\",\"stream_id\":").append(100 + i)
							.append(",\"stream_icon\":\"").append(url).append("img/logo-").append(i)
							.append("\",\"epg_channel_id\":\"ch").append(i).append("\",\"added\":\"")
							.append(now - i * 3600).append("\",\"category_id\":\"").append(1 + i % LIVE_CATS.length)
							.append("\",\"tv_archive\":").append(i % 3 == 0 ? 1 : 0)
							.append(",\"tv_archive_duration\":\"3\"}");
				}
			}
			case "get_vod_streams" -> {
				for (int i = 0; i < MOVIES.length; i++) {
					if (i > 0) sb.append(',');
					sb.append("{\"num\":").append(i + 1).append(",\"name\":\"").append(MOVIES[i])
							.append("\",\"stream_id\":").append(200 + i)
							.append(",\"stream_icon\":\"").append(url).append("img/poster-m").append(i)
							.append("\",\"rating\":\"").append(String.format(Locale.US, "%.1f", 6.5 + (i % 7) * 0.4))
							.append("\",\"added\":\"").append(now - i * 86400L)
							.append("\",\"category_id\":\"").append(10 + i % MOVIE_CATS.length)
							.append("\",\"container_extension\":\"mkv\"}");
				}
			}
			case "get_series" -> {
				for (int i = 0; i < SERIES.length; i++) {
					if (i > 0) sb.append(',');
					sb.append("{\"num\":").append(i + 1).append(",\"name\":\"").append(SERIES[i])
							.append("\",\"series_id\":").append(300 + i)
							.append(",\"cover\":\"").append(url).append("img/poster-s").append(i)
							.append("\",\"plot\":\"Uma história que prende do primeiro ao último episódio.\"")
							.append(",\"genre\":\"Drama, Suspense\",\"releaseDate\":\"2021-02-05\"")
							.append(",\"last_modified\":\"").append(now - i * 86400L)
							.append("\",\"rating\":\"").append(String.format(Locale.US, "%.1f", 7.0 + (i % 5) * 0.3))
							.append("\",\"backdrop_path\":[\"").append(url).append("img/backdrop-s").append(i)
							.append("\"],\"category_id\":\"").append(20 + i % SERIES_CATS.length).append("\"}");
				}
			}
			case "get_vod_info" -> {
				return "{\"info\":{\"movie_image\":\"" + url + "img/poster-m0\",\"plot\":\"Paul Atreides " +
						"se une aos Fremen em uma jornada de vingança contra os conspiradores que destruíram " +
						"sua família.\",\"cast\":\"Timothée Chalamet, Zendaya, Rebecca Ferguson\"," +
						"\"director\":\"Denis Villeneuve\",\"genre\":\"Ficção científica, Aventura\"," +
						"\"releasedate\":\"2024-02-29\",\"duration_secs\":9960,\"duration\":\"02:46:00\"," +
						"\"rating\":\"8.5\",\"backdrop_path\":[\"" + url + "img/backdrop-m0\"]}," +
						"\"movie_data\":{\"stream_id\":200,\"container_extension\":\"mkv\"}}";
			}
			case "get_series_info" -> {
				StringBuilder e = new StringBuilder("{\"seasons\":[");
				for (int s = 1; s <= 3; s++) {
					if (s > 1) e.append(',');
					e.append("{\"season_number\":").append(s).append(",\"name\":\"Temporada ").append(s).append("\"}");
				}
				e.append("],\"info\":{\"name\":\"").append(SERIES[0]).append("\",\"plot\":\"Um detetive " +
						"descobre um mundo de entidades do folclore brasileiro.\",\"episode_run_time\":\"45\"," +
						"\"backdrop_path\":[\"").append(url).append("img/backdrop-s0\"]},\"episodes\":{");
				for (int s = 1; s <= 3; s++) {
					if (s > 1) e.append(',');
					e.append('"').append(s).append("\":[");
					for (int n = 1; n <= 8; n++) {
						if (n > 1) e.append(',');
						e.append("{\"id\":\"").append(s * 100 + n).append("\",\"episode_num\":").append(n)
								.append(",\"title\":\"Episódio ").append(n).append("\",\"container_extension\":\"mp4\"")
								.append(",\"info\":{\"duration_secs\":\"2700\",\"plot\":\"A investigação avança.\"," +
										"\"movie_image\":\"").append(url).append("img/backdrop-e").append(n).append("\"}}");
					}
					e.append(']');
				}
				return e.append("}}").toString();
			}
			case "get_short_epg" -> {
				String now1 = b64("Jornal da Manhã"), next = b64("Futebol: Corinthians x Palmeiras");
				return "{\"epg_listings\":[{\"title\":\"" + now1 + "\",\"description\":\"" + b64("Notícias") +
						"\",\"start_timestamp\":\"" + (now - 1200) + "\",\"stop_timestamp\":\"" + (now + 2400) +
						"\"},{\"title\":\"" + next + "\",\"description\":\"\",\"start_timestamp\":\"" +
						(now + 2400) + "\",\"stop_timestamp\":\"" + (now + 9600) + "\"}]}";
			}
			default -> {
			}
		}
		return sb.append(']').toString();
	}

	private static void cats(StringBuilder sb, String[] names, int first) {
		for (int i = 0; i < names.length; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"category_id\":\"").append(first + i).append("\",\"category_name\":\"")
					.append(names[i]).append("\"}");
		}
	}

	private static String b64(String s) {
		return java.util.Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private synchronized byte[] image(String name) {
		byte[] b = images.get(name);
		if (b != null) return b;
		int n = Math.abs(name.hashCode());
		boolean poster = name.startsWith("poster");
		boolean logo = name.startsWith("logo");
		int w = logo ? 256 : poster ? 300 : 640;
		int h = logo ? 256 : poster ? 450 : 360;
		Bitmap img = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		Canvas g = new Canvas(img);
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		int c1 = PALETTE[n % PALETTE.length];
		int c2 = darker(PALETTE[(n / 7) % PALETTE.length]);
		p.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
		g.drawRect(0, 0, w, h, p);
		p.setShader(null);
		p.setColor(0xDCFFFFFF);
		p.setTypeface(Typeface.DEFAULT_BOLD);
		p.setTextSize(logo ? 64 : poster ? 40 : 56);
		String label = logo ? name.substring(5) : name.replaceAll("^[a-z]+-", "").toUpperCase(Locale.ROOT);
		g.drawText(label, w / 10f, h * 3 / 4f, p);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		img.compress(Bitmap.CompressFormat.PNG, 100, out);
		b = out.toByteArray();
		images.put(name, b);
		return b;
	}

	private static int darker(int c) {
		return Color.rgb(Color.red(c) / 3, Color.green(c) / 3, Color.blue(c) / 3);
	}
}
