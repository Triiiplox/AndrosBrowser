package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeChromeClient extends FermataChromeClient {

	@Nullable
	private Runnable fullScreenListener;

	public YoutubeChromeClient(FermataWebView web, VideoView videoView) {
		super(web, videoView);
	}

	/** Told when the video enters or leaves the full screen, e.g. to hide the mini player's bar. */
	void setFullScreenListener(@Nullable Runnable l) {
		fullScreenListener = l;
	}

	@Override
	public VideoView getFullScreenView() {
		return (VideoView) super.getFullScreenView();
	}

	protected void addCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		vv.setVisibility(VISIBLE);
	}

	protected void removeCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).removeView(view);
		vv.setVisibility(GONE);
	}

	protected void setFullScreen(MainActivityDelegate a, boolean fullScreen) {
		a.setVideoMode(fullScreen, getFullScreenView());
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
		getWebView().setVisibility(GONE);
		super.onShowCustomView(view, callback);
		if (fullScreenListener != null) fullScreenListener.run();
	}

	@Override
	public void onHideCustomView() {
		super.onHideCustomView();
		if (fullScreenListener != null) fullScreenListener.run();
	}

	@Override
	public boolean canEnterFullScreen() {
		MainActivityDelegate a = MainActivityDelegate.get(getWebView().getContext());
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (!((cb.getEngine() instanceof YoutubeMediaEngine))) return false;
		int st = cb.getPlaybackState().getState();
		return (st == STATE_PLAYING) || (st == STATE_PAUSED);
	}

	protected boolean onTouchEvent(View v, MotionEvent event) {
		return isFullScreen() && getFullScreenView().onTouchEvent(event);
	}
}
