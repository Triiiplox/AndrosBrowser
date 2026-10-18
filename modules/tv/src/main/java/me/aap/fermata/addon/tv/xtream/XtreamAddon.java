package me.aap.fermata.addon.tv.xtream;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Entry.Kind;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataActivityAddon;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * IPTV of Xtream Codes accounts, Netflix style: its own screen, and a media library tree for the
 * car and the media session. Watches the playback to keep "continue watching" up to date.
 */
@Keep
@SuppressWarnings("unused")
public class XtreamAddon implements MediaLibAddon, FermataActivityAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(XtreamAddon.class.getName());
	private static XtreamRootItem root;
	private final PlaybackRecorder recorder = new PlaybackRecorder();

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.xtream_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new XtreamFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return i instanceof XtreamItem;
	}

	@Override
	public XtreamRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) root = new XtreamRootItem(lib);
		return root;
	}

	public static XtreamRootItem getRoot(MainActivityDelegate a) {
		XtreamRootItem r = root;
		if ((r != null) && (r.getLib() == a.getLib())) return r;
		return root = new XtreamRootItem((DefaultMediaLib) a.getLib());
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
																								String id) {
		return getRootItem(lib).getItem(scheme, id);
	}

	@Override
	public void onActivityCreate(MainActivityDelegate a) {
		recorder.repo = XtreamRepo.get(a.getContext());
		recorder.binder = a.getMediaServiceBinder();
		recorder.binder.addBroadcastListener(recorder);
	}

	@Override
	public void onActivityPause(MainActivityDelegate a) {
		recorder.saveCurrent();
	}

	@Override
	public void onActivityDestroy(MainActivityDelegate a) {
		recorder.saveCurrent();
		if (recorder.binder != null) recorder.binder.removeBroadcastListener(recorder);
		recorder.binder = null;
	}

	/**
	 * Keeps the user library in step with the playback: a channel or a video moves to the top when
	 * it starts; the position of a video is saved when another item starts and when the app goes to
	 * the background. The app saves the position of the left item a moment after the change, so it
	 * is read a bit later.
	 */
	private static final class PlaybackRecorder implements FermataServiceUiBinder.Listener {
		XtreamRepo repo;
		FermataServiceUiBinder binder;

		@Override
		public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
			if (newItem instanceof XtreamPlayable n) save(n, n.getPrefs().getPositionPref());
			if (oldItem instanceof XtreamPlayable o) {
				App.get().getHandler().postDelayed(() -> save(o, o.getPrefs().getPositionPref()), 1500);
			}
		}

		void saveCurrent() {
			FermataServiceUiBinder b = binder;
			if (b == null) return;
			if (!(b.getCurrentItem() instanceof XtreamPlayable i)) return;
			MediaEngine eng = b.getMediaSessionCallback().getEngine();
			if ((eng == null) || (eng.getSource() != i)) return;
			eng.getPosition().onSuccess(pos -> save(i, pos));
		}

		private void save(XtreamPlayable i, long pos) {
			// A past program would show in the recent channels under its own title
			if ((repo == null) || i.isCatchup()) return;
			i.getMediaData().onCompletion((md, err) -> {
				long dur = (md != null) ? md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) : 0;
				long p = (i.getKind() == Kind.LIVE) ? 0 : Math.max(0, pos);
				UserLibrary.Record r = new UserLibrary.Record(i.getAccountId(), i.getKind(),
						i.getStreamId(), i.getName(), i.getSubtitle(), i.getImage(), i.getExtension(),
						i.getSeriesId(), i.getSeason(), i.getEpisode(), p, dur, System.currentTimeMillis());
				repo.run(() -> {
					repo.getLibrary().played(r);
					return null;
				});
			});
		}
	}
}
