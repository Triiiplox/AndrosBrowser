package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONTokener;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	private YoutubeJsInterface js;

	public YoutubeWebView(Context context) {
		super(context);
	}

	public YoutubeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		return js = new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		super.onPreferenceChanged(store, prefs);

		if (getAddon().autoHighestQualityChanged(prefs)) {
			if (getAddon().autoHighestQuality()) setHighestVideoQuality();
			else clearHighestVideoQuality();
		}

		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) injectSponsorBlock();
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		super.loadUrl(url);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if (cb.getEngine() instanceof YoutubeMediaEngine) cb.onStop();
		super.goBack();
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		// The page is gone with its activity (e.g. Fermata Auto exited from Android Auto), and nothing
		// can continue its playback. Switching to another section only detaches it until it's shown again.
		if ((e == ACTIVITY_DESTROY) && (js != null)) js.pageRemoved();
		super.onActivityEvent(a, e);
	}

	@Override
	protected void urlChanged(String url) {
		// Leaving the video within the page (e.g. YouTube's own back button) leaves it like goBack(),
		// even though YouTube keeps playing it as a muted preview
		String path = Uri.parse(url).getPath();
		if ((path != null) && (path.startsWith("/watch") || path.startsWith("/shorts"))) {
			unmute();
			YoutubeAddon addon = getAddon();
			if ((addon != null) && addon.autoplay()) {
				addon.setLastVideo(url);
				autoplay();
			}
			return;
		}
		YoutubeAddon addon = getAddon();
		if (addon != null) addon.setLastVideo(null);
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if (cb.getEngine() instanceof YoutubeMediaEngine) cb.onStop();
	}

	@Override
	protected void pageLoaded(String uri) {
		attachListeners();
		injectSponsorBlock();
		injectAdSkip();
		injectContinueWatching();
		injectTapFullScreen();
		addFocusHighlight();
		CookieManager.getInstance().flush();
	}

	protected void submitForm() {
		if (!me.aap.fermata.BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var e = new KeyboardEvent('keydown',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);\n" +
				"e = new KeyboardEvent('keyup',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);");
	}

	private void attachListeners() {
		String debug = BuildConfig.D ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		loadUrl("javascript:\n" +
				// Muted autoplay previews in the feeds reuse the player, they are not user playback
				"function isPreview(v) {\n" +
				"  return v.muted && !/^\\/(watch|shorts)/.test(location.pathname);\n" +
				"}\n" +
				"function videoEvent(v, e) {\n" +
				"  if (isPreview(v)) {\n" +
				"    if (!v.fermataPlaying) return;\n" +
				"    e = " + JS_VIDEO_PAUSED + ";\n" +
				"  }\n" +
				"  v.fermataPlaying = (e === " + JS_VIDEO_PLAYING + ");\n" +
				"  " + JS_EVENT + "(e, (e === " + JS_VIDEO_ENDED + ") ? null : v.currentSrc);\n" +
				"}\n" +
				"function attachVideoListeners(v) {\n" +
				"  if (v.getAttribute('FermataAttached') === 'true') return;\n" +
				"  v.setAttribute('FermataAttached', 'true');\n" +
				"  v.setAttribute('style', 'object-fit:" + scale + "');\n" + debug +
				"  if ((v.currentTime > 0) && !v.paused && !v.ended) videoEvent(v, " + JS_VIDEO_PLAYING + ");\n" +
				"  v.addEventListener('playing', function(e) {videoEvent(v, " + JS_VIDEO_PLAYING + ");});\n" +
				"  v.addEventListener('pause', function(e) {videoEvent(v, " + JS_VIDEO_PAUSED + ");});\n" +
				"  v.addEventListener('ended', function(e) {videoEvent(v, " + JS_VIDEO_ENDED + ");});\n" +
				"  v.addEventListener('volumechange', function(e) {\n" +
				"    if (!v.paused && !v.fermataPlaying && !isPreview(v)) videoEvent(v, " + JS_VIDEO_PLAYING + ");\n" +
				"  });\n" +
				"}\n" +
				"function findVideo() {\n" +
				"  var video = document.querySelectorAll('video');" +
				"  video.forEach(attachVideoListeners);\n" +
				"   setTimeout(findVideo, 500);\n" +
				"}\n" +
				// The page can report loaded again without a reload: keep a single loop
				"if (!window.__fermataFindVideo) { window.__fermataFindVideo = true; findVideo(); }");
	}

	// A video page opened in the car may stay paused: the page can be considered hidden, and YouTube
	// does not start the playback then. Starts it once per video, and stops trying as soon as it plays,
	// so a pause by the user is left alone.
	private void autoplay() {
		evaluateJavascript("""
				(function() {
				  var id = new URLSearchParams(location.search).get('v') || location.pathname;
				  if (window.__fermataAutoplay === id) return;
				  window.__fermataAutoplay = id;
				  window.__fermataPausedBy = null;
				  if (!window.__fermataTouchWatch) {
				    window.__fermataTouchWatch = true;
				    document.addEventListener('touchstart', function() { window.__fermataTouch = Date.now(); },
				      {capture: true, passive: true});
				  }
				  var tries = 0, start = Date.now();
				  (function check() {
				    if ((window.__fermataAutoplay !== id) || (window.__fermataPausedBy === id)) return;
				    var p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				    var v = p && p.querySelector('video');
				    if (v && !v.__fermataPauseWatch) {
				      v.__fermataPauseWatch = true;
				      v.addEventListener('pause', function() {
				        if (Date.now() - (window.__fermataTouch || 0) < 2000) window.__fermataPausedBy = window.__fermataAutoplay;
				      });
				    }
				    if (v && !v.paused) {
				      console.log('Fermata autoplay: ' + id + ' playing after ' + (Date.now() - start) + ' ms');
				      return;
				    }
				    // A slow network may take longer than a few seconds to load the video: keep trying, and
				    // ask the player to load it now and then even when nothing is loaded yet
				    if (v && !v.ended && ((v.readyState > 0) || (tries % 6 === 5))) {
				      if (typeof p.playVideo === 'function') p.playVideo(); else v.play();
				    }
				    if (++tries < 60) setTimeout(check, 500);
				    else console.log('Fermata autoplay: ' + id + ' not started after 30 s');
				  })();
				})();
				""", null);
	}

	// YouTube pauses after a while without touches and asks whether to continue: nobody answers in
	// the car, so the answer is yes
	private void injectContinueWatching() {
		evaluateJavascript("""
				(function() {
				  if (window.__fermataStillWatching) return;
				  window.__fermataStillWatching = true;
				  var ask = /continue watching|still watching|continuar assistindo|continuar a assistir|ainda est/i;
				  var yes = /^(yes|sim|continuar|continue)/i;
				  setInterval(function() {
				    var dialogs = document.querySelectorAll('[role="dialog"], ytm-dialog, yt-confirm-dialog-renderer, ' +
				      'tp-yt-paper-dialog, ytm-confirm-dialog-renderer');
				    for (var i = 0; i < dialogs.length; i++) {
				      var d = dialogs[i];
				      if (!ask.test(d.textContent || '')) continue;
				      var b = Array.from(d.querySelectorAll('button, [role="button"]'))
				        .find(function(x) { return yes.test((x.textContent || '').trim()); });
				      if (!b) continue;
				      console.log('Fermata: answering "continue watching"');
				      b.click();
				      var p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				      setTimeout(function() { if (p && (typeof p.playVideo === 'function')) p.playVideo(); }, 500);
				      return;
				    }
				  }, 3000);
				})();
				""", null);
	}

	// YouTube starts a video it plays by itself (no tap on the page: autoplay, the next video, a feed
	// preview opened, Android Auto) muted, behind "Tap to unmute", and may mute it again later (after
	// an ad, on a quality switch). Watches the player for as long as the page lives and unmutes it
	// whenever it gets muted. Only a mute right after a touch, of a video already heard, is the
	// user's: it is left alone until the next video.
	private void unmute() {
		evaluateJavascript("""
				(function() {
				  window.__fermataUnmute = new URLSearchParams(location.search).get('v') || location.pathname;
				  if (window.__fermataUnmuteFix) { window.__fermataUnmuteFix(); return; }
				  var touch = 0, heard = null, userMuted = null;
				  ['touchstart', 'mousedown', 'keydown'].forEach(function(n) {
				    document.addEventListener(n, function() { touch = Date.now(); }, {capture: true, passive: true});
				  });
				  var label = /^(ativar o som|ativar som|toque para ativar|tap to unmute|unmute)/i;
				  function fix() {
				    var id = window.__fermataUnmute;
				    if (!/^\\/(watch|shorts)/.test(location.pathname) || (userMuted === id)) return;
				    var p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				    var v = p && p.querySelector('video');
				    if (!v) return;
				    var muted = v.muted || (v.volume === 0) || ((typeof p.isMuted === 'function') && p.isMuted());
				    if (!muted) {
				      if (!v.paused) heard = id;
				      return;
				    }
				    if ((heard === id) && (Date.now() - touch < 1500)) {
				      console.log('Fermata unmute: muted by the user: ' + id);
				      userMuted = id;
				      return;
				    }
				    console.log('Fermata unmute: ' + id);
				    if (typeof p.unMute === 'function') p.unMute();
				    if ((typeof p.getVolume === 'function') && (p.getVolume() === 0) &&
				      (typeof p.setVolume === 'function')) p.setVolume(100);
				    v.muted = false;
				    if (v.volume === 0) v.volume = 1;
				    if (!v.muted) return;
				    var b = Array.from(document.querySelectorAll('.ytp-unmute, .ytmUnmuteButton, .ytp-unmute-button, ' +
				      'button, [role="button"]')).find(function(x) {
				      return x.classList.contains('ytp-unmute') || x.classList.contains('ytmUnmuteButton') ||
				        x.classList.contains('ytp-unmute-button') ||
				        label.test((x.getAttribute('aria-label') || x.textContent || '').trim());
				    });
				    if (b) b.click();
				  }
				  window.__fermataUnmuteFix = fix;
				  // Media events do not bubble, a capturing listener on the document still gets them
				  ['volumechange', 'playing', 'loadeddata'].forEach(function(n) {
				    document.addEventListener(n, fix, true);
				  });
				  setInterval(fix, 1000);
				  fix();
				})();
				""", null);
	}

	// Watches the player for as long as the page lives: an ad can start at any time, not only when
	// the playback starts. Clicks the skip button when there is one, else jumps to the end of the ad.
	private void injectAdSkip() {
		if (!BuildConfig.AUTO || !getAddon().skipAd()) return;
		evaluateJavascript("""
				(function() {
				  if (window.__fermataAdSkip) return;
				  window.__fermataAdSkip = true;
				  var pending = false;
				  function skip() {
				    pending = false;
				    var p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				    if (!p || !p.classList.contains('ad-showing')) return;
				    var b = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
				      '.ytp-skip-ad-button, .ytp-ad-skip-button-slot button, button[class*="skip-ad"]');
				    if (b) { console.log('Fermata ad skip: skip button'); b.click(); return; }
				    var v = p.querySelector('video');
				    if (v && isFinite(v.duration) && (v.duration > 0) && (v.currentTime < v.duration)) {
				      console.log('Fermata ad skip: seek to the end of a ' + v.duration + 's ad');
				      v.currentTime = v.duration;
				    }
				  }
				  new MutationObserver(function() {
				    if (pending) return;
				    pending = true;
				    requestAnimationFrame(skip);
				  }).observe(document.documentElement,
				    {subtree: true, childList: true, attributes: true, attributeFilter: ['class']});
				  skip();
				})();
				""", null);
	}

	// In the car a tap on a playing video opens it in full screen, the small page player is hard to
	// watch and to hit. The tap is a user gesture, which the browser may require for full screen.
	// Taps on the player's buttons and on a paused video are left to YouTube.
	private void injectTapFullScreen() {
		if (!MainActivityDelegate.get(getContext()).isCarActivity()) return;
		evaluateJavascript("""
				(function() {
				  if (window.__fermataTapFs) return;
				  window.__fermataTapFs = true;
				  var x = 0, y = 0, t = 0;
				  ['fullscreenerror', 'webkitfullscreenerror'].forEach(function(n) {
				    document.addEventListener(n, function() { console.log('Fermata fullscreen refused: ' + n); });
				  });
				  document.addEventListener('touchstart', function(e) {
				    var p = e.touches[0];
				    x = p.clientX; y = p.clientY; t = Date.now();
				  }, {capture: true, passive: true});
				  document.addEventListener('touchend', function(e) {
				    if ((e.changedTouches.length !== 1) || (Date.now() - t > 350)) return;
				    var p = e.changedTouches[0];
				    if ((Math.abs(p.clientX - x) > 12) || (Math.abs(p.clientY - y) > 12)) return;
				    if (!/^\\/watch/.test(location.pathname)) return;
				    if (document.fullscreenElement || document.webkitFullscreenElement) return;
				    var el = (e.target instanceof Element) ? e.target : null;
				    var player = el && el.closest('#movie_player, .html5-video-player');
				    if (!player) return;
				    if (el.closest('button, a, input, [role="button"], [role="slider"], .ytp-progress-bar, ytm-progress-bar')) return;
				    var v = player.querySelector('video');
				    if (!v || v.paused || v.ended) return;
				    console.log('Fermata tap: full screen');
				    if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();
				    else if (v.requestFullscreen) v.requestFullscreen();
				  }, {capture: true, passive: true});
				})();
				""", null);
	}

	private void injectSponsorBlock() {
		String script = YoutubeSponsorBlock.getScript(getContext(), getAddon().getPreferenceStore());
		if (!script.isEmpty()) evaluateJavascript(script, result -> configureSponsorBlock());
		else configureSponsorBlock();
	}

	private void configureSponsorBlock() {
		evaluateJavascript("if (window.FermataSponsorBlock) window.FermataSponsorBlock.configure(" +
				YoutubeSponsorBlock.getConfigJson(getAddon().getPreferenceStore()) + ");", null);
	}

	protected boolean requestFullScreen() {
		loadUrl("javascript: var v = document.querySelector('video');\n" +
				"if ('webkitRequestFullscreen' in v) v.webkitRequestFullscreen();\n" +
				"else if ('requestFullscreen' in v) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);");
		return true;
	}

	void play() {
		loadUrl("javascript:window.__fermataPausedBy = null;" +
				"var v = document.querySelector('video'); if (v != null) v.play();");
	}

	// A pause from the app (panel, steering wheel) is kept: autoplay does not resume the video
	void pause() {
		loadUrl("javascript:window.__fermataPausedBy = window.__fermataAutoplay;" +
				"var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		evaluateJavascript("""
				(function() {
				  const p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				  const fn = '%s';
				  if (!p || (typeof p[fn] !== 'function')) return false;
				  const id = () => new URLSearchParams(location.search).get('v');
				  const before = id();
				  p[fn]();
				  // Outside a playlist nextVideo() does nothing when YouTube's own autoplay is off:
				  // open the first related video instead
				  // Poll instead of waiting the worst case
				  if ((fn === 'nextVideo') && before) (function wait(n) {
				    if (id() !== before) return;
				    if (n < 3) { setTimeout(wait, 200, n + 1); return; }
				    const other = a => {
				      try { return new URL(a.href).searchParams.get('v') !== before; } catch (e) { return false; }
				    };
				    const links = ['ytm-item-section-renderer[section-identifier="related-items"] a[href*="/watch?v="]',
				      'ytm-video-with-context-renderer a[href*="/watch?v="]', 'ytm-compact-video-renderer a[href*="/watch?v="]',
				      'a[href*="/watch?v="]'];
				    for (const q of links) {
				      const a = Array.from(document.querySelectorAll(q)).find(other);
				      if (a) { a.click(); return; }
				    }
				  })(0);
				  return true;
				})();
				""".formatted(next ? "nextVideo" : "previousVideo"), r -> {
			if ("true".equals(r)) return;
			// Player API not available, fall back to the page's prev/next buttons
			FermataChromeClient chrome = getWebChromeClient();
			if (chrome == null) return;
			chrome.exitFullScreen().thenRun(() -> evaluateJavascript("""
					setTimeout(() => {
					  const buttons = document.querySelectorAll('button.player-middle-controls-prev-next-button');
					  if (buttons.length > %1$d) buttons[%1$d].click();
					}, 600);
					""".formatted(next ? 1 : 0), null));
		});
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		loadUrl("javascript:\n" +
				"function retryGetVideoQualities(attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(getVideoQualities, 100, attempt + 1, openMenu);\n" +
				"  else " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", null);\n" +
				"  return null;\n" +
				"}\n" +
				"function getVideoQualities(attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retryGetVideoQualities(attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var result = '';\n" +
				"  for (let i = 0; i < options.length; i++) {\n" +
				"    if (i != 0) result += ';';\n" +
				"    if (i == select.selectedIndex) result += '*';\n" +
				"    result += options[i].innerText;\n" +
				"  }\n" +
				"  " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", result);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return result;\n" +
				"}\n" +
				"getVideoQualities(0, true);");
		return p;
	}

	void setVideoQuality(int idx) {
		loadUrl("javascript:\n" +
				"function retrySetVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(setVideoQuality, 100, idx, attempt + 1, openMenu);\n" +
				"  return false;\n" +
				"}\n" +
				"function setVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retrySetVideoQuality(idx, attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var evt = document.createEvent(\"HTMLEvents\");\n" +
				"  evt.initEvent(\"change\", true, true);\n" +
				"  select.selectedIndex = idx;\n" +
				"  options[idx].selected = true;\n" +
				"  select.dispatchEvent(evt);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"  var state = window.__fermataQ = { player: null, handler: null, timeout: null, attempts: 0 };\n" +
				"  function getPlayer() {\n" +
				"    return document.querySelector('#movie_player') || document.querySelector('.html5-video-player');\n" +
				"  }\n" +
				"  function applyHighest(p) {\n" +
				"    if (!p || typeof p.getAvailableQualityLevels !== 'function') return false;\n" +
				"    var levels = p.getAvailableQualityLevels();\n" +
				"    if (!levels || levels.length === 0) return false;\n" +
				"    var best = null;\n" +
				"    for (var i = 0; i < levels.length; i++) {\n" +
				"      if (levels[i] !== 'auto') { best = levels[i]; break; }\n" +
				"    }\n" +
				"    if (!best) return false;\n" +
				"    if (p.getPlaybackQuality && p.getPlaybackQuality() === best) return true;\n" +
				"    if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(best, best);\n" +
				"    else if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(best);\n" +
				"    else return false;\n" +
				"    return true;\n" +
				"  }\n" +
				"  function install() {\n" +
				"    var p = getPlayer();\n" +
				"    if (!p || typeof p.addEventListener !== 'function') {\n" +
				"      if (++state.attempts < 50) state.timeout = setTimeout(install, 200);\n" +
				"      return;\n" +
				"    }\n" +
				"    state.player = p;\n" +
				"    state.handler = function(s) {\n" +
				"      if ((s === 1) || (s === 3)) applyHighest(getPlayer() || p);\n" +
				"    };\n" +
				"    p.addEventListener('onStateChange', state.handler);\n" +
				"    applyHighest(p);\n" +
				"  }\n" +
				"  install();\n" +
				"})();");
	}

	void clearHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value +
						" : 0})();",
				v -> {
					try {
						p.complete((long) (Double.parseDouble(v) * 1000));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(0L);
					}
				});
		return p;
	}

	void setPosition(long position) {
		double pos = position / 1000f;
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.currentTime = " +
				pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v" +
						".playbackRate" +
						" " +
						": 0})();",
				v -> {
					try {
						p.complete(Float.parseFloat(v));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(1f);
					}
				});
		return p;
	}

	void setSpeed(float speed) {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		// The page title is updated only some time after a video is opened, the player knows it already
		evaluateJavascript("""
				(function() {
				  var v = document.querySelector('video');
				  var p = v && v.closest('#movie_player');
				  var d = p && p.getVideoData && p.getVideoData();
				  return (d && d.title) || document.title;
				})()""", json -> p.complete(jsonString(json)));
		return p;
	}

	FutureSupplier<String> getVideoId() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("""
				(function() {
				  var v = document.querySelector('video');
				  var p = v && v.closest('#movie_player');
				  var d = p && p.getVideoData && p.getVideoData();
				  return (d && d.video_id) || new URLSearchParams(location.search).get('v');
				})()""", json -> p.complete(jsonString(json)));
		return p;
	}

	// evaluateJavascript() returns its result as JSON, so a string comes quoted and escaped
	private static String jsonString(String json) {
		try {
			Object v = new JSONTokener(json).nextValue();
			return (v instanceof String) ? (String) v : null;
		} catch (JSONException ex) {
			return json;
		}
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		loadUrl("javascript:" +
				"document.querySelectorAll('video')" +
				".forEach(v=> v.setAttribute('style', 'object-fit:" + p + "'));");
	}
}
