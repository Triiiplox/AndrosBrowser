package com.triiiplox.iptv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.LiveChannel;
import com.triiiplox.iptv.model.Movie;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class M3uPlaylistTest {
	private static final String LIST = "\uFEFF#EXTM3U url-tvg=\"http://epg.example/guide.xml\"\n" +
			"#EXTINF:-1 tvg-id=\"globo.br\" tvg-name=\"Globo HD\" tvg-logo=\"http://img/globo.png\" " +
			"group-title=\"Abertos, HD\" tvg-chno=\"5\",Globo HD\n" +
			"http://tv.example/live/globo.ts\n" +
			"#EXTINF:-1 group-title=\"Esportes\",ESPN\n" +
			"#EXTVLCOPT:http-user-agent=Test\n" +
			"http://tv.example/live/espn.m3u8\n" +
			"#EXTINF:-1 tvg-logo=\"http://img/matrix.jpg\" group-title=\"Filmes | Ação\",Matrix (1999)\n" +
			"http://tv.example/movie/matrix.mkv\n" +
			"#EXTINF:-1 group-title=\"Séries\",Dark S01E02 Mentiras\n" +
			"http://tv.example/series/dark-1-2.mp4\n" +
			"#EXTINF:-1 group-title=\"Séries\",Dark S01 E01\n" +
			"http://tv.example/series/dark-1-1.mp4\n" +
			"#EXTINF:-1 group-title=\"Séries\",Dark S02E01\n" +
			"http://tv.example/series/dark-2-1.mp4\n" +
			"#EXTINF:-1,Sem grupo\n" +
			"http://tv.example/live/nogroup\n" +
			"#EXTINF:-1 group-title=\"Esportes\",ESPN de novo\n" +
			"http://tv.example/live/espn.m3u8\n" +
			"http://tv.example/bare.ts\n";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void readsChannelsMoviesAndSeries() throws Exception {
		M3uPlaylist p = parse(LIST);
		Snapshot s = p.snapshot(1);

		assertEquals("the duplicate stream is dropped", 4, s.size(Kind.LIVE));
		assertEquals(1, s.size(Kind.MOVIE));
		assertEquals(1, s.size(Kind.SERIES));

		LiveChannel globo = (LiveChannel) p.entries.get(Kind.LIVE).get(0);
		assertEquals("Globo HD", globo.name);
		assertEquals("http://img/globo.png", globo.image);
		assertEquals("globo.br", globo.epgId);
		assertEquals(5, globo.number);
		assertEquals("http://tv.example/live/globo.ts", globo.url);
		Category c = s.category(Kind.LIVE, globo.categoryId);
		assertEquals("a comma inside a quoted group is kept", "Abertos, HD", c.name);

		Entry noGroup = p.entries.get(Kind.LIVE).get(2);
		assertEquals("Sem grupo", noGroup.name);
		assertEquals(M3uPlaylist.NO_GROUP, s.category(Kind.LIVE, noGroup.categoryId).name);
		assertEquals("a bare URL is named after its file", "bare.ts",
				p.entries.get(Kind.LIVE).get(3).name);

		Movie matrix = (Movie) p.entries.get(Kind.MOVIE).get(0);
		assertEquals("Matrix (1999)", matrix.name);
		assertEquals("mkv", matrix.extension);
		assertEquals("http://tv.example/movie/matrix.mkv", matrix.url);
		assertEquals("Filmes | Ação", s.category(Kind.MOVIE, matrix.categoryId).name);

		Entry dark = p.entries.get(Kind.SERIES).get(0);
		assertEquals("Dark", dark.name);
		Details d = p.series.get(dark.id);
		assertNotNull(d);
		assertEquals(2, d.seasons.size());
		assertEquals(1, d.seasons.get(0).number);
		assertEquals("episodes in order", 1, d.seasons.get(0).episodes.get(0).number);
		assertEquals(2, d.seasons.get(0).episodes.get(1).number);
		assertEquals("Mentiras", d.seasons.get(0).episodes.get(1).title);
		assertEquals("http://tv.example/series/dark-1-2.mp4", d.seasons.get(0).episodes.get(1).url);
		assertEquals(2, d.seasons.get(1).number);
	}

	@Test
	public void idsStayTheSameFromOneDownloadToTheNext() throws Exception {
		M3uPlaylist a = parse(LIST);
		M3uPlaylist b = parse("#EXTM3U\n#EXTINF:-1,Outro\nhttp://tv.example/live/other.ts\n" + LIST);
		assertEquals(a.entries.get(Kind.LIVE).get(0).id, b.entries.get(Kind.LIVE).get(1).id);
		assertEquals(a.entries.get(Kind.SERIES).get(0).id, b.entries.get(Kind.SERIES).get(0).id);
		assertNotNull(b.snapshot(1).get(Kind.LIVE, a.entries.get(Kind.LIVE).get(0).id));
	}

	@Test
	public void tvgTypeWins() throws Exception {
		M3uPlaylist p = parse("#EXTM3U\n" +
				"#EXTINF:-1 tvg-type=\"movie\",A Film S01E01\nhttp://x/a.mp4\n" +
				"#EXTINF:-1 tvg-type=\"series\",Show\nhttp://x/show-ep\n");
		assertEquals(1, p.entries.get(Kind.MOVIE).size());
		assertEquals(1, p.entries.get(Kind.SERIES).size());
		Details d = p.series.get(p.entries.get(Kind.SERIES).get(0).id);
		assertEquals(1, d.seasons.get(0).episodes.size());
	}

	@Test
	public void refusesWhatIsNotAPlaylist() {
		try {
			parse("<html><body>Login</body></html>\n");
			fail("a page of HTML has no stream");
		} catch (IOException expected) {
		}
		try {
			parse("#EXTM3U\n");
			fail("an empty playlist");
		} catch (IOException expected) {
		}
		assertTrue(M3uPlaylist.looksLikePlaylist("\uFEFF#EXTM3U\n"));
		assertTrue(M3uPlaylist.looksLikePlaylist("  #EXTINF:-1,A"));
		assertFalse(M3uPlaylist.looksLikePlaylist("<html>"));
	}

	@Test
	public void catalogOfAPlaylistIsCachedAndHasLocalDetails() throws Exception {
		AtomicInteger downloads = new AtomicInteger();
		Catalog c = Catalog.playlist("http://tv.example/list.m3u", url -> {
			downloads.incrementAndGet();
			return stream(LIST);
		}, tmp.newFolder());
		assertTrue(c.isPlaylist());
		assertNull(c.getClient());

		Snapshot s = c.snapshot();
		assertEquals(4, s.size(Kind.LIVE));
		assertEquals(1, downloads.get());
		assertFalse(c.isStale());

		List<Entry> series = s.entries(Kind.SERIES);
		assertEquals(1, series.size());
		Details d = c.details(series.get(0));
		assertEquals(2, d.seasons.size());
		assertNull(c.details(s.<Entry>entries(Kind.MOVIE).get(0)).plot);
		assertTrue(c.epg("x", 2).isEmpty());

		c.refresh();
		assertEquals(2, downloads.get());
	}

	private static M3uPlaylist parse(String s) throws IOException {
		try (InputStream in = stream(s)) {
			return M3uPlaylist.parse(in);
		}
	}

	private static InputStream stream(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}
}
