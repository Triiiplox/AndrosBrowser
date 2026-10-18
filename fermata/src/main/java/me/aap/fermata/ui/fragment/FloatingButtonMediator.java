package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.UiUtils.isVisible;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.FloatingButton.Mediator.BackMenu;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class FloatingButtonMediator implements BackMenu {
	public static final FloatingButtonMediator instance = new FloatingButtonMediator();

	@Override
	public void enable(FloatingButton fb, ActivityFragment f) {
		BackMenu.super.enable(fb, f);
		suppress(fb, f);
	}

	@Override
	public void onActivityEvent(FloatingButton fb, ActivityDelegate a, long e) {
		BackMenu.super.onActivityEvent(fb, a, e);
		// The screens share this mediator: enable() is not called again from one to the next
		if ((e & FRAGMENT_CHANGED) != 0) suppress(fb, a.getActiveFragment());
	}

	/*
	 * On the phone the button only repeated the system Back and the nav bar's Menu, over the
	 * content: it shows there only to add a folder. In the car it is the only Back on the screen,
	 * but in the settings, whose tool bar has one, and where it covered the arrows of the rows.
	 */
	private void suppress(FloatingButton fb, ActivityFragment f) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		boolean show = a.isCarActivity() ? !(f instanceof SettingsFragment) : isAddFolderEnabled(f);
		fb.setSuppressed(!show);
	}

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		if (a.isVideoMode() || !a.isRootPage()) return getBackIcon();
		if (isAddFolderEnabled(a.getActiveFragment())) return R.drawable.add_folder;
		return getMenuIcon();
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolder();
			else showMenu((FloatingButton) v);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		if (a.getPrefs().getVoiceControlFBPref()) {
			a.startVoiceAssistant();
			return true;
		}
		ActivityFragment f = a.getActiveFragment();
		if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolderPicker();
		else showMenu((FloatingButton) v);
		return true;
	}

	@Nullable
	@Override
	public View focusSearch(FloatingButton fb, int direction) {
		if (direction == FOCUS_RIGHT) {
			Context ctx = fb.getContext();
			NavBarView n = MainActivityDelegate.get(ctx).getNavBar();
			return (isVisible(n) && n.isRight()) ? n.focusSearch() :
					MediaItemListView.focusSearchLast(ctx, fb);
		} else if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(fb.getContext(), fb);
		} else if (direction == FOCUS_UP) {
			var a = MainActivityDelegate.get(fb.getContext());
			if (a.getActiveMenu() instanceof View v) return v.findFocus();
			var tb = a.getToolBar();
			if (isVisible(tb)) return tb.focusSearch();
		}

		return null;
	}

	private boolean isAddFolderEnabled(ActivityFragment f) {
		return ((f instanceof FoldersFragment) && f.isRootPage());
	}
}
