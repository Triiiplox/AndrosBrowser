package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;

/**
 * Indeterminate progress of the Modern theme: the emblem breathes softly and a light glint sweeps
 * across its chrome, like on a car badge. The emblem keeps its aspect ratio within the bounds.
 */
public class LogoLoadingDrawable extends Drawable implements Animatable {
	private static final long PERIOD = 1800;
	// The glint runs over this part of the period, while the emblem is at its brightest
	private static final float GLINT_START = 0.2f;
	private static final float GLINT_LENGTH = 0.5f;
	private final Bitmap logo;
	private final Rect src;
	private final RectF dst = new RectF();
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Matrix matrix = new Matrix();
	private float glintWidth;
	private long start;
	private boolean running;
	private int alpha = 255;

	public LogoLoadingDrawable(Context ctx) {
		logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo_emblem);
		src = new Rect(0, 0, logo.getWidth(), logo.getHeight());
		glint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
	}

	@Override
	protected void onBoundsChange(@NonNull Rect b) {
		float scale = Math.min(b.width() / (float) src.width(), b.height() / (float) src.height());
		float w = src.width() * scale;
		float h = src.height() * scale;
		dst.set(b.exactCenterX() - w / 2, b.exactCenterY() - h / 2, b.exactCenterX() + w / 2,
				b.exactCenterY() + h / 2);
		glintWidth = w * 0.35f;
		glint.setShader(new LinearGradient(-glintWidth / 2, 0, glintWidth / 2, 0,
				new int[]{0x00FFFFFF, 0xA6FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (dst.isEmpty()) return;
		float p = running ? ((SystemClock.uptimeMillis() - start) % PERIOD) / (float) PERIOD : 0.5f;
		// 0 -> 1 -> 0 over the period, eased at both ends
		float breath = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * p));
		float scale = 0.94f + 0.06f * breath;
		int a = (int) (alpha * (0.7f + 0.3f * breath));

		int save = canvas.save();
		canvas.scale(scale, scale, dst.centerX(), dst.centerY());
		canvas.saveLayer(dst, null);
		paint.setAlpha(a);
		canvas.drawBitmap(logo, src, dst, paint);

		float g = (p - GLINT_START) / GLINT_LENGTH;
		if (running && (g > 0f) && (g < 1f)) {
			g = g * g * (3 - 2 * g);
			float x = dst.left - glintWidth + (dst.width() + 2 * glintWidth) * g;
			matrix.setRotate(20f);
			matrix.postTranslate(x, dst.centerY());
			glint.getShader().setLocalMatrix(matrix);
			glint.setAlpha(a);
			canvas.drawRect(dst, glint);
		}

		canvas.restoreToCount(save);
		if (running && isVisible()) invalidateSelf();
	}

	@Override
	public void start() {
		if (running) return;
		running = true;
		start = SystemClock.uptimeMillis();
		invalidateSelf();
	}

	@Override
	public void stop() {
		if (!running) return;
		running = false;
		invalidateSelf();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getIntrinsicWidth() {
		return src.width();
	}

	@Override
	public int getIntrinsicHeight() {
		return src.height();
	}

	@Override
	public void setAlpha(int alpha) {
		this.alpha = alpha;
		invalidateSelf();
	}

	@Override
	public int getAlpha() {
		return alpha;
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
		invalidateSelf();
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
