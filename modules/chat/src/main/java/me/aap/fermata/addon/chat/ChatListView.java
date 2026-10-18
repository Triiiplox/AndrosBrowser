package me.aap.fermata.addon.chat;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.regex.Pattern;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;

/**
 * The conversation, as chat apps show it: the user's messages in a bubble on the right, the
 * answers as plain text on the left, and a "…" while an answer is on its way. A long press copies
 * a message; links open.
 */
public class ChatListView extends RecyclerView {
	static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
	private static final int USER = 0;
	private static final int ASSISTANT = 1;
	private static final int WAITING = 2;
	private final int textColor;
	private final int secondaryColor;
	private final int bubbleColor;
	private float scale;
	private boolean waiting;

	public ChatListView(@NonNull Context ctx) {
		super(ctx);
		var a = MainActivityDelegate.get(ctx);
		scale = a.getPrefs().getTextIconSizePref(a);
		textColor = color(ctx, android.R.attr.textColorPrimary, Color.WHITE);
		secondaryColor = color(ctx, android.R.attr.textColorSecondary, Color.GRAY);
		// A light veil of the text color: a neutral bubble on any theme, light or dark
		bubbleColor = (textColor & 0x00FFFFFF) | 0x24000000;
		int pad = toIntPx(ctx, 16);
		setPadding(pad, toIntPx(ctx, 8), pad, toIntPx(ctx, 8));
		setClipToPadding(false);
		LinearLayoutManager lm = new LinearLayoutManager(ctx);
		lm.setStackFromEnd(true);
		setLayoutManager(lm);
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setAdapter(new ChatListAdapter());
	}

	@Nullable
	@Override
	public ChatListAdapter getAdapter() {
		return (ChatListAdapter) super.getAdapter();
	}

	void scale(float scale) {
		this.scale = scale;
		ChatListAdapter a = getAdapter();
		if (a != null) a.notifyDataSetChanged();
	}

	/** Shows or hides the "…" of an answer on its way, after the last message. */
	void setWaiting(boolean waiting) {
		if (this.waiting == waiting) return;
		this.waiting = waiting;
		ChatListAdapter a = getAdapter();
		if (a == null) return;
		int pos = ChatGpt.getInstance().getMessages().size();
		if (waiting) {
			a.notifyItemInserted(pos);
			scrollToPosition(pos);
		} else {
			a.notifyItemRemoved(pos);
		}
	}

	private static int color(Context ctx, int attr, int fallback) {
		TypedValue v = new TypedValue();
		if (!ctx.getTheme().resolveAttribute(attr, v, true)) return fallback;
		if (v.resourceId != 0) {
			try {
				return ctx.getColorStateList(v.resourceId).getDefaultColor();
			} catch (Exception ignore) {
				return fallback;
			}
		}
		return v.data;
	}

	class ChatListAdapter extends Adapter<Holder> {

		@Override
		public int getItemViewType(int position) {
			var messages = ChatGpt.getInstance().getMessages();
			if (position >= messages.size()) return WAITING;
			return (messages.get(position).role == ChatGpt.Role.ASSISTANT) ? ASSISTANT : USER;
		}

		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			Context ctx = getContext();
			FrameLayout row = new FrameLayout(ctx);
			row.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			TextView t = new TextView(ctx);
			FrameLayout.LayoutParams lp;
			int v = toIntPx(ctx, 6);

			if (viewType == USER) {
				lp = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END);
				GradientDrawable bg = new GradientDrawable();
				bg.setColor(bubbleColor);
				bg.setCornerRadius(toIntPx(ctx, 20));
				t.setBackground(bg);
				t.setPadding(toIntPx(ctx, 16), toIntPx(ctx, 10), toIntPx(ctx, 16), toIntPx(ctx, 10));
				t.setTextColor(textColor);
				lp.setMargins(toIntPx(ctx, 48), v, 0, v);
			} else {
				lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.START);
				t.setPadding(0, toIntPx(ctx, 4), 0, toIntPx(ctx, 4));
				t.setTextColor((viewType == WAITING) ? secondaryColor : textColor);
				t.setLineSpacing(0, 1.2f);
				lp.setMargins(0, v, 0, v);
			}

			t.setLinkTextColor(textColor);
			row.addView(t, lp);
			return new Holder(row, t);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int position) {
			TextView text = holder.text;
			text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * scale);

			if (getItemViewType(position) == WAITING) {
				text.setText("•••");
				text.setMovementMethod(null);
				text.setOnLongClickListener(null);
				return;
			}

			var msg = ChatGpt.getInstance().getMessages().get(position);
			var s = new SpannableString(msg.content);
			boolean links = false;

			for (var m = URL_PATTERN.matcher(msg.content); m.find(); ) {
				s.setSpan(new URLSpan(m.group()), m.start(), m.end(), SPAN_EXCLUSIVE_EXCLUSIVE);
				links = true;
			}

			text.setMovementMethod(links ? LinkMovementMethod.getInstance() : null);
			text.setOnLongClickListener(t -> {
				UiUtils.addToClipboard(getContext(), msg.role.toString(), msg.content);
				return true;
			});
			text.setText(s);
		}

		@Override
		public int getItemCount() {
			return ChatGpt.getInstance().getMessages().size() + (waiting ? 1 : 0);
		}

		// A message lands before the "…", which moves down one row
		void messageAdded(int index) {
			notifyItemInserted(index);
			scrollToPosition(getItemCount() - 1);
		}
	}

	private static final class Holder extends ViewHolder {
		final TextView text;

		Holder(@NonNull View itemView, TextView text) {
			super(itemView);
			this.text = text;
		}
	}
}
