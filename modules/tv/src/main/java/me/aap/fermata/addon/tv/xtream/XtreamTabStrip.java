package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * The tabs of the IPTV screen. They keep their full size while they fit; on a narrow screen the
 * chips get tighter and the text smaller, step by step, so that every tab stays whole instead of
 * the last one being cut by the buttons beside the strip.
 */
public final class XtreamTabStrip extends LinearLayout {
	/** Horizontal padding (dp), text size (sp) and gap (dp), from roomy to compact. */
	private static final float[][] STEPS = {
			{16, 15, 8}, {13, 14, 6}, {10, 13, 6}, {8, 12, 4}, {6, 11, 4}
	};

	public XtreamTabStrip(Context ctx) {
		super(ctx);
	}

	public XtreamTabStrip(Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
			apply(STEPS[fit(MeasureSpec.getSize(widthSpec))]);
		}
		super.onMeasure(widthSpec, heightSpec);
	}

	/**
	 * The first step whose tabs fit the width. Computed from the text widths, without touching the
	 * views: changing them during the measure would ask for another layout pass.
	 */
	private int fit(int available) {
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float d = dm.density;
		Paint p = null;

		for (int i = 0; i < STEPS.length - 1; i++) {
			float[] step = STEPS[i];
			float w = 0;
			int n = getChildCount();

			for (int c = 0; c < n; c++) {
				if (!(getChildAt(c) instanceof TextView t)) continue;
				if (p == null) p = new Paint(t.getPaint());
				p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, step[1], dm));
				w += p.measureText(t.getText().toString()) + 2 * step[0] * d;
				if (c < n - 1) w += step[2] * d;
			}

			if (w <= available) return i;
		}

		return STEPS.length - 1;
	}

	/** Changes only what differs, so a layout with the same step settles at once. */
	private void apply(float[] step) {
		float d = getResources().getDisplayMetrics().density;
		int pad = Math.round(step[0] * d);
		int gap = Math.round(step[2] * d);

		for (int i = 0, n = getChildCount(); i < n; i++) {
			View c = getChildAt(i);
			if (c instanceof TextView t) {
				if ((t.getPaddingStart() != pad) || (t.getPaddingEnd() != pad)) {
					t.setPaddingRelative(pad, t.getPaddingTop(), pad, t.getPaddingBottom());
				}
				t.setTextSize(TypedValue.COMPLEX_UNIT_SP, step[1]);
			}
			int end = (i == n - 1) ? 0 : gap;
			if ((c.getLayoutParams() instanceof MarginLayoutParams m) && (m.getMarginEnd() != end)) {
				m.setMarginEnd(end);
			}
		}
	}
}
