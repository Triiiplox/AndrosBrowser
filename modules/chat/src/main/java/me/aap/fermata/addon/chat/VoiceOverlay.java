package me.aap.fermata.addon.chat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The voice mode: a dark screen with a moving orb in the middle, what is heard or answered under
 * it, and two buttons at the bottom, mute and close. The orb breathes while listening, follows the
 * voice level, turns faster while the answer is on its way and pulses while it is spoken.
 */
final class VoiceOverlay extends FrameLayout {
	enum State {LISTENING, THINKING, SPEAKING, PAUSED}

	interface Listener {
		void onMuteToggled();

		void onClose();
	}

	private final Orb orb;
	private final TextView caption;
	private final ImageButton mute;

	VoiceOverlay(Context ctx, Listener listener) {
		super(ctx);
		setBackgroundColor(Color.BLACK);
		setClickable(true); // The conversation below does not get the touches
		setVisibility(GONE);

		LinearLayout center = new LinearLayout(ctx);
		center.setOrientation(LinearLayout.VERTICAL);
		center.setGravity(Gravity.CENTER_HORIZONTAL);
		orb = new Orb(ctx);
		int size = Math.min(ctx.getResources().getDisplayMetrics().widthPixels,
				ctx.getResources().getDisplayMetrics().heightPixels) / 2;
		center.addView(orb, new LinearLayout.LayoutParams(size, size));
		caption = new TextView(ctx);
		caption.setTextColor(0xCCFFFFFF);
		caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		caption.setGravity(Gravity.CENTER);
		caption.setMaxLines(4);
		caption.setEllipsize(TextUtils.TruncateAt.END);
		int pad = toIntPx(ctx, 32);
		caption.setPadding(pad, toIntPx(ctx, 28), pad, 0);
		center.addView(caption, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		addView(center, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER));

		LinearLayout bottom = new LinearLayout(ctx);
		bottom.setOrientation(LinearLayout.HORIZONTAL);
		bottom.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
		int bp = toIntPx(ctx, 16);
		bottom.setPadding(bp, bp, bp, toIntPx(ctx, 20));
		mute = roundButton(ctx, R.drawable.chat_mic, 0xFF2A2A2A, Color.WHITE);
		mute.setContentDescription(ctx.getString(R.string.chat_mute));
		mute.setOnClickListener(v -> listener.onMuteToggled());
		ImageButton close = roundButton(ctx, R.drawable.chat_close, Color.WHITE, Color.BLACK);
		close.setContentDescription(ctx.getString(R.string.chat_close_voice));
		close.setOnClickListener(v -> listener.onClose());
		int b = toIntPx(ctx, 56);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(b, b);
		bottom.addView(mute, lp);
		lp = new LinearLayout.LayoutParams(b, b);
		lp.setMarginStart(toIntPx(ctx, 12));
		bottom.addView(close, lp);
		addView(bottom, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM));
	}

	void show() {
		setVisibility(VISIBLE);
		orb.start();
	}

	void hide() {
		setVisibility(GONE);
		orb.stop();
		caption.setText("");
	}

	void setState(State state) {
		orb.state = state;
		Context ctx = getContext();
		mute.setImageResource((state == State.PAUSED) ? R.drawable.chat_mic_off : R.drawable.chat_mic);
		switch (state) {
			case LISTENING -> caption.setText(R.string.chat_listening);
			case THINKING -> {
			}
			case SPEAKING -> {
			}
			case PAUSED -> caption.setText(ctx.getString(R.string.chat_paused));
		}
	}

	void setCaption(CharSequence text) {
		caption.setText(text);
	}

	/** The voice level of the recognizer, in dB as it gives it (about -2 to 10). */
	void setLevel(float rmsDb) {
		orb.target = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
	}

	private static ImageButton roundButton(Context ctx, int icon, int bg, int fg) {
		ImageButton b = new ImageButton(ctx);
		GradientDrawable d = new GradientDrawable();
		d.setShape(GradientDrawable.OVAL);
		d.setColor(bg);
		b.setBackground(d);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(fg));
		b.setScaleType(ImageView.ScaleType.CENTER);
		return b;
	}

	private static final class Orb extends View {
		private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Matrix matrix = new Matrix();
		private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
		private LinearGradient gradient;
		private RadialGradient highlight;
		State state = State.LISTENING;
		float target;
		private float level;
		private float angle;
		private long last;

		Orb(Context ctx) {
			super(ctx);
			clock.setDuration(1000);
			clock.setRepeatCount(ValueAnimator.INFINITE);
			clock.setInterpolator(new LinearInterpolator());
			clock.addUpdateListener(a -> invalidate());
		}

		void start() {
			last = 0;
			if (!clock.isStarted()) clock.start();
		}

		void stop() {
			clock.cancel();
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			clock.cancel();
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			float r = Math.min(w, h) / 2f;
			gradient = new LinearGradient(-r, -r, r, r,
					new int[]{0xFF3B6BEA, 0xFF4F7FF0, 0xFFE9EEFF, 0xFFB9CAF8},
					new float[]{0f, 0.45f, 0.62f, 1f}, Shader.TileMode.CLAMP);
			highlight = new RadialGradient(-r * 0.3f, -r * 0.35f, r * 0.9f, 0x55FFFFFF, 0x00FFFFFF,
					Shader.TileMode.CLAMP);
			fill.setShader(gradient);
			glow.setShader(highlight);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			if (gradient == null) return;
			long now = System.currentTimeMillis();
			float dt = (last == 0) ? 0f : Math.min(0.1f, (now - last) / 1000f);
			last = now;

			float speed = switch (state) {
				case LISTENING -> 18f;
				case THINKING -> 120f;
				case SPEAKING -> 45f;
				case PAUSED -> 4f;
			};
			angle = (angle + speed * dt) % 360f;
			float wanted = switch (state) {
				case LISTENING -> target;
				case SPEAKING -> 0.35f + 0.25f * (float) Math.sin(now / 140.0);
				default -> 0f;
			};
			level += (wanted - level) * Math.min(1f, dt * 8f);
			float breath = 0.5f + 0.5f * (float) Math.sin(now / 900.0);

			float cx = getWidth() / 2f;
			float cy = getHeight() / 2f;
			float max = Math.min(cx, cy);
			float r = max * (0.82f + 0.05f * breath + 0.13f * level);
			if (state == State.PAUSED) r = max * 0.78f;

			matrix.reset();
			matrix.setRotate(angle);
			matrix.postTranslate(cx, cy);
			gradient.setLocalMatrix(matrix);
			matrix.reset();
			matrix.setTranslate(cx, cy);
			highlight.setLocalMatrix(matrix);
			fill.setAlpha((state == State.PAUSED) ? 140 : 255);
			canvas.drawCircle(cx, cy, r, fill);
			canvas.drawCircle(cx, cy, r, glow);
		}
	}
}
