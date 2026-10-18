package com.triiiplox.iptv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.triiiplox.iptv.model.AccountInfo;
import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.LiveChannel;
import com.triiiplox.iptv.model.Movie;
import com.triiiplox.iptv.model.Program;
import com.triiiplox.iptv.model.Series;

import org.junit.Test;

import java.util.List;

public class XtreamClientTest {
	private final FakePanel panel = new FakePanel();
	private final XtreamClient client = new XtreamClient(FakePanel.account(), panel);

	@Test
	public void accountInfo() throws Exception {
		AccountInfo i = client.accountInfo();
		assertTrue(i.isActive());
		assertTrue(i.trial);
		assertEquals(1792270540L, i.expires);
		assertEquals(1, i.maxConnections);
		assertEquals("America/Sao_Paulo", i.timezone);
	}

	@Test
	public void categoriesAcceptNumbersSpacesAndObjects() throws Exception {
		List<Category> live = client.categories(Kind.LIVE);
		assertEquals(3, live.size());
		assertEquals("2", live.get(1).id);
		assertEquals("Esportes", live.get(1).name);
		List<Category> series = client.categories(Kind.SERIES);
		assertEquals(1, series.size());
		assertEquals("Drama", series.get(0).name);
	}

	@Test
	public void liveChannels() throws Exception {
		List<LiveChannel> l = client.liveChannels();
		assertEquals("an entry without an id is skipped", 3, l.size());
		LiveChannel g = l.get(0);
		assertEquals("101", g.id);
		assertEquals("http://img/globo.png", g.image);
		assertEquals("globo.br", g.epgId);
		assertEquals(3, g.archiveDays);
		assertNull("an empty icon is no icon", l.get(1).image);
		assertNull(l.get(1).epgId);
		assertEquals(0, l.get(1).archiveDays);
		assertEquals("Canal \"Aspas\" ç", l.get(2).name);
	}

	@Test
	public void movies() throws Exception {
		List<Movie> l = client.movies();
		assertEquals(3, l.size());
		assertEquals("mkv", l.get(0).extension);
		assertEquals(8.5f, l.get(0).rating, 0.01);
		assertEquals("the 5 based rating when there is no other", 9f, l.get(1).rating, 0.01);
		assertEquals(1750000000L, l.get(1).added);
		assertEquals(0, l.get(2).added);
		assertEquals(0f, l.get(2).rating, 0);
	}

	@Test
	public void series() throws Exception {
		List<Series> l = client.series();
		assertEquals(2, l.size());
		Series s = l.get(0);
		assertEquals("2021", s.year);
		assertEquals("http://img/ci_bd.jpg", s.backdrop);
		assertEquals(1765000000L, s.added);
		assertNull(l.get(1).backdrop);
	}

	@Test
	public void movieDetails() throws Exception {
		Details d = client.movieDetails("201");
		assertEquals("Paul Atreides...", d.plot);
		assertEquals("Denis Villeneuve", d.director);
		assertEquals(9960, d.duration);
		assertEquals("2024", d.year());
		assertEquals("https://img/duna_bd.jpg", d.backdrop());
		assertEquals("Way9Dexny3w", d.trailer);
		assertTrue(d.seasons.isEmpty());
	}

	@Test
	public void seriesDetailsSortsSeasonsAndEpisodes() throws Exception {
		Details d = client.seriesDetails("301");
		assertEquals(2, d.seasons.size());
		Details.Season s1 = d.seasons.get(0);
		assertEquals(1, s1.number);
		assertEquals("Temporada 1", s1.name);
		assertEquals("http://img/s1.jpg", s1.cover);
		assertEquals(2, s1.episodes.size());
		assertEquals("401", s1.episodes.get(0).id);
		assertEquals(2650, s1.episodes.get(0).duration);
		assertEquals("http://img/e1.jpg", s1.episodes.get(0).image);
		assertEquals("info as an empty array is no info", 0, s1.episodes.get(1).duration);
		Details.Season s2 = d.seasons.get(1);
		assertEquals("http://img/s2b.jpg", s2.cover);
		assertEquals(2700, s2.episodes.get(0).duration);
		assertEquals("a single backdrop as a string", "http://img/bd.jpg", d.backdrop());
		assertEquals(45 * 60, d.duration);
	}

	@Test
	public void episodesAsArrayOfArrays() throws Exception {
		panel.responses.put("get_series_info", "{\"episodes\":[[{\"id\":1,\"episode_num\":1}],[{\"id\":2," +
				"\"episode_num\":1},{\"id\":3,\"episode_num\":2}]]}");
		Details d = client.seriesDetails("1");
		assertEquals(2, d.seasons.size());
		assertEquals(2, d.seasons.get(1).episodes.size());
		assertNull(d.seasons.get(0).name);
	}

	@Test
	public void shortEpgDecodesBase64AndSorts() throws Exception {
		List<Program> l = client.shortEpg("101", 4);
		assertEquals(2, l.size());
		assertEquals("Novela", l.get(0).title);
		assertNull(l.get(0).description);
		assertTrue(l.get(0).archived);
		assertEquals("Jornal Nacional", l.get(1).title);
		assertEquals("Notícias", l.get(1).description);
		assertTrue(l.get(1).isOnAir(2500));
		assertFalse(l.get(1).isOnAir(3000));
	}

	@Test
	public void fullEpgKeepsThePanelStartForCatchUp() throws Exception {
		List<Program> l = client.fullEpg("101");
		assertEquals(2, l.size());
		Program game = l.get(0);
		assertEquals("Futebol", game.title);
		assertEquals("2026-10-17 21:30:00", game.panelStart);
		assertTrue(game.canCatchUp(1792290000));
		assertFalse("not over yet", game.canCatchUp(1792285000));
		assertFalse("not kept by the panel", l.get(1).canCatchUp(1792300000));
		assertTrue(panel.requests.get(panel.requests.size() - 1)
				.contains("action=get_simple_data_table&stream_id=101"));

		// By the panel's clock: its own start, 110 minutes
		assertEquals("http://panel.test:8080/timeshift/user/p%40ss%20word/110/2026-10-17:21-30/101.ts",
				FakePanel.account().catchupUrl("101", game, null));
		// No start of its own: the epoch in the panel's time zone (00:30 UTC is 21:30 in São Paulo)
		Program bare = new Program("x", null, game.start, game.end, true);
		assertEquals("http://panel.test:8080/timeshift/user/p%40ss%20word/110/2026-10-17:21-30/101.ts",
				FakePanel.account().catchupUrl("101", bare, "America/Sao_Paulo"));
		assertEquals("2026-10-18:00-30", XtreamAccount.timeshiftStart(bare, null));
	}

	@Test
	public void unexpectedShapesAreEmptyNotErrors() throws Exception {
		panel.responses.put("get_vod_streams", "{\"user_info\":{\"auth\":0}}");
		panel.responses.put("get_live_streams", "null");
		panel.responses.put("get_vod_info", "[]");
		assertTrue(client.movies().isEmpty());
		assertTrue(client.liveChannels().isEmpty());
		assertNull(client.movieDetails("1").plot);
	}

	@Test(expected = java.io.IOException.class)
	public void loginRefused() throws Exception {
		panel.responses.put(null, "{\"user_info\":{\"auth\":0}}");
		client.accountInfo();
	}

	@Test
	public void requestsCarryTheEncodedCredentials() throws Exception {
		client.movies();
		String url = panel.requests.get(0);
		assertTrue(url, url.startsWith("http://panel.test:8080/player_api.php?username=user&password=p%40ss%20word"));
		assertNotNull(FakePanel.action(url));
	}
}
