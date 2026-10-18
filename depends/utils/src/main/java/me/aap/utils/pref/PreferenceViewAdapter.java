package me.aap.utils.pref;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.utils.R;
import me.aap.utils.app.App;
import me.aap.utils.ui.UiUtils;


/**
 * @author Andrey Pavlenko
 */
public class PreferenceViewAdapter extends RecyclerView.Adapter<PreferenceViewAdapter.PrefViewHolder> {
	private PreferenceSet set;
	private RecyclerView recyclerView;
	private int rowBackground = R.drawable.box_secondary;
	private int rowPaddingH = 5;
	private int rowPaddingV = 5;
	private int rowMargin = 1;

	public PreferenceViewAdapter(PreferenceSet set) {
		this.set = set;
	}

	/**
	 * The look of the rows: their background, padding and margin (dp). The default is a card per
	 * row; a grouped list gives the rows a transparent background and draws the group itself.
	 */
	public void setRowStyle(int background, int paddingH, int paddingV, int margin) {
		rowBackground = background;
		rowPaddingH = paddingH;
		rowPaddingV = paddingV;
		rowMargin = margin;
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		this.recyclerView = recyclerView;
	}

	@NonNull
	@Override
	public PrefViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		Context ctx = parent.getContext();
		PreferenceView v = new PreferenceView(ctx);
		RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		int margin = (int) UiUtils.toPx(ctx, rowMargin);
		int padH = (int) UiUtils.toPx(ctx, rowPaddingH);
		int padV = (int) UiUtils.toPx(ctx, rowPaddingV);
		v.setLayoutParams(lp);
		lp.setMargins(margin, margin, margin, margin);
		v.setPadding(padH, padV, padH, padV);
		v.setFocusable(true);
		v.setBackgroundResource(rowBackground);
		return new PrefViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull PrefViewHolder holder, int position) {
		holder.getPreferenceView().setPreference(this, set.preferences.get(position));
	}

	@Override
	public void onViewRecycled(@NonNull PrefViewHolder holder) {
		holder.getPreferenceView().cleanUp();
		holder.getPreferenceView().setPreference(this, null);
	}

	public void onDestroy() {
		RecyclerView view = recyclerView;
		if (view == null) return;
		for (int i = 0, n = view.getChildCount(); i < n; i++) {
			View v = view.getChildAt(i);
			if (v instanceof PreferenceView) ((PreferenceView) v).cleanUp();
		}
		view.setAdapter(null);
	}

	@Override
	public int getItemCount() {
		return set.preferences.size();
	}

	public void setPreferenceSet(PreferenceSet set) {
		PreferenceSet old = getPreferenceSet();
		if (old != null) old.setAdapter(null);

		this.set = set;
		set.setAdapter(this);
		notifyDataSetChanged();

		if ((old != null) && (recyclerView != null)) {
			int idx = set.getPreferences().indexOf(old);

			if (idx != -1) {
				App.get().getHandler().post(() -> {
					if (set != this.set) return;
					PrefViewHolder h = (PrefViewHolder) recyclerView.findViewHolderForAdapterPosition(idx);
					if (h == null) return;
					h.getPreferenceView().requestFocus();
					recyclerView.smoothScrollToPosition(idx);
				});
			}
		}
	}

	public PreferenceSet getPreferenceSet() {
		return set;
	}

	public void setSize(float scale) {
		if (recyclerView == null) return;
		Context ctx = recyclerView.getContext();
		for (int i = 0, n = recyclerView.getChildCount(); i < n; i++) {
			((PreferenceView) recyclerView.getChildAt(i)).setSize(ctx, scale);
		}
	}

	public static class PrefViewHolder extends RecyclerView.ViewHolder {

		public PrefViewHolder(@NonNull PreferenceView pref) {
			super(pref);
		}

		public PreferenceView getPreferenceView() {
			return (PreferenceView) itemView;
		}
	}
}
