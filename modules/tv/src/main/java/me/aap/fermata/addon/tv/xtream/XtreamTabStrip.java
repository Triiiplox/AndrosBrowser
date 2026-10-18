package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.util.AttributeSet;
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

	private int fittedWidth = -1;
	private int fittedKey;
	private int fittedStep;

	public XtreamTabStrip(Context ctx) {
		super(ctx);
	}

	public XtreamTabStrip(Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
			int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
			int key = contentKey();
			// Tried again only when the width or the tabs change: trying sizes changes the views,
			// and doing it on every pass would ask for a layout on every frame
			if ((available != fittedWidth) || (key != fittedKey)) {
				fittedStep = STEPS.length - 1;
				for (int i = 0; i < STEPS.length; i++) {
					apply(STEPS[i]);
					if (naturalWidth() <= available) {
						fittedStep = i;
						break;
					}
				}
				fittedWidth = available;
				fittedKey = key;
			}
			apply(STEPS[fittedStep]);
		}
		super.onMeasure(widthSpec, heightSpec);
	}

	/** The width the tabs take at their own size, measured, not estimated. */
	private int naturalWidth() {
		int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		int w = 0;
		for (int i = 0, n = getChildCount(); i < n; i++) {
			View c = getChildAt(i);
			if (c.getVisibility() == GONE) continue;
			c.measure(unspecified, unspecified);
			w += c.getMeasuredWidth();
			if (c.getLayoutParams() instanceof MarginLayoutParams m) {
				w += m.getMarginStart() + m.getMarginEnd();
			}
		}
		return w;
	}

	private int contentKey() {
		int k = getChildCount();
		for (int i = 0, n = getChildCount(); i < n; i++) {
			if (getChildAt(i) instanceof TextView t) k = 31 * k + t.getText().toString().hashCode();
		}
		return k;
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
