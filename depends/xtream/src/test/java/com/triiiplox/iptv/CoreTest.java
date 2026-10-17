package com.triiiplox.iptv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.triiiplox.iptv.json.JsonReader;
import com.triiiplox.iptv.json.JsonToken;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.net.CachingTransport;
import com.triiiplox.iptv.net.HttpException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CoreTest {
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	// --- JSON ---

	@Test
	public void jsonSkipsNestedValuesAndReadsAnyScalar() throws Exception {
		String json = "{\"a\":{\"x\":[1,{\"y\":[true,null]},\"s\"]},\"b\":12.5,\"c\":false,\"d\":\"\"," +
				"\"e\":\"\\u00e9\\n\\\"\\\\\\/\",\"f\":null,\"g\":[],\"h\":-3}";
		try (JsonReader r = new JsonReader(new StringReader(json))) {
			r.beginObject();
			assertEquals("a", r.nextName());
			r.skipValue();
			assertEquals("b", r.nextName());
			assertEquals("12.5", r.nextStringOrNull());
			assertEquals("c", r.nextName());
			assertEquals("false", r.nextStringOrNull());
			assertEquals("d", r.nextName());
			assertNull("an empty string is null", r.nextStringOrNull());
			assertEquals("e", r.nextName());
			assertEquals("é\n\"\\/", r.nextStringOrNull());
			assertEquals("f", r.nextName());
			assertNull(r.nextStringOrNull());
			assertEquals("g", r.nextName());
			assertNull("an array as a scalar is skipped", r.nextStringOrNull());
			assertEquals("h", r.nextName());
			assertEquals("-3", r.nextString());
			assertFalse(r.hasNext());
			r.endObject();
			assertEquals(JsonToken.END_DOCUMENT, r.peek());
		}
	}

	@Test
	public void jsonAcrossBufferBoundaries() throws Exception {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < 5000; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"id\":").append(i).append(",\"name\":\"Nome com acentuação ").append(i).append("\\u0021\"}");
		}
		sb.append(']');
		int n = 0;
		try (JsonReader r = new JsonReader(new StringReader(sb.toString()))) {
			r.beginArray();
			while (r.hasNext()) {
				r.beginObject();
				r.nextName();
				assertEquals(String.valueOf(n), r.nextString());
				r.nextName();
				assertEquals("Nome com acentuação " + n + "!", r.nextString());
				r.endObject();
				n++;
			}
			r.endArray();
		}
		assertEquals(5000, n);
	}

	@Test
	public void jsonRejectsBrokenInput() {
		for (String bad : new String[]{"{\"a\":1", "[1,,2]", "{\"a\" 1}", "\"open", "<html>403</html>"}) {
			try (JsonReader r = new JsonReader(new StringReader(bad))) {
				r.skipValue();
				r.peek();
				fail("Accepted " + bad);
			} catch (IOException expected) {
				// ok
			}
		}
	}

	// --- Account ---

	@Test
	public void accountFromM3uLink() {
		XtreamAccount a = XtreamAccount.fromM3uUrl(
				" https://panel.test:8443/get.php?username=joao&password=s%26cret&type=m3u_plus&output=m3u8 ");
		assertNotNull(a);
		assertEquals("https://panel.test:8443", a.getServer());
		assertEquals("joao", a.getUser());
		assertEquals("s&cret", a.getPassword());
		assertEquals("m3u8", a.getLiveFormat());
		assertEquals("https://panel.test:8443/live/joao/s%26cret/7.m3u8", a.liveUrl("7"));
		assertEquals("https://panel.test:8443/movie/joao/s%26cret/8.mkv", a.movieUrl("8", "mkv"));
		assertEquals("https://panel.test:8443/series/joao/s%26cret/9.mp4", a.episodeUrl("9", null));
		assertFalse("no password in toString", a.toString().contains("cret"));
		assertFalse(a.id().contains("cret"));
		assertEquals("same server and user, same id", a.id(),
				new XtreamAccount("https://panel.test:8443/", "joao", "other", "ts").id());
		assertNull(XtreamAccount.fromM3uUrl("http://host/list.m3u"));
		assertNull(XtreamAccount.fromM3uUrl("http://host/get.php?username=&password=x"));
		assertNull(XtreamAccount.fromM3uUrl("not a url"));
		assertEquals("ts", XtreamAccount.fromM3uUrl("http://h/get.php?username=a&password=b").getLiveFormat());
		assertEquals("http://panel.x", new XtreamAccount("panel.x", "a", "b", null).getServer());
	}

	// --- Cache ---

	@Test
	public void cacheServesFreshCopiesRefreshesStaleOnesAndSurvivesOffline() throws Exception {
		FakePanel panel = new FakePanel();
		long[] now = {1_000_000};
		File dir = tmp.newFolder();
		CachingTransport c = new CachingTransport(panel, dir, url -> 1000, () -> now[0]);
		String url = FakePanel.account().apiUrl("get_vod_streams");

		assertTrue(read(c.open(url)).contains("Duna"));
		assertTrue(read(c.open(url)).contains("Duna"));
		assertEquals("fresh: one request", 1, panel.count("get_vod_streams"));

		now[0] += 2000;
		panel.responses.put("get_vod_streams", "[\"new\"]");
		assertEquals("stale: asked again", "[\"new\"]", read(c.open(url)));
		assertEquals(2, panel.count("get_vod_streams"));

		now[0] += 2000;
		panel.offline = true;
		assertEquals("offline: the stale copy", "[\"new\"]", read(c.open(url)));

		panel.offline = false;
		panel.errors.put("get_vod_streams", 503);
		assertEquals("a failing panel: the stale copy", "[\"new\"]", read(c.open(url, true)));

		String other = FakePanel.account().apiUrl("get_series");
		panel.offline = true;
		try {
			c.open(other);
			fail("Nothing cached, offline");
		} catch (IOException expected) {
			// ok
		}
		for (File f : dir.listFiles()) assertFalse(f.getName(), f.getName().contains("ss"));
	}

	@Test
	public void cutDownloadKeepsTheGoodCopy() throws Exception {
		long[] now = {0};
		boolean[] cut = {false};
		File dir = tmp.newFolder();
		CachingTransport c = new CachingTransport(url -> {
			if (!cut[0]) return new ByteArrayInputStream("good".getBytes(StandardCharsets.UTF_8));
			return new InputStream() {
				int n;

				@Override
				public int read() throws IOException {
					if (n++ < 3) return 'x';
					throw new IOException("connection reset");
				}
			};
		}, dir, url -> 10, () -> now[0]);
		assertEquals("good", read(c.open("u")));
		now[0] = 100;
		cut[0] = true;
		assertEquals("good", read(c.open("u")));
		assertEquals("no leftover", 1, dir.listFiles().length);
	}

	// --- Catalog and snapshot ---

	@Test
	public void catalogIndexesSearchesAndRanks() throws Exception {
		Catalog cat = new Catalog(FakePanel.account(), new FakePanel(), tmp.newFolder());
		Snapshot s = cat.snapshot();
		assertEquals(3, s.size(Kind.LIVE));
		assertEquals(3, s.size(Kind.MOVIE));
		assertEquals(2, s.size(Kind.SERIES));
		assertEquals("Duna: Parte Dois", s.get(Kind.MOVIE, "201").name);
		assertEquals(2, s.inCategory(Kind.MOVIE, "10").size());
		assertEquals("Ação", s.category(Kind.MOVIE, "10").name);
		assertEquals("empty categories are no rows", 2, s.rows(Kind.LIVE).size());

		List<Entry> r = s.search("compadecida", 10);
		assertEquals(1, r.size());
		assertEquals("accents and case", 1, s.search("CIDADE invisivel", 10).size());
		assertEquals("every word, any order", 1, s.search("dois duna", 10).size());
		assertEquals("prefix first", "SporTV", s.search("sp", 10).get(0).name);
		assertTrue(s.search("  ", 10).isEmpty());

		List<Entry> newest = s.newest(Kind.MOVIE, 10);
		assertEquals("no date, not new", 2, newest.size());
		assertEquals("201", newest.get(0).id);
		assertEquals("202", s.topRated(Kind.MOVIE, 10).get(0).id);
	}

	@Test
	public void catalogLoadsWhatThePanelHas() throws Exception {
		FakePanel panel = new FakePanel();
		panel.errors.put("get_series", 404);
		panel.errors.put("get_series_categories", 404);
		Snapshot s = new Catalog(FakePanel.account(), panel, tmp.newFolder()).snapshot();
		assertEquals(0, s.size(Kind.SERIES));
		assertEquals(3, s.size(Kind.MOVIE));

		FakePanel down = new FakePanel();
		down.offline = true;
		try {
			new Catalog(FakePanel.account(), down, tmp.newFolder()).snapshot();
			fail("Nothing at all");
		} catch (IOException expected) {
			// ok
		}
	}

	@Test
	public void catalogOpensOfflineFromTheCache() throws Exception {
		FakePanel panel = new FakePanel();
		File dir = tmp.newFolder();
		Catalog first = new Catalog(FakePanel.account(), panel, dir);
		first.details(first.snapshot().get(Kind.MOVIE, "201"));
		panel.offline = true;
		Catalog again = new Catalog(FakePanel.account(), panel, dir);
		assertEquals(3, again.snapshot().size(Kind.MOVIE));
		assertEquals(3, again.refresh().size(Kind.MOVIE));
		assertNotNull(again.details(again.snapshot().get(Kind.MOVIE, "201")));
	}

	@Test
	public void homeRows() throws Exception {
		Snapshot s = new Catalog(FakePanel.account(), new FakePanel(), tmp.newFolder()).snapshot();
		UserLibrary lib = new UserLibrary(new File(tmp.newFolder(), "lib.json"));
		Home.Page p = Home.start(s, lib, "acc");
		assertEquals("the newest series with a backdrop", "301", p.hero.id);
		assertEquals(Home.Row.Type.NEW_MOVIES, p.rows.get(0).type);

		lib.played(rec(Kind.MOVIE, "201", null, 120_000, 1_000_000, 1));
		lib.toggleFavorite(rec(Kind.SERIES, "301", null, 0, 0, 2));
		p = Home.start(s, lib, "acc");
		assertEquals(Home.Row.Type.CONTINUE, p.rows.get(0).type);
		assertEquals(Home.Row.Type.MY_LIST, p.rows.get(1).type);

		Home.Page movies = Home.of(Kind.MOVIE, s, lib, "acc");
		assertEquals(Home.Row.Type.CONTINUE, movies.rows.get(0).type);
		Home.Page series = Home.of(Kind.SERIES, s, lib, "acc");
		assertEquals("the movie is not continued on the series page", Home.Row.Type.MY_LIST,
				series.rows.get(0).type);
		Home.Page live = Home.of(Kind.LIVE, s, lib, "acc");
		assertNull(live.hero);
		assertEquals("Abertos", live.rows.get(0).category.name);
	}

	// --- User library ---

	@Test
	public void continueWatchingOnePerSeriesNoPeeksNoFinished() throws Exception {
		File f = new File(tmp.newFolder(), "lib.json");
		UserLibrary lib = new UserLibrary(f);
		lib.played(rec(Kind.MOVIE, "1", null, 30_000, 6_000_000, 1));       // a peek
		lib.played(rec(Kind.MOVIE, "2", null, 5_900_000, 6_000_000, 2));    // finished
		lib.played(rec(Kind.MOVIE, "3", null, 600_000, 6_000_000, 3));
		lib.played(rec(Kind.EPISODE, "e1", "s1", 600_000, 2_700_000, 4));
		lib.played(rec(Kind.EPISODE, "e2", "s1", 300_000, 2_700_000, 5));
		lib.played(rec(Kind.LIVE, "101", null, 0, 0, 6));
		lib.played(rec(Kind.MOVIE, "3", null, 900_000, 6_000_000, 7));      // again: moves up

		List<UserLibrary.Record> c = lib.continueWatching("acc", 10);
		assertEquals(2, c.size());
		assertEquals("3", c.get(0).id);
		assertEquals(900_000, c.get(0).position);
		assertEquals("the last episode of the series", "e2", c.get(1).id);
		assertEquals("e2", lib.lastEpisode("acc", "s1").id);
		assertEquals(1, lib.recentChannels("acc", 10).size());
		assertTrue(lib.continueWatching("other", 10).isEmpty());

		assertTrue(lib.toggleFavorite(rec(Kind.MOVIE, "9", null, 0, 0, 8)));
		assertTrue(lib.isFavorite("acc", Kind.MOVIE, "9"));

		UserLibrary reloaded = new UserLibrary(f);
		assertEquals(2, reloaded.continueWatching("acc", 10).size());
		assertTrue(reloaded.isFavorite("acc", Kind.MOVIE, "9"));
		assertFalse(reloaded.toggleFavorite(rec(Kind.MOVIE, "9", null, 0, 0, 9)));
		assertFalse(new UserLibrary(f).isFavorite("acc", Kind.MOVIE, "9"));
	}

	@Test
	public void brokenLibraryFileStartsEmpty() throws Exception {
		File f = new File(tmp.newFolder(), "lib.json");
		try (FileWriter w = new FileWriter(f)) {
			w.write("{\"history\":[{\"a\":\"acc\",\"k\":\"movie\",\"i\":\"1\",\"p\":");
		}
		UserLibrary lib = new UserLibrary(f);
		assertTrue(lib.continueWatching("acc", 10).isEmpty());
		lib.played(rec(Kind.MOVIE, "2", null, 600_000, 6_000_000, 1));
		assertEquals(1, new UserLibrary(f).continueWatching("acc", 10).size());
	}

	@Test
	public void httpErrorsDoNotLeakTheUrl() {
		HttpException e = new HttpException(403, "Forbidden");
		assertEquals("HTTP 403 Forbidden", e.getMessage());
		assertFalse(e.isTransient());
		assertTrue(new HttpException(429, null).isTransient());
		assertTrue(new HttpException(503, null).isTransient());
	}

	@Test
	public void normalize() {
		assertEquals("acao 2", Snapshot.normalize("  Ação 2! "));
		assertEquals("sao paulo fhd", Snapshot.normalize("SÃO-PAULO | FHD"));
		assertEquals("", Snapshot.normalize(null));
	}

	private static UserLibrary.Record rec(Kind k, String id, String series, long pos, long dur,
																				long updated) {
		return new UserLibrary.Record("acc", k, id, "T" + id, null, null, "mp4", series, 1, 1, pos, dur,
				updated);
	}

	private static String read(InputStream in) throws IOException {
		try (in) {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] b = new byte[4096];
			for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
			return out.toString("UTF-8");
		}
	}
}
