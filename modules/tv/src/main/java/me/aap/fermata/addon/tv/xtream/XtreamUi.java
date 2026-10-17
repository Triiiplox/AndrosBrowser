package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.Series;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Small helpers of the IPTV screens: pictures, durations, the line under a title. */
final class XtreamUi {
	private XtreamUi() {
	}

	/**
	 * Loads a picture into a view. The view remembers the URL it waits for, so a recycled card
	 * never shows the picture of the one it was before. Without a picture the fallback (the title)
	 * shows.
	 *
	 * @param big a backdrop: decoded at its size, not as an icon
	 */
	static void image(ImageView v, @Nullable String url, @Nullable View fallback, boolean big) {
		v.setTag(R.id.xtream_image_url, url);
		v.setImageDrawable(null);
		if (fallback != null) fallback.setVisibility(View.VISIBLE);
		if (url == null) return;
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		a.getLib().getBitmap(url, true, !big).main().onCompletion((bm, err) -> {
			if ((bm == null) || !url.equals(v.getTag(R.id.xtream_image_url))) return;
			v.setImageBitmap(bm);
			if (fallback != null) fallback.setVisibility(View.INVISIBLE);
		});
	}

	/** "1h 52min", "45 min", or null. */
	@Nullable
	static String duration(Context ctx, long seconds) {
		if (seconds <= 0) return null;
		long m = Math.max(1, Math.round(seconds / 60.0));
		return (m < 60) ? ctx.getString(R.string.xtream_minutes, m) :
				ctx.getString(R.string.xtream_hours_minutes, m / 60, m % 60);
	}

	/** "2024 · ★ 8.5 · Ação" for a card or the hero. */
	static String meta(Context ctx, Entry e, @Nullable String categoryName) {
		StringBuilder sb = new StringBuilder();
		if ((e instanceof Series s) && (s.year != null)) sb.append(s.year);
		if (e.rating > 0) sep(sb).append("★ ").append(String.format(java.util.Locale.ROOT, "%.1f", e.rating));
		if ((e instanceof Series s) && (s.genre != null)) sep(sb).append(s.genre);
		else if (categoryName != null) sep(sb).append(categoryName);
		return sb.toString();
	}

	static StringBuilder sep(StringBuilder sb) {
		if (sb.length() > 0) sb.append("  ·  ");
		return sb;
	}

	/** What is left of a record, e.g. "Faltam 23 min", or its subtitle. */
	@Nullable
	static String left(Context ctx, UserLibrary.Record r) {
		if ((r.duration > 0) && (r.position > 0)) {
			String d = duration(ctx, (r.duration - r.position) / 1000);
			if (d != null) return ctx.getString(R.string.xtream_left, d);
		}
		return r.subtitle;
	}

	static void progress(android.widget.ProgressBar p, @Nullable UserLibrary.Record r) {
		if ((r == null) || (r.duration <= 0) || (r.position <= 0)) {
			p.setVisibility(View.GONE);
		} else {
			p.setVisibility(View.VISIBLE);
			p.setProgress((int) (1000 * r.progress()));
		}
	}

	static void text(TextView v, @Nullable CharSequence s) {
		v.setText(s);
		v.setVisibility(((s == null) || (s.length() == 0)) ? View.GONE : View.VISIBLE);
	}

	static boolean isVideo(Kind k) {
		return (k == Kind.MOVIE) || (k == Kind.EPISODE);
	}
}
