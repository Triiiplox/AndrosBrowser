package me.aap.fermata.addon.tv;

import android.view.View;
import android.widget.ImageButton;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.ToolBarMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Adds an "add IPTV list" button to the TV tool bar: the floating button and the menu item are
 * easy to miss.
 */
class TvToolBarMediator implements ToolBarView.Mediator {
	static final ToolBarView.Mediator instance =
			ToolBarMediator.instance.join(new TvToolBarMediator());

	private TvToolBarMediator() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		ImageButton b = addButton(tb, R.drawable.tv_add, TvToolBarMediator::onAddClick,
				R.id.tv_add_source);
		CharSequence label = tb.getContext().getString(R.string.add_tv_source);
		b.setContentDescription(label);
		b.setTooltipText(label);
	}

	private static void onAddClick(View v) {
		ActivityFragment f = MainActivityDelegate.get(v.getContext()).getActiveFragment();
		if (f instanceof TvFragment) ((TvFragment) f).addSource();
	}
}
