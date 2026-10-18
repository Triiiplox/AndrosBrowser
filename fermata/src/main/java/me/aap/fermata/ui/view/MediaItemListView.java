package me.aap.fermata.ui.view;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListView extends RecyclerView implements PreferenceStore.Listener {
	private boolean isSelectionActive;
	private boolean grid;
	private int focusReq;
	@Nullable
	private CharSequence emptyText;
	@Nullable
	private StaticLayout emptyLayout;

	public MediaItemListView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		configure(ctx.getResources().getConfiguration());
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
	}

	/** What the list says while it has nothing, e.g. how to add the first folder; null for nothing. */
	public void setEmptyText(@Nullable CharSequence text) {
		emptyText = text;
		emptyLayout = null;
		invalidate();
	}

	// Not draw(): a list that "will not draw" (no overscroll glow) skips it, not dispatchDraw()
	@Override
	protected void dispatchDraw(@NonNull Canvas c) {
		super.dispatchDraw(c);
		CharSequence text = emptyText;
		Adapter<?> a = getAdapter();
		if ((text == null) || ((a != null) && (a.getItemCount() > 0))) return;
		int w = getWidth() - toIntPx(getContext(), 64);
		if (w <= 0) return;
		StaticLayout l = emptyLayout;
		if ((l == null) || (l.getWidth() != w)) {
			TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			TypedValue v = new TypedValue();
			getContext().getTheme().resolveAttribute(android.R.attr.textColorSecondary, v, true);
			p.setColor((v.resourceId != 0) ? getContext().getColor(v.resourceId) : v.data);
			p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17,
					getResources().getDisplayMetrics()));
			emptyLayout = l = StaticLayout.Builder.obtain(text, 0, text.length(), p, w)
					.setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0, 1.2f).build();
		}
		c.save();
		c.translate((getWidth() - w) / 2f, (getHeight() - l.getHeight()) / 2f);
		l.draw(c);
		c.restore();
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		configure(newConfig);
	}

	// Re-applies the list / grid layout, when the screen decides it per folder
	public void configure() {
		configure(getContext().getResources().getConfiguration());
	}

	private void configure(Configuration cfg) {
		Context ctx = getContext();
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			MainActivityPrefs prefs = a.getPrefs();
			grid = a.isGridView();

			if (grid) {
				float scale = prefs.getTextIconSizePref(a);
				// Wide cards need wider columns to keep the logos readable
				int base = (a.getGridIconRatio() > 1f) ? 168 : 128;
				int span = (int) Math.max(cfg.screenWidthDp / (base * scale), 2);
				LayoutManager lm = getLayoutManager();
				if ((lm instanceof GridLayoutManager) && (((GridLayoutManager) lm).getSpanCount() == span))
					return;
				setLayoutManager(new GridLayoutManager(ctx, span));
			} else if (!(getLayoutManager() instanceof LinearLayoutManager) ||
					(getLayoutManager() instanceof GridLayoutManager)) {
				setLayoutManager(new LinearLayoutManager(ctx));
			}
		});
	}

	@NonNull
	@Override
	public MediaItemListViewAdapter getAdapter() {
		return (MediaItemListViewAdapter) requireNonNull(super.getAdapter());
	}

	public boolean isSelectionActive() {
		return isSelectionActive;
	}

	public void select(boolean select) {
		if (!select && !isSelectionActive) return;

		boolean selectAll = isSelectionActive;
		isSelectionActive = select;

		for (MediaItemWrapper w : getAdapter().getList()) {
			if (selectAll) w.setSelected(select, true);
			else w.refreshViewCheckbox();
		}
	}

	public void discardSelection() {
		select(false);
	}

	public void refresh() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	public void refreshState() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	@Override
	public void smoothScrollToPosition(int position) {
		scrollToPosition(position);
	}

	@Override
	public void scrollToPosition(int position) {
		scrollToPosition(position, true);
	}

	public void scrollToPosition(int position, boolean focus) {
		List<MediaItemWrapper> list = getAdapter().getList();
		if ((position < 0) || (position >= list.size())) return;

		focusReq = -1;
		super.scrollToPosition(position);
		if (!focus || !isVisible(this)) return;
		MainActivityDelegate a = getActivity();
		if (a.getBody().isVideoMode()) return;
		MediaItemWrapper w = list.get(position);
		MediaItemViewHolder h = w.getViewHolder();
		if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) h.getItemView().requestFocus();
		else focusReq = position;
	}

	public int getScrollPosition() {
		List<MediaItemWrapper> list = getAdapter().getList();
		if (list.isEmpty()) return 0;

		for (int i = 0, n = getChildCount(); i < n; i++) {
			MediaItemView v = (MediaItemView) getChildAt(i);
			MediaItemWrapper w = v.getItemWrapper();
			if (w == null) continue;
			MediaItemViewHolder h = w.getViewHolder();
			if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) {
				for (int j = 0, s = list.size(); j < s; j++) {
					if (list.get(j) == w) return j;
				}
			}
		}
		return 0;
	}

	void holderAttached(MediaItemViewHolder h) {
		if ((focusReq != -1) && (h.getAdapterPosition() == focusReq)) {
			focusReq = -1;
			h.getItemView().requestFocus();
		}
	}

	public void pageUp() {
		int cnt = getChildCount();
		if (cnt == 0) return;
		MediaItemWrapper w = ((MediaItemView) getChildAt(0)).getItemWrapper();
		if (w == null) return;
		int idx = getAdapter().getList().indexOf(w);
		if (idx > 0) scrollToPosition(Math.max(idx - cnt, 0), false);
	}

	public void pageDown() {
		int cnt = getChildCount();
		if (cnt == 0) return;
		MediaItemWrapper w = ((MediaItemView) getChildAt(cnt - 1)).getItemWrapper();
		if (w == null) return;
		int idx = getAdapter().getList().indexOf(w);
		if (idx < 0) return;
		int size = getAdapter().getList().size();
		scrollToPosition(Math.min(idx + cnt, size - 1), false);
	}

	@Nullable
	public static View focusSearchFirst(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView v = ((MediaLibFragment) f).getListView();
			List<MediaItemWrapper> list = v.getAdapter().getList();
			return ((list != null) && !list.isEmpty()) ? v.focusTo(focused, list, 0) : v.focusEmpty();
		}
		return null;
	}

	@Nullable
	public static View focusSearchFirstVisible(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView lv = ((MediaLibFragment) f).getListView();
			LinearLayoutManager lm = (LinearLayoutManager) lv.getLayoutManager();
			if (lm != null) {
				int pos = lm.findFirstVisibleItemPosition();
				if (pos != RecyclerView.NO_POSITION) {
					return lv.getAdapter().getList().get(pos).getView();
				}
			}
			View first = lv.getChildAt(0);
			return (first != null) ? first : lv.focusEmpty();
		}
		return focused;
	}

	@Nullable
	public static View focusSearchLast(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		if (f instanceof MediaLibFragment) {
			MediaItemListView v = ((MediaLibFragment) f).getListView();
			List<MediaItemWrapper> list = v.getAdapter().getList();
			return ((list != null) && !list.isEmpty())
					? v.focusTo(focused, list, list.size() - 1)
					: v.focusEmpty();
		}
		return null;
	}

	public static void focusActive(Context ctx, @Nullable View focused) {
		View v = focusSearchActive(ctx, focused);
		if (v != null) App.get().getHandler().post(v::requestFocus);
	}

	public static View focusSearchActive(Context ctx, @Nullable View focused) {
		ActivityFragment f = MainActivityDelegate.get(ctx).getActiveFragment();
		return (f instanceof MediaLibFragment)
				? (((MediaLibFragment) f).getListView()).findActive(focused) : null;
	}

	private View findActive(View focused) {
		List<MediaItemWrapper> list = getAdapter().getList();
		MainActivityDelegate a = getActivity();
		PlayableItem p = a.getCurrentPlayable();

		if (p != null) {
			for (int i = 0, n = list.size(); i < n; i++) {
				MediaItemWrapper w = list.get(i);
				if (w.getItem() == p) return focusTo(focused, w, i);
			}
		}

		int n = list.size();
		if (n == 0) return focusEmpty();

		for (int i = 0; i < n; i++) {
			MediaItemWrapper w = list.get(i);
			Item item = w.getItem();
			if ((item instanceof PlayableItem) && ((PlayableItem) item).isLastPlayed()) {
				return focusTo(focused, w, i);
			}
		}

		return focusTo(focused, list.get(0), 0);
	}

	private View focusEmpty() {
		View v = getActivity().getFloatingButton();
		return isVisible(v) ? v : this;
	}

	@Override
	public View focusSearch(@Nullable View focused, int direction) {
		if (!(focused instanceof MediaItemView) || (focused.getParent() != this)) return focused;
		if (MainActivityDelegate.get(focused.getContext()).getActiveMenu() instanceof View v)
			return v.findFocus();

		if (grid) {
			if (direction == FOCUS_LEFT) {
				List<MediaItemWrapper> list = getAdapter().getList();
				if (list != null) return focusTo(focused, list, indexOf(list, focused) - 1);
			} else if (direction == FOCUS_RIGHT) {
				List<MediaItemWrapper> list = getAdapter().getList();
				int idx = indexOf(list, focused);
				if ((idx != -1) && (idx < list.size() - 1)) return focusTo(focused, list, idx + 1);
				else return focusRight(focused);
			}

			return super.focusSearch(focused, direction);
		}

		if (direction == FOCUS_LEFT) return focusLeft(focused);
		if (direction == FOCUS_RIGHT) return focusRight(focused);

		List<MediaItemWrapper> list = getAdapter().getList();

		if ((list == null) || list.isEmpty()) {
			return (direction == FOCUS_UP) ? focusUp(focused) : focusDown(focused);
		}

		ViewHolder vh = getChildViewHolder(focused);
		if (!(vh instanceof MediaItemViewHolder)) return focused;

		int pos = vh.getAdapterPosition();
		if ((pos < 0) || (pos >= list.size())) return focused;
		return focusTo(focused, list, (direction == FOCUS_UP) ? (pos - 1) : (pos + 1));
	}

	private View focusLeft(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		NavBarView n = a.getNavBar();
		if (isVisible(n) && n.isLeft()) return n.focusSearch();
		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, 0) : focused;
	}

	private View focusRight(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		BodyLayout b = a.getBody();
		if (b.isBothMode() || b.isVideoMode()) return b.getVideoView();
		View v = a.getFloatingButton();
		if (isVisible(v)) return v;
		NavBarView n = a.getNavBar();
		return (isVisible(n) && n.isRight()) ? n.focusSearch() : focused;
	}

	private View focusUp(@Nullable View focused) {
		ToolBarView tb = getActivity().getToolBar();
		if (isVisible(tb)) return tb.focusSearch();

		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, list.size() - 1) : focused;
	}

	private View focusDown(@Nullable View focused) {
		MainActivityDelegate a = getActivity();
		ControlPanelView p = a.getControlPanel();
		if (isVisible(p)) return p.focusSearch();

		NavBarView n = a.getNavBar();
		if (isVisible(n) && n.isBottom()) return n.focusSearch();

		List<MediaItemWrapper> list = getAdapter().getList();
		return ((list != null) && !list.isEmpty()) ? focusTo(focused, list, 0) : focused;
	}

	@Nullable
	public MediaItemView focusTo(Item i) {
		List<MediaItemWrapper> list = getAdapter().getList();
		int pos = 0;
		for (MediaItemWrapper w : list) {
			if (i == w.getItem()) {
				View focus = focusTo(null, w, pos);
				if (focus != null) focus.requestFocus();
				return (MediaItemView) focus;
			}
			pos++;
		}
		return null;
	}

	public View focusTo(@Nullable View focused, List<MediaItemWrapper> list, int pos) {
		if (pos < 0) return focusUp(focused);
		if (pos >= list.size()) return focusDown(focused);
		return focusTo(focused, list.get(pos), pos);
	}

	private View focusTo(@Nullable View focused, MediaItemWrapper w, int pos) {
		MediaItemViewHolder h = w.getViewHolder();

		if ((h != null) && h.isAttached() && (h.getItemWrapper() == w)) {
			super.scrollToPosition(pos);
			return h.getItemView();
		} else {
			focusReq = pos;
			super.scrollToPosition(pos);
			return focused;
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		MainActivityDelegate a = getActivity();

		if (MainActivityPrefs.hasGridViewPref(a, prefs)) {
			configure(getContext().getResources().getConfiguration());
			MediaLibFragment f = a.getActiveMediaLibFragment();

			if ((f != null) && (f.getView() == this)) {
				MediaLib.BrowsableItem i = getAdapter().getParent();

				if (i != null) {
					i.getLastPlayedItem().onSuccess(pi -> {
						if (pi != null) f.revealItem(pi);
					});
				}
			}
		}
	}

	@NonNull
	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private int indexOf(List<MediaItemWrapper> list, View v) {
		for (int i = 0, n = list.size(); i < n; i++) {
			MediaItemViewHolder h = list.get(i).getViewHolder();
			if ((h != null) && (h.getItemView() == v) && h.isAttached()) return i;
		}
		return -1;
	}
}
