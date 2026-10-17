package me.aap.fermata.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.webkit.WebView;

import java.util.Arrays;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore.Pref;

/**
 * Diagnostics written to Fermata.log: the log the user shares is the only view of what
 * happened in the car.
 */
public final class Diag {

	private Diag() {
	}

	// Once per process start: what is running, on what
	public static void logStart(Context ctx) {
		String webView = "?";
		try {
			PackageInfo p = WebView.getCurrentWebViewPackage();
			if (p != null) webView = p.packageName + " " + p.versionName;
		} catch (Throwable ignore) {
		}
		Log.i("===== Fermata ", BuildConfig.VERSION_NAME, " (", BuildConfig.APPLICATION_ID, ", commit ",
				BuildConfig.GIT_COMMIT, ") | Android ", Build.VERSION.RELEASE, " (API ", Build.VERSION.SDK_INT,
				") | ", Build.MANUFACTURER, " ", Build.MODEL, " | WebView ", webView);
	}

	// When the car screen opens: its size and the settings that shape what is shown there
	public static void logCar(MainActivityDelegate a) {
		try {
			Context ctx = a.getContext();
			DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
			Configuration cfg = ctx.getResources().getConfiguration();
			MainActivityPrefs p = a.getPrefs();
			Log.i("Android Auto screen: ", dm.widthPixels, "x", dm.heightPixels, " px, ",
					cfg.screenWidthDp, "x", cfg.screenHeightDp, " dp, density ", dm.density,
					" | theme ", p.getThemePref(true), " | nav bar position ", p.getNavBarPosPref(a),
					" | nav items B/L/R ", items(p, "NAV_BAR_ITEMS_B"), " ", items(p, "NAV_BAR_ITEMS_L"), " ",
					items(p, "NAV_BAR_ITEMS_R"), " | start screen ", p.getShowAddonOnStartPref());
		} catch (Throwable err) {
			Log.e(err, "Failed to log the car configuration");
		}
	}

	private static String items(MainActivityPrefs p, String name) {
		String[] v = p.getStringArrayPref(Pref.sa(name, (String[]) null));
		return (v == null) ? "default" : Arrays.toString(v);
	}
}
