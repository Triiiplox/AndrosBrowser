package me.aap.fermata.screens;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.IBinder;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import me.aap.fermata.media.service.FermataMediaService;
import me.aap.fermata.ui.activity.MainActivity;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

/**
 * Opens the real main activity in Robolectric, with the media service bound, and saves what the
 * screen shows as a PNG.
 */
public final class Screens {
	private Screens() {
	}

	public static ActivityController<MainActivity> open() {
		Application app = ApplicationProvider.getApplicationContext();
		Intent i = new Intent(app, FermataMediaService.class);
		i.setAction(FermataMediaService.ACTION_MEDIA_SERVICE);
		FermataMediaService svc = Robolectric.buildService(FermataMediaService.class, i).create().get();
		IBinder b = svc.onBind(i);
		shadowOf(app).setComponentNameAndServiceForBindService(
				new ComponentName(app, FermataMediaService.class), b);
		ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
		settle();
		return c;
	}

	/** Lets the posted work and the animations run for a while. */
	public static void settle() {
		for (int n = 0; n < 20; n++) {
			ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS);
		}
	}

	/** Real time passes too: the network and the background threads work while the UI idles. */
	public static void waitReal(int seconds) throws InterruptedException {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (System.currentTimeMillis() < end) {
			ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
			Thread.sleep(50);
		}
	}

	public static void waitFor(BooleanSupplier done, int seconds) throws InterruptedException {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (!done.getAsBoolean()) {
			if (System.currentTimeMillis() > end) throw new AssertionError("Timed out");
			ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
			Thread.sleep(50);
		}
	}

	public static File save(View root, String name) throws IOException {
		settle();
		int w = root.getWidth();
		int h = root.getHeight();
		Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
		root.draw(new Canvas(bmp));
		File dir = new File(System.getProperty("screenshots.dir", "build/screenshots"));
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
		File f = new File(dir, name + ".png");
		try (FileOutputStream out = new FileOutputStream(f)) {
			bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
		}
		return f;
	}
}
