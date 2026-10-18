package me.aap.utils.pref;

import static me.aap.utils.ui.UiUtils.toPx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The rows of a preference list as one group, as in the settings of current phones: a rounded
 * surface behind all of them, a hairline between two rows, and some room around the group. The
 * rows themselves are flat (see {@link PreferenceViewAdapter#setRowStyle}).
 */
public class PreferenceGroupDecoration extends RecyclerView.ItemDecoration {
	private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint divider = new Paint();
	private final Path path = new Path();
	private final RectF bounds = new RectF();
	private final float radius;
	private final float dividerInset;
	private final int margin;

	public PreferenceGroupDecoration(Context ctx) {
		int text = color(ctx, android.R.attr.textColorPrimary);
		// Veils of the text color: they suit any theme, dark or light
		surface.setColor((text & 0x00FFFFFF) | 0x12000000);
		divider.setColor((text & 0x00FFFFFF) | 0x1F000000);
		divider.setStrokeWidth(Math.max(1f, toPx(ctx, 1) / 2f));
		radius = toPx(ctx, 20);
		dividerInset = toPx(ctx, 16);
		margin = (int) toPx(ctx, 12);
	}

	@Override
	public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent,
														 @NonNull RecyclerView.State state) {
		int pos = parent.getChildAdapterPosition(view);
		int last = state.getItemCount() - 1;
		out.set(margin, (pos == 0) ? margin : 0, margin, (pos == last) ? margin : 0);
	}

	@Override
	public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent,
										 @NonNull RecyclerView.State state) {
		int n = parent.getChildCount();
		if (n == 0) return;
		int last = state.getItemCount() - 1;
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		boolean first = false;
		boolean end = false;

		for (int i = 0; i < n; i++) {
			View v = parent.getChildAt(i);
			int pos = parent.getChildAdapterPosition(v);
			if (pos == RecyclerView.NO_POSITION) continue;
			top = Math.min(top, v.getTop() + v.getTranslationY());
			bottom = Math.max(bottom, v.getBottom() + v.getTranslationY());
			if (pos == 0) first = true;
			if (pos == last) end = true;
		}
		if (top >= bottom) return;

		// Round only the real ends of the group: a row cut by the edge of the list goes on below it
		float left = margin;
		float right = parent.getWidth() - margin;
		float rt = first ? radius : 0;
		float rb = end ? radius : 0;
		bounds.set(left, first ? top : top - radius, right, end ? bottom : bottom + radius);
		path.reset();
		path.addRoundRect(bounds, new float[]{rt, rt, rt, rt, rb, rb, rb, rb}, Path.Direction.CW);
		c.drawPath(path, surface);

		for (int i = 0; i < n; i++) {
			View v = parent.getChildAt(i);
			int pos = parent.getChildAdapterPosition(v);
			if ((pos == RecyclerView.NO_POSITION) || (pos == last)) continue;
			float y = v.getBottom() + v.getTranslationY();
			c.drawLine(left + dividerInset, y, right - dividerInset, y, divider);
		}
	}

	private static int color(Context ctx, int attr) {
		TypedValue v = new TypedValue();
		if (!ctx.getTheme().resolveAttribute(attr, v, true)) return 0xFFFFFFFF;
		if (v.resourceId != 0) {
			try {
				return ctx.getColorStateList(v.resourceId).getDefaultColor();
			} catch (Exception ignore) {
				return 0xFFFFFFFF;
			}
		}
		return v.data;
	}
}
