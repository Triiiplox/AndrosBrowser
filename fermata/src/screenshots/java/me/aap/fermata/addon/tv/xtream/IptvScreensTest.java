package me.aap.fermata.addon.tv.xtream;

import android.view.View;
import android.view.ViewGroup;

import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.XtreamAccount;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.screens.Screens;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** One class per size: the app keeps static state that one JVM cannot open twice. */
public abstract class IptvScreensTest {

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w800dp-h400dp-land-mdpi")
	public static class Car {
		@Test
		public void shoot() throws Exception {
			IptvScreensTest.shoot("car");
		}
	}

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w393dp-h852dp-port-xxhdpi")
	public static class Phone {
		@Test
		public void shoot() throws Exception {
			IptvScreensTest.shoot("phone");
		}
	}

	static void shoot(String size) throws Exception {
		try (FakeXtreamServer panel = new FakeXtreamServer()) {
			ActivityController<MainActivity> c = Screens.open();
			MainActivity a = c.get();
			XtreamRepo repo = XtreamRepo.get(a);
			repo.setActiveAccount(repo.addAccount(
					new XtreamAccount(panel.url, "andro", "senha", "ts"), "Meu IPTV"));

			MainActivityDelegate d = MainActivityDelegate.get(a);
			d.showFragment(me.aap.fermata.R.id.xtream_fragment);
			XtreamFragment f = (XtreamFragment) d.getActiveFragment();
			View root = a.getWindow().getDecorView();
			Screens.waitFor(() -> field(f, "snapshot") != null, 20);
			Screens.waitReal(3);
			Screens.save(root, size + "-iptv-home");

			ViewGroup tabs = (ViewGroup) field(f, "tabs");
			String[] names = {"home", "live", "movies", "series"};
			for (int i = 1; i < tabs.getChildCount(); i++) {
				tabs.getChildAt(i).performClick();
				Screens.waitReal(3);
				Screens.save(root, size + "-iptv-" + names[i]);
			}

			Snapshot s = (Snapshot) field(f, "snapshot");
			Method open = XtreamFragment.class.getDeclaredMethod("openDetails", Entry.class);
			open.setAccessible(true);
			List<Entry> movies = s.entries(Kind.MOVIE);
			open.invoke(f, movies.get(0));
			Screens.waitReal(3);
			Screens.save(root, size + "-iptv-movie");
			f.closeDetails();
			List<Entry> series = s.entries(Kind.SERIES);
			open.invoke(f, series.get(0));
			Screens.waitReal(3);
			Screens.save(root, size + "-iptv-series");
			f.closeDetails();

			Method search = XtreamFragment.class.getDeclaredMethod("openSearch");
			search.setAccessible(true);
			search.invoke(f);
			Screens.waitReal(1);
			Screens.save(root, size + "-iptv-search");
		}
	}

	private static Object field(Object o, String name) {
		try {
			Field fl = o.getClass().getDeclaredField(name);
			fl.setAccessible(true);
			return fl.get(o);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
