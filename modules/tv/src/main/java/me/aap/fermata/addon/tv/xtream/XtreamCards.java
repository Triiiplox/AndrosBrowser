package me.aap.fermata.addon.tv.xtream;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.addon.tv.R;

/**
 * The cards of a row or of the search results: posters for movies and series, logos for live
 * channels, wide pictures with the watched part for "continue watching".
 */
final class XtreamCards extends RecyclerView.Adapter<XtreamCards.Holder> {
	static final int POSTER = 0;
	static final int CHANNEL = 1;
	static final int WIDE = 2;
	private final Listener listener;
	private List<Entry> entries = Collections.emptyList();
	private List<UserLibrary.Record> records = Collections.emptyList();
	private boolean wideRecords;

	XtreamCards(Listener listener) {
		this.listener = listener;
	}

	void setEntries(List<Entry> entries) {
		this.entries = entries;
		this.records = Collections.emptyList();
		notifyDataSetChanged();
	}

	/** @param wide continue watching: wide cards with the progress */
	void setRecords(List<UserLibrary.Record> records, boolean wide) {
		this.records = records;
		this.entries = Collections.emptyList();
		this.wideRecords = wide;
		notifyDataSetChanged();
	}

	@Override
	public int getItemCount() {
		return entries.isEmpty() ? records.size() : entries.size();
	}

	@Override
	public int getItemViewType(int position) {
		if (!entries.isEmpty()) return (entries.get(position).kind == Kind.LIVE) ? CHANNEL : POSTER;
		UserLibrary.Record r = records.get(position);
		if (r.kind == Kind.LIVE) return CHANNEL;
		return wideRecords ? WIDE : POSTER;
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
		int layout = switch (type) {
			case CHANNEL -> R.layout.xtream_card_channel;
			case WIDE -> R.layout.xtream_card_wide;
			default -> R.layout.xtream_card_poster;
		};
		View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
		return new Holder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull Holder h, int position) {
		if (!entries.isEmpty()) {
			Entry e = entries.get(position);
			h.entry = e;
			h.record = null;
			bind(h, e.name, null, e.image, null);
		} else {
			UserLibrary.Record r = records.get(position);
			h.entry = null;
			h.record = r;
			String name = r.title;
			String sub = (r.kind == Kind.EPISODE) ? r.subtitle : XtreamUi.left(h.itemView.getContext(), r);
			if (wideRecords && (r.kind == Kind.MOVIE)) sub = XtreamUi.left(h.itemView.getContext(), r);
			bind(h, name, sub, r.image, r);
		}
	}

	private static void bind(Holder h, String name, @Nullable String sub, @Nullable String image,
													 @Nullable UserLibrary.Record r) {
		h.title.setText(name);
		if (h.name != null) h.name.setText(name);
		if (h.sub != null) XtreamUi.text(h.sub, sub);
		if (h.progress != null) XtreamUi.progress(h.progress, r);
		h.itemView.setContentDescription(name);
		XtreamUi.image(h.image, image, h.title, false);
	}

	interface Listener {
		void onEntry(Entry e);

		void onRecord(UserLibrary.Record r);

		/** A long press: e.g. add to my list. */
		void onLongPress(@Nullable Entry e, @Nullable UserLibrary.Record r);
	}

	final class Holder extends RecyclerView.ViewHolder {
		final ImageView image;
		final TextView title;
		@Nullable
		final TextView name;
		@Nullable
		final TextView sub;
		@Nullable
		final ProgressBar progress;
		Entry entry;
		UserLibrary.Record record;

		Holder(View v) {
			super(v);
			image = v.findViewById(R.id.xtream_card_image);
			title = v.findViewById(R.id.xtream_card_title);
			name = v.findViewById(R.id.xtream_card_name);
			sub = v.findViewById(R.id.xtream_card_sub);
			progress = v.findViewById(R.id.xtream_card_progress);
			v.setOnClickListener(x -> {
				if (entry != null) listener.onEntry(entry);
				else if (record != null) listener.onRecord(record);
			});
			v.setOnLongClickListener(x -> {
				listener.onLongPress(entry, record);
				return true;
			});
		}
	}
}
