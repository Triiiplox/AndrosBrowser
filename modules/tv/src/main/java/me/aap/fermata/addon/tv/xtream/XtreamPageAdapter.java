package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.Home;
import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Series;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.R;

/**
 * A page of the IPTV screen: the hero, then one horizontal row of cards per line. The rows share
 * one pool of card views, so scrolling down a long page creates few new views.
 */
final class XtreamPageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final int HERO = 0;
	private static final int ROW = 1;
	private final Controller controller;
	private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
	@Nullable
	private Entry hero;
	@Nullable
	private Snapshot snapshot;
	private List<Home.Row> rows = new ArrayList<>();

	XtreamPageAdapter(Controller controller) {
		this.controller = controller;
		pool.setMaxRecycledViews(XtreamCards.POSTER, 40);
		pool.setMaxRecycledViews(XtreamCards.CHANNEL, 30);
		pool.setMaxRecycledViews(XtreamCards.WIDE, 12);
	}

	void setPage(Snapshot s, Home.Page page) {
		snapshot = s;
		hero = page.hero;
		rows = page.rows;
		notifyDataSetChanged();
	}

	void clear() {
		hero = null;
		rows = new ArrayList<>();
		notifyDataSetChanged();
	}

	@Override
	public int getItemCount() {
		return rows.size() + ((hero != null) ? 1 : 0);
	}

	@Override
	public int getItemViewType(int position) {
		return ((hero != null) && (position == 0)) ? HERO : ROW;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
		LayoutInflater inf = LayoutInflater.from(parent.getContext());
		if (type == HERO) return new HeroHolder(inf.inflate(R.layout.xtream_hero, parent, false));
		return new RowHolder(inf.inflate(R.layout.xtream_row, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
		if (h instanceof HeroHolder hh) {
			hh.bind(hero);
		} else {
			((RowHolder) h).bind(rows.get(position - ((hero != null) ? 1 : 0)));
		}
	}

	static String rowTitle(Context ctx, Home.Row r) {
		return switch (r.type) {
			case CONTINUE -> ctx.getString(R.string.xtream_row_continue);
			case MY_LIST -> ctx.getString(R.string.xtream_row_my_list);
			case RECENT_CHANNELS -> ctx.getString(R.string.xtream_row_recent_channels);
			case NEW_MOVIES -> ctx.getString(R.string.xtream_row_new_movies);
			case NEW_SERIES -> ctx.getString(R.string.xtream_row_new_series);
			case TOP_MOVIES -> ctx.getString(R.string.xtream_row_top_movies);
			case TOP_SERIES -> ctx.getString(R.string.xtream_row_top_series);
			case CATEGORY -> (r.category != null) ? r.category.name : "";
		};
	}

	interface Controller extends XtreamCards.Listener {
		void onHeroPlay(Entry e);

		void onHeroInfo(Entry e);
	}

	private final class HeroHolder extends RecyclerView.ViewHolder {
		final ImageView image;
		final TextView title;
		final TextView meta;
		final TextView play;
		final TextView info;
		Entry entry;

		HeroHolder(View v) {
			super(v);
			image = v.findViewById(R.id.xtream_hero_image);
			title = v.findViewById(R.id.xtream_hero_title);
			meta = v.findViewById(R.id.xtream_hero_meta);
			play = v.findViewById(R.id.xtream_hero_play);
			info = v.findViewById(R.id.xtream_hero_info);
			info.setText(R.string.xtream_more_info);
			info.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
			play.setOnClickListener(x -> {
				if (entry != null) controller.onHeroPlay(entry);
			});
			info.setOnClickListener(x -> {
				if (entry != null) controller.onHeroInfo(entry);
			});
			v.setOnClickListener(x -> {
				if (entry != null) controller.onHeroInfo(entry);
			});
		}

		void bind(@Nullable Entry e) {
			entry = e;
			if (e == null) return;
			Context ctx = itemView.getContext();
			title.setText(e.name);
			Category c = (snapshot != null) && (e.categoryId != null) ?
					snapshot.category(e.kind, e.categoryId) : null;
			XtreamUi.text(meta, XtreamUi.meta(ctx, e, (c != null) ? c.name : null));
			String pic = ((e instanceof Series s) && (s.backdrop != null)) ? s.backdrop : e.image;
			XtreamUi.image(image, pic, null, true);
		}
	}

	private final class RowHolder extends RecyclerView.ViewHolder {
		final TextView title;
		final RecyclerView list;
		final XtreamCards cards;

		RowHolder(View v) {
			super(v);
			title = v.findViewById(R.id.xtream_row_title);
			list = v.findViewById(R.id.xtream_row_list);
			LinearLayoutManager lm = new LinearLayoutManager(v.getContext(),
					LinearLayoutManager.HORIZONTAL, false);
			lm.setInitialPrefetchItemCount(6);
			list.setLayoutManager(lm);
			list.setRecycledViewPool(pool);
			list.setHasFixedSize(true);
			cards = new XtreamCards(controller);
			list.setAdapter(cards);
		}

		void bind(Home.Row r) {
			title.setText(rowTitle(itemView.getContext(), r));
			if (r.entries.isEmpty()) cards.setRecords(r.records, r.type == Home.Row.Type.CONTINUE);
			else cards.setEntries(r.entries);
			list.scrollToPosition(0);
		}
	}
}
