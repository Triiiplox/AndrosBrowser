package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * The mini player of the YouTube app, for the site: on a video's page a bar goes home, to the
 * subscriptions or to the library while the video keeps playing in a corner. The site has no such
 * thing (leaving the video's page unloads it), so the video's page stays where it is, made small,
 * and the browsing is a second page over it, signed in with the same cookies. A video tapped
 * there plays in the player; a tap on the corner brings the player back. Shorts, as in the app,
 * leave the video for the Shorts player.
 */
final class YoutubeMiniPlayer {
	static final String HOME = "https://m.youtube.com/";
	private static final String SHORTS = "https://m.youtube.com/shorts";
	private static final String SUBSCRIPTIONS = "https://m.youtube.com/feed/subscriptions";
	private static final String YOU = "https://m.youtube.com/feed/you";
	private final FrameLayout root;
	private final YoutubeWebView player;
	private final LinearLayout bar;
	private final FrameLayout cover;
	@Nullable
	private WebView browse;
	// The page of the player, as told by its changes: getUrl() lags behind the site's own moves
	@Nullable
	private String url;
	private boolean mini;

	YoutubeMiniPlayer(FrameLayout root, YoutubeWebView player) {
		this.root = root;
		this.player = player;
		Context ctx = root.getContext();
		bar = createBar(ctx);
		root.addView(bar, new FrameLayout.LayoutParams(MATCH_PARENT, toIntPx(ctx, 56), Gravity.BOTTOM));
		cover = createCover(ctx);
		cover.setVisibility(View.GONE);
		root.addView(cover);
		url = player.getUrl();
		player.setUrlListener(u -> {
			url = u;
			update();
		});
		update();
	}

	boolean isMini() {
		return mini;
	}

	/** Back: the browsing's history, then the player again. */
	boolean onBackPressed() {
		if (!mini) return false;
		WebView b = browse;
		if ((b != null) && b.canGoBack()) b.goBack();
		else expand();
		return true;
	}

	/** The YouTube button of the nav bar, tapped again: the home of the browsing. */
	boolean home() {
		if (!mini || (browse == null)) return false;
		browse.loadUrl(HOME);
		return true;
	}

	void destroy() {
		player.setUrlListener(null);
		if (browse != null) browse.destroy();
		browse = null;
	}

	// --- States ---

	// Minimize keeps what was being browsed; the other buttons go to their page
	private void minimize(@Nullable String url) {
		FermataChromeClient chrome = player.getWebChromeClient();
		if ((chrome != null) && chrome.isFullScreen()) chrome.exitFullScreen();
		WebView b = browse();
		if (url != null) b.loadUrl(url);
		else if (b.getUrl() == null) b.loadUrl(url = HOME);
		b.setVisibility(View.VISIBLE);
		// The page of the video, made small: its player is at its top, the corner shows that
		Context ctx = root.getContext();
		int w = Math.min(Math.round(root.getWidth() * 0.42f), toIntPx(ctx, 320));
		int h = w * 9 / 16;
		int m = toIntPx(ctx, 12);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.BOTTOM | Gravity.END);
		lp.setMargins(m, m, m, m);
		player.setLayoutParams(lp);
		player.evaluateJavascript("window.scrollTo(0, 0);", null);
		cover.setLayoutParams(new FrameLayout.LayoutParams(lp));
		cover.setVisibility(View.VISIBLE);
		cover.bringToFront();
		mini = true;
		Log.i("YouTube mini player: browsing ", url);
		update();
	}

	private void expand() {
		player.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		cover.setVisibility(View.GONE);
		if (browse != null) browse.setVisibility(View.GONE);
		mini = false;
		update();
	}

	// The corner's close: the video stops, and what was being browsed stays in the player
	private void close() {
		String url = (browse != null) ? browse.getUrl() : null;
		MainActivityDelegate.get(root.getContext()).getMediaSessionCallback().onStop();
		expand();
		if (url != null) player.loadUrl(url);
	}

	private void play(String url) {
		Log.i("YouTube mini player: playing ", url);
		expand();
		player.loadUrl(url);
	}

	// The bar shows on a video's page only: the other pages of the site have their own tab bar
	void update() {
		FermataChromeClient chrome = player.getWebChromeClient();
		boolean full = (chrome != null) && chrome.isFullScreen();
		bar.setVisibility(!mini && !full && isVideo(url) ? View.VISIBLE : View.GONE);
		bar.bringToFront();
	}

	private static boolean isVideo(@Nullable String url) {
		if (url == null) return false;
		String path = Uri.parse(url).getPath();
		return (path != null) && (path.startsWith("/watch") || path.startsWith("/shorts"));
	}

	// --- Views ---

	@SuppressLint("SetJavaScriptEnabled")
	private WebView browse() {
		if (browse != null) return browse;
		WebView b = new WebView(root.getContext());
		WebSettings s = b.getSettings();
		WebSettings ps = player.getSettings();
		s.setJavaScriptEnabled(true);
		s.setDomStorageEnabled(true);
		s.setDatabaseEnabled(true);
		s.setUserAgentString(ps.getUserAgentString());
		// The previews of the feed play muted on their own; nothing else plays here
		s.setMediaPlaybackRequiresUserGesture(true);
		b.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
				String u = req.getUrl().toString();
				if (!isVideo(u)) return false;
				play(u);
				return true;
			}

			// The site moves between its pages without loading them: a video is seen here
			@Override
			public void doUpdateVisitedHistory(WebView v, String u, boolean reload) {
				if (!isVideo(u)) return;
				v.stopLoading();
				v.post(() -> {
					if (v.canGoBack()) v.goBack();
				});
				play(u);
			}
		});
		b.setVisibility(View.GONE);
		root.addView(b, 0, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		return browse = b;
	}

	private LinearLayout createBar(Context ctx) {
		LinearLayout l = new LinearLayout(ctx);
		l.setOrientation(LinearLayout.HORIZONTAL);
		l.setGravity(Gravity.CENTER_VERTICAL);
		l.setBackgroundColor(0xF0121212);
		l.setElevation(toIntPx(ctx, 8));
		l.addView(button(ctx, R.string.yt_minimize, () -> minimize(null)), weight());
		l.addView(button(ctx, R.string.yt_home, () -> minimize(HOME)), weight());
		l.addView(button(ctx, R.string.yt_shorts, () -> player.loadUrl(SHORTS)), weight());
		l.addView(button(ctx, R.string.yt_subscriptions, () -> minimize(SUBSCRIPTIONS)), weight());
		l.addView(button(ctx, R.string.yt_you, () -> minimize(YOU)), weight());
		return l;
	}

	private static LinearLayout.LayoutParams weight() {
		return new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
	}

	private static TextView button(Context ctx, int text, Runnable onClick) {
		TextView t = new TextView(ctx);
		t.setText(text);
		t.setTextColor(Color.WHITE);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		t.setGravity(Gravity.CENTER);
		t.setMaxLines(1);
		t.setFocusable(true);
		TypedValue v = new TypedValue();
		ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, v, true);
		t.setBackgroundResource(v.resourceId);
		t.setOnClickListener(x -> onClick.run());
		return t;
	}

	// Over the corner: a tap brings the player back, the cross stops it
	private FrameLayout createCover(Context ctx) {
		FrameLayout c = new FrameLayout(ctx);
		GradientDrawable frame = new GradientDrawable();
		frame.setStroke(toIntPx(ctx, 2), 0xCCFFFFFF);
		frame.setCornerRadius(toIntPx(ctx, 8));
		c.setForeground(frame);
		c.setElevation(toIntPx(ctx, 10));
		c.setClickable(true);
		c.setContentDescription(ctx.getString(R.string.yt_expand));
		c.setOnClickListener(x -> expand());
		ImageButton close = new ImageButton(ctx);
		close.setImageResource(me.aap.utils.R.drawable.close);
		close.setColorFilter(Color.WHITE);
		GradientDrawable round = new GradientDrawable();
		round.setShape(GradientDrawable.OVAL);
		round.setColor(0xB0000000);
		close.setBackground(round);
		close.setContentDescription(ctx.getString(R.string.yt_close_mini));
		close.setOnClickListener(x -> close());
		int s = toIntPx(ctx, 32);
		c.addView(close, new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END));
		return c;
	}
}
