package me.aap.fermata.screens;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class MainScreensTest {

	@Test
	@Config(qualifiers = "pt-rBR-w800dp-h400dp-land-mdpi")
	public void car() throws Exception {
		shoot("car");
	}

	@Test
	@Config(qualifiers = "pt-rBR-w393dp-h852dp-port-xxhdpi")
	public void phone() throws Exception {
		shoot("phone");
	}

	private static void shoot(String size) throws Exception {
		ActivityController<MainActivity> c = Screens.open();
		MainActivity a = c.get();
		Screens.save(a.getWindow().getDecorView(), size + "-start");
		MainActivityDelegate d = MainActivityDelegate.get(a);
		int[] ids = {R.id.folders_fragment, R.id.settings_fragment};
		String[] names = {"folders", "settings"};
		for (int i = 0; i < ids.length; i++) {
			d.showFragment(ids[i]);
			Screens.save(a.getWindow().getDecorView(), size + "-" + names[i]);
		}
	}
}
