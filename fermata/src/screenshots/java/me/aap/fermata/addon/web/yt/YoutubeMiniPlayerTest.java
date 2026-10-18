package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.screens.Screens;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * The mini player on a video's page: the bar, minimizing to a corner with the browsing over the
 * screen, a tap on the corner, Back. The site does not load here: its page changes are told to the
 * player as the WebView would.
 */
public abstract class YoutubeMiniPlayerTest {

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w800dp-h400dp-land-mdpi")
	public static class Car {
		@Test
		public void miniPlayer() throws Exception {
			YoutubeMiniPlayerTest.run("car");
		}
	}

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w393dp-h852dp-port-xxhdpi")
	public static class Phone {
		@Test
		public void miniPlayer() throws Exception {
			YoutubeMiniPlayerTest.run("phone");
		}
	}

	static void run(String size) throws Exception {
		MainActivity a = Screens.open().get();
		AddonManager.get().getOrInstallAddon("me.aap.fermata.addon.web.yt.YoutubeAddon");
		Screens.settle();
		MainActivityDelegate d = MainActivityDelegate.get(a);
		d.showFragment(me.aap.fermata.R.id.youtube_fragment);
		Screens.waitReal(1);
		YoutubeFragment f = (YoutubeFragment) d.getActiveFragment();
		ViewGroup root = (ViewGroup) f.requireView();
		YoutubeWebView player = f.getWebView();
		assertNotNull(player);

		TextView minimize = text(root, "▾ Minimizar");
		assertNotNull("the bar", minimize);
		player.urlChanged("https://m.youtube.com/");
		assertTrue("no bar on the home page", !((View) minimize.getParent()).isShown());

		player.urlChanged("https://m.youtube.com/watch?v=abc");
		Screens.settle();
		assertTrue("the bar on a video's page", ((View) minimize.getParent()).isShown());
		Screens.save(a.getWindow().getDecorView(), size + "-yt-watch-bar");

		text(root, "Inscrições").performClick();
		Screens.settle();
		assertTrue("the player in a corner", isMini(player, root));
		WebView browse = browse(root, player);
		assertNotNull("the browsing", browse);
		assertTrue(browse.isShown());
		assertEquals("https://m.youtube.com/feed/subscriptions", shadowOf(browse).getLastLoadedUrl());
		assertTrue("no bar while browsing", !((View) minimize.getParent()).isShown());
		Screens.save(a.getWindow().getDecorView(), size + "-yt-mini");

		d.onBackPressed();
		Screens.settle();
		assertTrue("Back brings the player back", isFull(player));
		assertTrue(!browse.isShown());

		minimize.performClick();
		Screens.settle();
		assertTrue(isMini(player, root));
		View cover = cover(root, player, browse);
		assertNotNull("the cover of the corner", cover);
		cover.performClick();
		Screens.settle();
		assertTrue("a tap on the corner brings the player back", isFull(player));

		// Shorts, as in the app: the video's page leaves for the Shorts player, full size
		text(root, "Shorts").performClick();
		Screens.settle();
		assertEquals("https://m.youtube.com/shorts", shadowOf(player).getLastLoadedUrl());
		assertTrue(isFull(player));
	}

	// What the layout is asked: Robolectric's WebView does not measure again after a change
	private static boolean isMini(View player, View root) {
		ViewGroup.LayoutParams lp = player.getLayoutParams();
		return (lp.width > 0) && (lp.width < root.getWidth() / 2) && (lp.height == lp.width * 9 / 16);
	}

	private static boolean isFull(View player) {
		ViewGroup.LayoutParams lp = player.getLayoutParams();
		return (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) && (lp.height == ViewGroup.LayoutParams.MATCH_PARENT);
	}

	private static TextView text(View v, String t) {
		List<View> l = new ArrayList<>();
		collect(v, x -> (x instanceof TextView tv) && t.contentEquals(tv.getText()), l);
		return l.isEmpty() ? null : (TextView) l.get(0);
	}

	private static WebView browse(ViewGroup root, WebView player) {
		for (int i = 0; i < root.getChildCount(); i++) {
			View c = root.getChildAt(i);
			if ((c instanceof WebView w) && (w != player)) return w;
		}
		return null;
	}

	private static View cover(ViewGroup root, View player, View browse) {
		for (int i = 0; i < root.getChildCount(); i++) {
			View c = root.getChildAt(i);
			if ((c != player) && (c != browse) && c.isShown() && c.isClickable()) return c;
		}
		return null;
	}

	private static void collect(View v, java.util.function.Predicate<View> p, List<View> out) {
		if (p.test(v)) out.add(v);
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), p, out);
		}
	}
}
