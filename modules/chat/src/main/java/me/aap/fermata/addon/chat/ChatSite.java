package me.aap.fermata.addon.chat;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.Locale;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * ChatGPT with the user's own account: the chatgpt.com site, signed in once and kept signed in by
 * its cookies. The account's plan pays, not tokens, and the site brings its own voice mode, which
 * gets the microphone here. The site may first check the browser (Cloudflare); if it does not let
 * this one through, the ChatGPT app with the same account is offered instead.
 */
final class ChatSite extends FrameLayout {
	static final String URL = "https://chatgpt.com/";
	private static final long CHECK_DELAY = 25_000;
	final WebView web;
	private final Runnable check = this::checkBrowserCheck;
	private boolean checkShown;

	@SuppressLint("SetJavaScriptEnabled")
	ChatSite(Context ctx) {
		super(ctx);
		web = new WebView(ctx);
		WebSettings s = web.getSettings();
		s.setJavaScriptEnabled(true);
		s.setDomStorageEnabled(true);
		s.setDatabaseEnabled(true);
		s.setMediaPlaybackRequiresUserGesture(false);
		s.setSupportMultipleWindows(false);
		s.setJavaScriptCanOpenWindowsAutomatically(false);
		// Without the WebView marks Google lets its sign-in through: the site offers it first
		s.setUserAgentString(s.getUserAgentString().replace("; wv", "")
				.replaceAll("Version/\\S+ ", ""));
		CookieManager cm = CookieManager.getInstance();
		cm.setAcceptCookie(true);
		cm.setAcceptThirdPartyCookies(web, true);
		web.setWebViewClient(new Client());
		web.setWebChromeClient(new Chrome());
		addView(web, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		web.loadUrl(URL);
		postDelayed(check, CHECK_DELAY);
	}

	boolean back() {
		if (!web.canGoBack()) return false;
		web.goBack();
		return true;
	}

	void pause() {
		web.onPause();
		CookieManager.getInstance().flush();
	}

	void resume() {
		web.onResume();
	}

	void destroy() {
		removeCallbacks(check);
		CookieManager.getInstance().flush();
		web.destroy();
	}

	// Still on Cloudflare's page ("Just a moment…") after a while: say it, and offer the app
	private void checkBrowserCheck() {
		if (checkShown || !isAttachedToWindow()) return;
		String t = web.getTitle();
		if (t == null) return;
		String title = t.toLowerCase(Locale.ROOT);
		if (!title.contains("moment") && !title.contains("momento")) return;
		checkShown = true;
		Log.w("ChatGPT site: still on the browser check: ", t);
		MainActivityDelegate.get(getContext()).createDialogBuilder()
				.setMessage(R.string.chat_site_check)
				.setNegativeButton(android.R.string.ok, null)
				.setPositiveButton(R.string.open_chatgpt_app,
						(d, w) -> ChatAddon.openChatGptApp(getContext())).show();
	}

	private final class Client extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
			Uri u = req.getUrl();
			String scheme = u.getScheme();
			if ("http".equals(scheme) || "https".equals(scheme)) return false;
			// intent:, mailto: and the like go to their apps
			try {
				Intent i = "intent".equals(scheme) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
						: new Intent(Intent.ACTION_VIEW, u);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(i);
			} catch (ActivityNotFoundException | java.net.URISyntaxException ex) {
				Log.w("ChatGPT site: no app for ", u);
			}
			return true;
		}

		@Override
		public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
			if (!req.isForMainFrame()) return;
			Log.w("ChatGPT site: ", err.getDescription(), " ", req.getUrl());
			UiUtils.showAlert(getContext(),
					getContext().getString(R.string.chat_site_failed, String.valueOf(err.getDescription())));
		}

		@Override
		public void onPageFinished(WebView v, String url) {
			CookieManager.getInstance().flush();
		}
	}

	private final class Chrome extends WebChromeClient {
		// The site's voice mode and dictation: the microphone, once Android allows it
		@Override
		public void onPermissionRequest(@NonNull PermissionRequest req) {
			boolean audio = false;
			for (String r : req.getResources()) {
				if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
			}
			if (!audio) {
				req.deny();
				return;
			}
			String[] grant = {PermissionRequest.RESOURCE_AUDIO_CAPTURE};
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (a.isCarActivityNotMirror()) {
				req.grant(grant);
				return;
			}
			a.getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO).main()
					.onCompletion((r, err) -> {
						if ((err == null) && (r != null) && (r.length > 0) && (r[0] == PERMISSION_GRANTED)) {
							req.grant(grant);
						} else {
							req.deny();
						}
					});
		}
	}
}
