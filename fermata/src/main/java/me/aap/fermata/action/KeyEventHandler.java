package me.aap.fermata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class KeyEventHandler {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static Worker worker;
	private static TapCounter taps;

	public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event,
																			 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(cb, null, event, defaultHandler);
	}

	public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event,
																			 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(activity.getMediaSessionCallback(), activity, event, defaultHandler);
	}

	private static boolean handleKeyEvent(MediaSessionCallback cb,
																				@Nullable MainActivityDelegate activity, KeyEvent event,
																				IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		// A held key repeats every ~50ms: only its first event goes to the release log
		if (event.getRepeatCount() == 0) Log.i((activity == null) ? "Media: " : "Activity: ", event);
		else Log.d((activity == null) ? "Media: " : "Activity: ", event);

		if (event.isCanceled()) {
			worker = null;
			taps = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		if (taps != null) {
			if (taps.handle(event)) return true;
			taps = null;
		}

		if (worker != null) {
			if (worker.handle(event)) return true;
			worker = null;
			return false;
		}

		var code = event.getKeyCode();
		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);

		if (!k.isMedia() && (activity != null) && (activity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(code, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(code, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			Log.i(k, " key double click");
			performAction(dblClickAction, cb, activity, uptimeMillis());
			return true;
		}
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

		var clickAction = k.getClickAction();
		if (clickAction == null) return defaultHandler.apply(code, event);
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) return defaultHandler.apply(code, event);

		// A key with an action on three presses counts them all before acting
		var tripleClickAction = k.getTripleClickAction();
		if ((tripleClickAction != null) && (tripleClickAction != Action.NONE)) {
			taps = new TapCounter(cb, activity, k, clickAction, dblClickAction, longClickAction,
					tripleClickAction);
			return true;
		}

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			Log.d(k, " key click");
			performAction(clickAction, cb, activity, uptimeMillis());
			return true;
		}

		worker = new Worker(cb, activity, k, clickAction, dblClickAction, longClickAction);
		return true;
	}

	private static void performAction(Action action, MediaSessionCallback cb,
																		@Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		Log.i("Performing action ", action);
		action.getHandler().handle(cb, activity, timestamp);
	}

	/*
	 * Counts the presses of a key that has an action on three of them: each release waits a moment
	 * for the next press, then the count decides (one, two, three or more). A key held down does
	 * its long press action. Its cost: one press acts a moment later than without the count.
	 */
	private static final class TapCounter implements Runnable {
		private final MediaSessionCallback cb;
		@Nullable
		private final MainActivityDelegate activity;
		private final Key key;
		private final Action[] actions;
		private final Action longClickAction;
		private final Runnable longPress = this::longPress;
		private int presses = 1;
		private boolean down = true;
		private boolean held;

		TapCounter(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
							 Action click, Action dbl, Action longClick, Action triple) {
			this.cb = cb;
			this.activity = activity;
			this.key = key;
			actions = new Action[]{click, dbl, triple};
			longClickAction = longClick;
			handler().postDelayed(longPress, LONG_CLICK_INTERVAL);
		}

		boolean handle(KeyEvent e) {
			if (e.getKeyCode() != key.getCode()) return false;
			if (e.getAction() == ACTION_DOWN) {
				if (e.getRepeatCount() > 0) return true;
				handler().removeCallbacks(this);
				presses++;
				down = true;
				handler().postDelayed(longPress, LONG_CLICK_INTERVAL);
			} else if (e.getAction() == ACTION_UP) {
				down = false;
				handler().removeCallbacks(longPress);
				if (held) taps = null;
				else handler().postDelayed(this, DBL_CLICK_INTERVAL);
			}
			return true;
		}

		@Override
		public void run() {
			if ((taps != this) || down) return;
			taps = null;
			Action a = actions[Math.min(presses, actions.length) - 1];
			// Two presses with nothing for them: two clicks
			if ((presses == 2) && (a == Action.NONE)) {
				Log.i(key, " key 2 clicks");
				performAction(actions[0], cb, activity, uptimeMillis());
				performAction(actions[0], cb, activity, uptimeMillis());
				return;
			}
			Log.i(key, " key pressed ", presses, " times");
			performAction(a, cb, activity, uptimeMillis());
		}

		private void longPress() {
			if ((taps != this) || !down || (presses != 1)) return;
			held = true;
			Log.i(key, " key long click");
			if (longClickAction != Action.NONE) performAction(longClickAction, cb, activity, uptimeMillis());
			taps = this;
		}

		private android.os.Handler handler() {
			return (activity == null) ? cb.getHandler() : activity.getHandler();
		}
	}

	private static final class Worker implements Runnable {
		private final MediaSessionCallback cb;
		@Nullable
		private final MainActivityDelegate activity;
		private final Key key;
		private final Action clickAction;
		private final Action dblClickAction;
		private final Action longClickAction;
		private final long time;
		private long longClickTime;
		private boolean up;


		Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
					 Action clickAction, Action dblClickAction, Action longClickAction) {
			this.cb = cb;
			this.activity = activity;
			this.key = key;
			this.clickAction = clickAction;
			this.dblClickAction = dblClickAction;
			this.longClickAction = longClickAction;
			time = longClickTime = uptimeMillis();
			sched(DBL_CLICK_INTERVAL);
		}

		@Override
		public void run() {
			if (worker != this) return;
			if (up) {
				Log.d(key, " key click");
				handle(clickAction);
				return;
			}

			long now = uptimeMillis();
			long diff = now - longClickTime;

			if (diff < LONG_CLICK_INTERVAL) {
				sched(LONG_CLICK_INTERVAL - diff);
			} else if (diff > 15000) { // Key UP not received?
				worker = null;
			} else {
				longClickTime = time;
				Log.i(key, " key long click");
				handle(longClickAction);
				worker = this;
				sched(LONG_CLICK_INTERVAL);
			}
		}

		boolean handle(KeyEvent e) {
			if (e.getKeyCode() != key.getCode()) return false;

			switch (e.getAction()) {
				case ACTION_DOWN -> {
					if (!up) {
						if ((longClickAction == clickAction) || (longClickAction == Action.NONE)) {
							Log.d(key, " key click");
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;

					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							Log.i(key, " key double click");
							handle(dblClickAction);
						} else if (dblClickAction == clickAction) {
							Log.d(key, " key click");
							handle(clickAction);
						} else {
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						worker = null;
					} else {
						worker = null;
						if (longClickTime == time) {
							Log.d(key, " key click");
							handle(clickAction);
						}
					}

					return true;
				}
				case ACTION_MULTIPLE -> {
					Log.i(key, " key double click");
					handle(dblClickAction);
					return true;
				}
			}
			return false;
		}

		private void handle(Action action) {
			performAction(action, cb, activity, time);
		}

		private void sched(long delay) {
			var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
			handler.postDelayed(this, delay);
		}
	}
}
