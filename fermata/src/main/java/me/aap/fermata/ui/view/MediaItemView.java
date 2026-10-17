package me.aap.fermata.ui.view;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.misc.MiscUtils.ifNull;
import static me.aap.utils.ui.UiUtils.getTextAppearanceSize;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemView extends ConstraintLayout
		implements OnLongClickListener, OnCheckedChangeListener, Item.ChangeListener {
	private static final RotateAnimation rotate =
			new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
					0.5f);
	private static Drawable loadingDrawable;
	@ColorInt
	private static int iconColor;
	@ColorInt
	private static int hintColor;
	@StyleRes
	private final int titleTextAppearance;
	@StyleRes
	private final int subtitleTextAppearance;
	private final ColorStateList iconTint;
	private final ColorStateList textTint;
	@Nullable
	private final ColorStateList subtitleTint;
	private final float iconAspectRatio;
	private final float iconCornerRadius;
	private final float iconExtraSize;
	private final int iconBackground;
	private final boolean boldTitle;
	@DrawableRes
	private final int defaultCover;
	private boolean wideIcon;
	// Grid cards wider than square: logos are fitted, not cropped
	private boolean wideCard;
	private boolean gridLayout;
	private float gridRatio = 1f;
	private Paint livePaint;
	private Paint liveTextPaint;
	private String liveText;
	@Nullable
	private MediaItemViewHolder holder;
	private ProgressUpdater progressUpdater;
	private VectorDrawableCompat watchedVideoDrawable;
	private VectorDrawableCompat watchingVideoDrawable;
	private VectorDrawableCompat archiveLabelDrawable;
	private FutureSupplier<MediaDescriptionCompat> loading;

	public MediaItemView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs, R.attr.appMediaItemStyle);
		TypedArray ta =
				ctx.obtainStyledAttributes(attrs, R.styleable.MediaItemView, R.attr.appMediaItemStyle,
						R.style.AppTheme_MediaItemStyle);
		iconColor = ta.getColor(R.styleable.MediaItemView_iconColor, 0);
		hintColor = ta.getColor(R.styleable.MediaItemView_hintColor, 0);
		titleTextAppearance = ta.getResourceId(R.styleable.MediaItemView_titleTextAppearance, 0);
		subtitleTextAppearance = ta.getResourceId(R.styleable.MediaItemView_subtitleTextAppearance, 0);
		setElevation(ta.getDimension(R.styleable.MediaItemView_elevation, 0));
		textTint = ta.getColorStateList(R.styleable.MediaItemView_android_textColor);
		subtitleTint = ta.getColorStateList(R.styleable.MediaItemView_mediaItemSubtitleColor);
		iconAspectRatio = ta.getFloat(R.styleable.MediaItemView_mediaItemIconAspectRatio, 1f);
		iconCornerRadius = ta.getDimension(R.styleable.MediaItemView_mediaItemIconCornerRadius, 0);
		iconExtraSize = ta.getDimension(R.styleable.MediaItemView_mediaItemIconExtraSize, toPx(ctx, 10));
		iconBackground = ta.getResourceId(R.styleable.MediaItemView_mediaItemIconBackground, 0);
		boldTitle = ta.getBoolean(R.styleable.MediaItemView_mediaItemBoldTitle, false);
		defaultCover = ta.getResourceId(R.styleable.MediaItemView_mediaItemDefaultCover, 0);
		ta.recycle();
		MainActivityDelegate a = getMainActivity();
		applyLayout(ctx, a.isGridView(), a.getPrefs().getTextIconSizePref(a));
		iconTint = getIcon().getImageTintList();
		setLongClickable(true);
		setOnLongClickListener(this);
		setBackgroundResource(R.drawable.media_item_bg);
		setFocusable(true);
	}

	public void applyLayout(Context ctx, boolean grid, float size) {
		removeAllViews();
		inflate(ctx, grid ? R.layout.media_item_grid_layout : R.layout.media_item_list_layout, this);
		getCheckBox().setOnCheckedChangeListener(this);
		// Shown by refreshCheckbox() when the list is in selection mode
		getCheckBox().setVisibility(GONE);
		gridLayout = grid;
		gridRatio = grid ? getMainActivity().getGridIconRatio() : 1f;
		wideCard = grid && (gridRatio > 1f);

		if (wideCard) {
			ImageView i = getIcon();
			LayoutParams lp = (LayoutParams) i.getLayoutParams();
			lp.dimensionRatio = gridRatio + ":1";
			i.setLayoutParams(lp);
		}

		setSize(ctx, grid, size);
	}

	// The screen may switch between list and grid per folder, e.g. TV channels and their EPG
	private boolean isLayoutStale(MainActivityDelegate a) {
		boolean grid = a.isGridView();
		if (grid != gridLayout) return true;
		return grid && (a.getGridIconRatio() != gridRatio);
	}

	public void setSize(Context ctx, boolean grid, float size) {
		setTextAppearance(ctx, getTitle(), titleTextAppearance, size, textTint);
		setTextAppearance(ctx, getSubtitle(), subtitleTextAppearance, size,
				(subtitleTint != null) ? subtitleTint : textTint);
		wideIcon = wideCard || (!grid && (iconAspectRatio != 1f));
		if (!grid) {
			int iconSize = (int) (getTitle().getTextSize() + getSubtitle().getTextSize() +
					iconExtraSize);
			ImageView i = getIcon();
			ViewGroup.LayoutParams lp = i.getLayoutParams();
			lp.height = iconSize;
			lp.width = (int) (iconSize * iconAspectRatio);
			i.setLayoutParams(lp);
		}
		if (wideIcon) styleWideIcon(getIcon());
	}

	// A wide, rounded tile: bitmaps fill it, resource icons are centered on its background
	private void styleWideIcon(ImageView i) {
		if (iconBackground != 0) i.setBackgroundResource(iconBackground);
		if (iconCornerRadius > 0) {
			i.setOutlineProvider(new ViewOutlineProvider() {
				@Override
				public void getOutline(View v, Outline o) {
					o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), iconCornerRadius);
				}
			});
			i.setClipToOutline(true);
		}
	}

	private void setIconBitmap(ImageView icon, Bitmap bm) {
		icon.setImageTintList(null);
		if (wideCard && !getMainActivity().isGridIconCropped()) {
			setCardScale(icon, 6);
		} else if (wideIcon) {
			icon.setPadding(0, 0, 0, 0);
			icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
		}
		icon.setImageBitmap(bm);
	}

	// A card shows the whole picture, resource icons get more room around them
	private void setCardScale(ImageView icon, int paddingDp) {
		int p = (int) toPx(getContext(), paddingDp);
		icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
		icon.setPadding(p, p, p, p);
	}

	// The generic video/audio icon is replaced with the theme's cover, if it has one
	private void setItemIcon(ImageView icon, Item i) {
		int res = i.getIcon();
		if ((defaultCover != 0) && ((res == R.drawable.video) || (res == R.drawable.audiotrack))) {
			icon.setImageTintList(null);
			if (wideCard) setCardScale(icon, 10);
			else if (wideIcon) icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			icon.setImageResource(defaultCover);
		} else {
			setIconResource(icon, res);
		}
	}

	private void setIconResource(ImageView icon, @DrawableRes int res) {
		icon.setImageTintList(iconTint);
		if (wideCard) setCardScale(icon, 24);
		else if (wideIcon) icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		icon.setImageResource(res);
	}

	private void setTextAppearance(Context ctx, TextView v, @StyleRes int res, float scale,
																 ColorStateList color) {
		v.setTextAppearance(res);
		v.setTextSize(COMPLEX_UNIT_PX, getTextAppearanceSize(ctx, res) * scale);
		v.setTextColor(color);
	}

	public void setHolder(@Nullable MediaItemViewHolder holder) {
		this.holder = holder;
	}

	@Nullable
	public MediaItemViewHolder getHolder() {
		return holder;
	}

	@Nullable
	public MediaItemListView getListView() {
		MediaItemViewHolder h = getHolder();
		return (h != null) ? h.getListView() : null;
	}

	@Nullable
	public MediaItemWrapper getItemWrapper() {
		MediaItemViewHolder h = getHolder();
		return (h != null) ? h.getItemWrapper() : null;
	}

	@Nullable
	public Item getItem() {
		MediaItemWrapper w = getItemWrapper();
		return (w != null) ? w.getItem() : null;
	}

	public void rebind(@Nullable MediaItemWrapper oldItem, @Nullable MediaItemWrapper newItem) {
		if ((newItem != null) && isLayoutStale(getMainActivity())) {
			MainActivityDelegate a = getMainActivity();
			cancelLoading();
			if (oldItem != null) oldItem.getItem().removeChangeListener(this);
			applyLayout(getContext(), a.isGridView(), a.getPrefs().getTextIconSizePref(a));
			oldItem = null;
		}
		if (oldItem == newItem) return;
		cancelLoading();
		if (oldItem != null) oldItem.getItem().removeChangeListener(this);
		if ((newItem == null) || (getVisibility() != VISIBLE)) return;
		boolean hasListener = newItem.getItem().addChangeListener(this);
		load(newItem, !hasListener).onCompletion((r, err) -> {
			if (getItemWrapper() != newItem) return;
			if ((err != null) && !isCancellation(err))
				Log.e(err, "Failed to load media description: ", newItem);
		});
	}

	@Override
	public void mediaItemChanged(Item i) {
		MediaItemWrapper w = getItemWrapper();
		if ((w != null) && (w.getItem() == i)) load(w, false);
	}

	private void cancelLoading() {
		if (loading == null) return;
		loading.cancel();
		loading = null;
	}

	private FutureSupplier<MediaDescriptionCompat> load(MediaItemWrapper w, boolean showLoading) {
		cancelLoading();
		Item i = w.getItem();

		if (i instanceof EpgItem) {
			EpgItem e = (EpgItem) i;
			setProgress(i, e.getStartTime(), e.getEndTime());
		} else {
			setProgress(i, 0, 0);
		}

		FutureSupplier<MediaDescriptionCompat> load =
				loading = i.getMediaDescription().main().addConsumer((md, fail, p, total) -> {
					if (getItemWrapper() != w) return;

					if (fail != null) {
						if (isCancellation(fail)) return;
						Log.e(fail, "Failed to load media description: ", i);
						setDefaults(i, false);
						return;
					}

					if ((p == PROGRESS_DONE) || (p == 1)) {
						getTitle().setText(ifNull(md.getTitle(), i::getName));
					}
					if ((p == PROGRESS_DONE) || (p == 2)) {
						getSubtitle().setText(md.getSubtitle());
					}
					if ((p == PROGRESS_DONE) || (p == 3)) {
						Bundle b = md.getExtras();
						if (b != null) setProgress(i, b.getLong(STREAM_START_TIME),
								b.getLong(STREAM_END_TIME));
					}
					if ((p == PROGRESS_DONE) || (p == 4)) {
						Uri uri = md.getIconUri();

						if ((uri != null) && !SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
							FutureSupplier<Bitmap> loadIcon =
									i.getLib().getBitmap(uri.toString(), true, true).main()
											.onCompletion((bm, err) -> {
												if (getItemWrapper() != w) return;

												ImageView icon = getIcon();
												icon.clearAnimation();
												cancelLoading();

												if (bm != null) {
													setIconBitmap(icon, bm);
												} else {
													setItemIcon(icon, i);
												}
											});

							if (!loadIcon.isDone()) {
								ImageView icon = getIcon();
								icon.clearAnimation();
								setItemIcon(icon, i);
							}
						} else {
							ImageView icon = getIcon();
							icon.clearAnimation();
							setItemIcon(icon, i);
							cancelLoading();
						}
					}
				});

		if (!load.isDone()) setDefaults(i, showLoading);
		return load;
	}

	private void setProgress(Item i, long start, long end) {
		LinearProgressIndicator p = getProgress();

		if (progressUpdater != null) {
			progressUpdater.cancel();
			progressUpdater = null;
		}

		if ((start > 0) && (end > start)) {
			progressUpdater = new ProgressUpdater(i, p, start, end);
			progressUpdater.run();
			p.setVisibility(VISIBLE);
		} else {
			p.setVisibility(GONE);
		}
	}

	private void setDefaults(Item i, boolean loading) {
		ImageView icon = getIcon();
		getTitle().setText(i.getName());

		if (loading) {
			rotate.setDuration(1000);
			rotate.setRepeatCount(Animation.INFINITE);
			if (wideIcon) icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			icon.setImageDrawable(getLoadingDrawable(getContext()));
			icon.startAnimation(rotate);
			getSubtitle().setText(R.string.loading);
		} else {
			icon.clearAnimation();
			setItemIcon(icon, i);
			getSubtitle().setText("");
		}
	}

	private static Drawable getLoadingDrawable(Context ctx) {
		if (loadingDrawable == null) {
			loadingDrawable = ContextCompat.getDrawable(ctx, R.drawable.loading);
			Objects.requireNonNull(loadingDrawable).setTint(iconColor);
			MainActivityDelegate.get(ctx)
					.addBroadcastListener(MediaItemView::onActivityDestroyEvent, ACTIVITY_DESTROY);
		}

		return loadingDrawable;
	}

	private static void onActivityDestroyEvent(ActivityDelegate a, long e) {
		loadingDrawable = null;
		a.removeBroadcastListener(MediaItemView::onActivityDestroyEvent);
	}

	public ImageView getIcon() {
		return (ImageView) getChildAt(0);
	}

	public TextView getTitle() {
		return (TextView) getChildAt(1);
	}

	public TextView getSubtitle() {
		return (TextView) getChildAt(2);
	}

	public LinearProgressIndicator getProgress() {
		return (LinearProgressIndicator) getChildAt(3);
	}

	public MaterialCheckBox getCheckBox() {
		return (MaterialCheckBox) getChildAt(4);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		MediaItemWrapper w = getItemWrapper();
		if (w != null) w.setSelected(isChecked, false);
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		Item item = getItem();
		if ((visibility == VISIBLE) && (item != null)) refresh();
	}

	@Override
	public void onDrawForeground(Canvas canvas) {
		super.onDrawForeground(canvas);
		Item item = getItem();
		VectorDrawableCompat d;
		if (gridLayout && (item instanceof StreamItem)) drawLiveBadge(canvas);

		if ((item instanceof ArchiveItem) && !((ArchiveItem) item).isExpired()) {
			d = archiveLabelDrawable;
			if (d == null) {
				d = archiveLabelDrawable =
						VectorDrawableCompat.create(getResources(), R.drawable.archive_label, null);
				if (d == null) return;
				d.setTint(hintColor);
			}
		} else {
			if (!(item instanceof PlayableItem) || ((PlayableItem) item).isStream()) return;
			PlayableItem p = (PlayableItem) item;
			if (!p.isVideo()) return;

			PlayableItemPrefs prefs = p.getPrefs();

			if (prefs.getWatchedPref()) {
				d = watchedVideoDrawable;
				if (d == null) {
					d = watchedVideoDrawable =
							VectorDrawableCompat.create(getResources(), R.drawable.done, null);
					if (d == null) return;
					d.setTint(hintColor);
				}
			} else if (prefs.getPositionPref() > 0) {
				d = watchingVideoDrawable;
				if (d == null) {
					d = watchingVideoDrawable =
							VectorDrawableCompat.create(getResources(), R.drawable.watching, null);
					if (d == null) return;
					d.setTint(hintColor);
				}
			} else {
				return;
			}
		}

		ImageView i = getIcon();
		int l = i.getLeft();
		int t = i.getTop();
		int r = i.getRight();
		int b = i.getBottom();
		d.setBounds(l + (r - l) / 3, t + (b - t) / 3, r, b);
		d.draw(canvas);
	}

	// A small "LIVE" pill in the top left corner of the card
	private void drawLiveBadge(Canvas canvas) {
		Context ctx = getContext();
		if (livePaint == null) {
			livePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			livePaint.setColor(0xFFE50914);
			liveTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			liveTextPaint.setColor(Color.WHITE);
			liveTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
			liveTextPaint.setTextSize(toPx(ctx, 9));
			liveTextPaint.setLetterSpacing(0.05f);
			liveText = ctx.getString(R.string.live_badge);
		}
		ImageView i = getIcon();
		float m = toPx(ctx, 6);
		float px = toPx(ctx, 5);
		float py = toPx(ctx, 2);
		Paint.FontMetrics fm = liveTextPaint.getFontMetrics();
		float w = liveTextPaint.measureText(liveText) + 2 * px;
		float h = (fm.descent - fm.ascent) + 2 * py;
		float l = i.getLeft() + m;
		float t = i.getTop() + m;
		float r = h / 2;
		canvas.drawRoundRect(l, t, l + w, t + h, r, r, livePaint);
		canvas.drawText(liveText, l + px, t + py - fm.ascent, liveTextPaint);
	}

	public void refresh() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;
		Item item = w.getItem();
		if ((item instanceof PlayableItem) && ((PlayableItem) item).isVideo()) {
			rebind(w, null);
			rebind(null, w);
		}
		refreshState(item);
	}

	private void refreshState(Item item) {
		MainActivityDelegate a = getMainActivity();
		refreshCheckbox();

		if (item instanceof EpgItem) {
			EpgItem e = (EpgItem) item;
			EpgItem prev = e.getPrev();
			boolean arch = e instanceof ArchiveItem;
			long time = System.currentTimeMillis();
			long end = e.getEndTime();
			long updateDelay = 0;
			if ((end > time) && (e.getStartTime() <= time)) updateDelay = end - time;

			if (arch && (!(prev instanceof ArchiveItem))) {
				long delay = ((ArchiveItem) e).getExpirationTime() - time;
				if ((delay > 0) && (delay > updateDelay)) updateDelay = delay;
			}

			if (updateDelay > 0) {
				setState(Typeface.BOLD, false);
				a.postDelayed(() -> {
					if (getItem() != e) return;
					refreshState(e);
					EpgItem next = e.getNext();

					if (next != null) {
						ActivityFragment f = a.getActiveFragment();
						if (f instanceof MediaLibFragment) {
							MediaItemView focus = ((MediaLibFragment) f).getListView().focusTo(next);
							if (focus != null) focus.refresh();
						}
					}
				}, updateDelay);

				if (!arch) {
					setSelected(false);
					setState(Typeface.NORMAL, false);
					return;
				}
			}
		}

		var cur = a.getCurrentPlayable();
		if (item.equals(cur)) {
			setSelected(true);
			setState(Typeface.BOLD, true);
		} else {
			boolean last =
					(cur == null) && ((item instanceof PlayableItem) && ((PlayableItem) item).isLastPlayed());
			int type = last ? Typeface.BOLD : Typeface.NORMAL;
			setSelected(false);
			setState(type, last);
		}
	}

	private void setState(int surfaceType, boolean activated) {
		TextView t = getTitle();
		t.setTypeface(null, boldTitle ? Typeface.BOLD : surfaceType);
		t.setActivated(activated);
		t = getSubtitle();
		t.setTypeface(null, surfaceType);
		t.setActivated(activated);
		setActivated(activated);
	}

	public void refreshCheckbox() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;
		MaterialCheckBox cb = getCheckBox();

		if (!w.isSelectionSupported()) {
			cb.setVisibility(GONE);
			return;
		}

		MediaItemListView l = getListView();

		if ((l != null) && l.isSelectionActive()) {
			cb.setVisibility(VISIBLE);
			cb.setChecked(w.isSelected());
		} else {
			cb.setVisibility(GONE);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		MainActivityDelegate a = getMainActivity();
		OverlayMenu menu = a.getContextMenu();
		MediaItemMenuHandler handler = new MediaItemMenuHandler(menu, this);
		MediaItemListView l = getListView();
		if (l != null) getListView().discardSelection();
		handler.show();
		return true;
	}

	@Override
	protected void onFocusChanged(boolean gainFocus, int direction,
																@Nullable Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		if (!BuildConfig.AUTO || !gainFocus) return;
		MainActivityDelegate d = getMainActivity();
		if (d.getPrefs().useDpadCursor(d)) {
			View c = d.findViewById(R.id.cursor);
			if ((c != null) && (c.getVisibility() == VISIBLE)) return;
		}

		// The next item in the list must always be visible for rotary input scrolling
		MediaItemListView lv = getListView();
		if (lv == null) return;
		int idx = lv.indexOfChild(this);

		if (idx == 0) {
			List<MediaItemWrapper> l = lv.getAdapter().getList();
			idx = l.indexOf(getItemWrapper());
			if (idx == 0) return;
			idx--;
		} else if (idx == (lv.getChildCount() - 1)) {
			List<MediaItemWrapper> l = lv.getAdapter().getList();
			idx = l.indexOf(getItemWrapper());
			if (idx == (l.size() - 1)) return;
			idx++;
		} else {
			return;
		}

		lv.scrollToPosition(idx, false);
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private final class ProgressUpdater implements Runnable, Cancellable {
		private final Item item;
		private final LinearProgressIndicator progress;
		private final long start;
		private final long end;
		private Cancellable scheduled;

		private ProgressUpdater(Item item, LinearProgressIndicator progress, long start, long end) {
			this.item = item;
			this.progress = progress;
			this.start = start;
			this.end = end;
		}

		@Override
		public void run() {
			if (!isValid()) return;
			long time = System.currentTimeMillis();

			if (time >= end) {
				progressUpdater = null;
				progress.setProgressCompat(100, true);
				update();
			} else if (start > time) {
				progress.setProgressCompat(0, true);
				runAt(time, start);
			} else {
				long dur = end - start;
				int prog = (int) ((time - start) * 100 / dur);
				progress.setProgressCompat(prog, true);

				if (prog >= 99) {
					runAt(time, end);
				} else {
					long upTime = start + (prog + 1) * dur / 100;
					runAt(time, Math.min(upTime, end));
				}
			}
		}

		@Override
		public boolean cancel() {
			return (scheduled != null) && scheduled.cancel();
		}

		private boolean isValid() {
			return (progressUpdater == this) && (item == getItem()) && (item != null);
		}

		private void runAt(long curTime, long atTime) {
			if (scheduled != null) scheduled.cancel();
			scheduled = getMainActivity().postDelayed(this, atTime - curTime);
		}

		private void update() {
			item.getMediaDescription().main().onSuccess(d -> {
				if (!isValid()) return;
				Bundle b = d.getExtras();
				if (b != null) {
					long start = b.getLong(STREAM_START_TIME);
					long end = b.getLong(STREAM_END_TIME);
					if ((start == this.start) && (end == this.end)) return;
				}
				refresh();
			});
		}
	}
}
