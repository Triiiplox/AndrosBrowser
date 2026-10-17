package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.model.Entry;

import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.utils.app.App;

/**
 * Search over the whole catalog as you type, ignoring case and accents. The catalog is in memory,
 * so a search takes milliseconds; it still runs off the main thread, after a short pause in the
 * typing.
 */
final class XtreamSearch {
	private static final int LIMIT = 120;
	private final View view;
	private final EditText text;
	private final XtreamCards cards;
	private final TextView empty;
	private final Snapshot snapshot;
	private final XtreamRepo repo;
	private final Runnable search = this::search;
	private int seq;

	XtreamSearch(Context ctx, Snapshot snapshot, XtreamCards.Listener listener, Runnable close) {
		this.snapshot = snapshot;
		repo = XtreamRepo.get(ctx);
		view = LayoutInflater.from(ctx).inflate(R.layout.xtream_search, null);
		text = view.findViewById(R.id.xtream_search_text);
		empty = view.findViewById(R.id.xtream_search_empty);
		RecyclerView list = view.findViewById(R.id.xtream_search_results);
		float dp = ctx.getResources().getDisplayMetrics().density;
		int width = ctx.getResources().getDisplayMetrics().widthPixels;
		int span = Math.max(3, (int) (width / (112 * dp)));
		list.setLayoutManager(new GridLayoutManager(ctx, span));
		cards = new XtreamCards(listener);
		list.setAdapter(cards);
		view.findViewById(R.id.xtream_search_back).setOnClickListener(x -> close.run());
		text.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				App.get().getHandler().removeCallbacks(search);
				App.get().getHandler().postDelayed(search, 250);
			}
		});
		text.setOnEditorActionListener((v, actionId, event) -> {
			if ((actionId == EditorInfo.IME_ACTION_SEARCH) ||
					((event != null) && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))) {
				hideKeyboard();
				return true;
			}
			return false;
		});
	}

	View getView() {
		return view;
	}

	void focus() {
		text.requestFocus();
		InputMethodManager imm = (InputMethodManager) text.getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
	}

	void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) text.getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.hideSoftInputFromWindow(text.getWindowToken(), 0);
	}

	void close() {
		App.get().getHandler().removeCallbacks(search);
		hideKeyboard();
	}

	private void search() {
		String q = text.getText().toString();
		int s = ++seq;
		if (q.trim().isEmpty()) {
			cards.setEntries(java.util.Collections.emptyList());
			empty.setVisibility(View.GONE);
			return;
		}
		repo.run(() -> snapshot.search(q, LIMIT)).main().onSuccess(r -> show(s, r));
	}

	private void show(int s, List<Entry> results) {
		if (s != seq) return;
		cards.setEntries(results);
		empty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
	}
}
