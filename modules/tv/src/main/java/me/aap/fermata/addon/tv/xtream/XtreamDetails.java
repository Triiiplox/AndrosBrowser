package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.LiveChannel;
import com.triiiplox.iptv.model.Program;
import com.triiiplox.iptv.model.Series;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.xtream.XtreamRepo.Account;
import me.aap.utils.log.Log;

/**
 * The details page of a movie or a series, Netflix style: the backdrop, the title, play or
 * continue, my list, the plot, and for a series its seasons and episodes with what was watched.
 * The page shows at once with what the list knows; the details fill it when they arrive.
 * <p>
 * A live channel gets the same page: watch live, my list, what is on now and next, and when the
 * panel records the channel, its past programs by day, to watch again (catch-up).
 */
final class XtreamDetails {
	private final Controller controller;
	private final Account account;
	private final Entry entry;
	private final XtreamRepo repo;
	private final RecyclerView view;
	private final Adapter adapter = new Adapter();
	@Nullable
	private Details details;
	// The season shown, or for a channel the index of the day of past programs shown
	private int season = -1;
	// A channel: its guide, and the past programs to watch again, newest first, by day
	private List<Program> guide = Collections.emptyList();
	private final List<List<Program>> pastDays = new ArrayList<>();

	XtreamDetails(Context ctx, Controller controller, Account account, Entry entry) {
		this.controller = controller;
		this.account = account;
		this.entry = entry;
		repo = XtreamRepo.get(ctx);
		view = new RecyclerView(ctx);
		view.setLayoutManager(new LinearLayoutManager(ctx));
		view.setBackgroundColor(resolveColor(ctx, android.R.attr.colorBackground));
		view.setClipToPadding(false);
		view.setPadding(0, 0, 0, (int) (24 * ctx.getResources().getDisplayMetrics().density));
		view.setAdapter(adapter);
		load();
	}

	View getView() {
		return view;
	}

	Entry getEntry() {
		return entry;
	}

	/** After a playback: the progress and the continue button may have changed. */
	void refresh() {
		adapter.notifyDataSetChanged();
	}

	private boolean isChannel() {
		return entry.kind == Kind.LIVE;
	}

	private int archiveDays() {
		return (entry instanceof LiveChannel ch) ? ch.archiveDays : 0;
	}

	private void load() {
		if (isChannel()) {
			loadGuide();
			return;
		}
		repo.run(() -> repo.catalog(account).details(entry)).main().onCompletion((d, err) -> {
			if (err != null) Log.e(err, "Xtream: failed to load the details of ", entry);
			details = (d != null) ? d : new Details(null, null, null, null, null, 0, 0, null, null, null,
					null);
			if (!details.seasons.isEmpty()) season = defaultSeason(details);
			adapter.loaded = true;
			adapter.notifyDataSetChanged();
		});
	}

	// The whole guide when the panel records the channel (the past days with it), else now and next
	private void loadGuide() {
		boolean archive = archiveDays() > 0;
		repo.run(() -> archive ? repo.catalog(account).archive(entry.id) :
				repo.catalog(account).epg(entry.id, 4)).main().onCompletion((l, err) -> {
			if (err != null) Log.e(err, "Xtream: failed to load the guide of ", entry);
			guide = (l != null) ? l : Collections.emptyList();
			pastDays.clear();
			if (archive) groupPastDays(System.currentTimeMillis() / 1000);
			season = 0;
			adapter.loaded = true;
			adapter.notifyDataSetChanged();
		});
	}

	// The past programs kept by the panel, newest first, one list per day. Some panels never flag
	// the kept programs: then the past ones within the channel's archive days are offered.
	private void groupPastDays(long now) {
		boolean flagged = false;
		for (Program p : guide) flagged |= p.archived;
		long oldest = now - archiveDays() * 86400L;
		List<Program> past = new ArrayList<>();
		for (Program p : guide) {
			if (flagged ? p.canCatchUp(now) : ((p.end <= now) && (p.start >= oldest))) past.add(p);
		}
		past.sort((a, b) -> Long.compare(b.start, a.start));
		Calendar c = Calendar.getInstance();
		int day = -1;
		for (Program p : past) {
			c.setTimeInMillis(p.start * 1000);
			int d = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
			if (d != day) {
				pastDays.add(new ArrayList<>());
				day = d;
			}
			pastDays.get(pastDays.size() - 1).add(p);
		}
	}

	private List<Program> pastPrograms() {
		return ((season >= 0) && (season < pastDays.size())) ? pastDays.get(season) :
				Collections.emptyList();
	}

	// The season of the last episode watched, else the first
	private int defaultSeason(Details d) {
		UserLibrary.Record last = repo.getLibrary().lastEpisode(account.id, entry.id);
		if (last != null) {
			for (Details.Season s : d.seasons) if (s.number == last.season) return s.number;
		}
		return d.seasons.get(0).number;
	}

	private List<Details.Episode> episodes() {
		if (details == null) return Collections.emptyList();
		for (Details.Season s : details.seasons) if (s.number == season) return s.episodes;
		return Collections.emptyList();
	}

	/** The episode to play from the header: continue the last one, or the next, or the first. */
	@Nullable
	private Details.Episode nextEpisode() {
		if ((details == null) || details.seasons.isEmpty()) return null;
		UserLibrary.Record last = repo.getLibrary().lastEpisode(account.id, entry.id);
		boolean next = false;
		for (Details.Season s : details.seasons) {
			for (Details.Episode ep : s.episodes) {
				if (next) return ep;
				if ((last != null) && ep.id.equals(last.id)) {
					if (!last.isFinished()) return ep;
					next = true;
				}
			}
		}
		return details.seasons.get(0).episodes.isEmpty() ? null : details.seasons.get(0).episodes.get(0);
	}

	interface Controller {
		void playMovie(Entry movie, @Nullable Details d, boolean fromStart);

		void playLive(Entry channel);

		/** A past program of a channel, from the panel's recording. */
		void playCatchup(Entry channel, Program p);

		void playEpisode(Series series, Details.Episode ep, boolean fromStart);

		void toggleMyList(Entry e);

		void closeDetails();
	}

	private static int resolveColor(Context ctx, int attr) {
		android.util.TypedValue v = new android.util.TypedValue();
		ctx.getTheme().resolveAttribute(attr, v, true);
		return v.data;
	}

	private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		boolean loaded;

		@Override
		public int getItemCount() {
			return 1 + (isChannel() ? pastPrograms().size() : episodes().size());
		}

		@Override
		public int getItemViewType(int position) {
			return (position == 0) ? 0 : 1;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
			LayoutInflater inf = LayoutInflater.from(parent.getContext());
			if (type == 0) return new Header(inf.inflate(R.layout.xtream_details_header, parent, false));
			return new EpisodeHolder(inf.inflate(R.layout.xtream_episode, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
			if (h instanceof Header hh) hh.bind();
			else if (isChannel()) ((EpisodeHolder) h).bind(pastPrograms().get(position - 1));
			else ((EpisodeHolder) h).bind(episodes().get(position - 1));
		}
	}

	private final class Header extends RecyclerView.ViewHolder {
		final ImageView backdrop;
		final ImageView poster;
		final TextView title;
		final TextView meta;
		final TextView play;
		final TextView list;
		final TextView restart;
		final ProgressBar progress;
		final TextView plot;
		final TextView credits;
		final View loading;
		final TextView episodesTitle;
		final HorizontalScrollView seasonsScroll;
		final LinearLayout seasons;
		boolean imagesSet;

		Header(View v) {
			super(v);
			backdrop = v.findViewById(R.id.xtream_details_backdrop);
			poster = v.findViewById(R.id.xtream_details_poster);
			title = v.findViewById(R.id.xtream_details_title);
			meta = v.findViewById(R.id.xtream_details_meta);
			play = v.findViewById(R.id.xtream_details_play);
			list = v.findViewById(R.id.xtream_details_list);
			restart = v.findViewById(R.id.xtream_details_restart);
			progress = v.findViewById(R.id.xtream_details_progress);
			plot = v.findViewById(R.id.xtream_details_plot);
			credits = v.findViewById(R.id.xtream_details_credits);
			loading = v.findViewById(R.id.xtream_details_loading);
			episodesTitle = v.findViewById(R.id.xtream_details_episodes_title);
			seasonsScroll = v.findViewById(R.id.xtream_details_seasons_scroll);
			seasons = v.findViewById(R.id.xtream_details_seasons);
			v.findViewById(R.id.xtream_details_back).setOnClickListener(x -> controller.closeDetails());
			restart.setText(R.string.xtream_from_start);
			restart.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
			plot.setOnClickListener(x -> plot.setMaxLines((plot.getMaxLines() == 4) ? 40 : 4));
			list.setOnClickListener(x -> {
				controller.toggleMyList(entry);
				bindList();
			});
		}

		void bind() {
			if (isChannel()) {
				bindChannel(itemView.getContext());
				return;
			}
			Context ctx = itemView.getContext();
			Details d = details;
			title.setText(entry.name);
			if (!imagesSet || (d != null)) {
				String bd = (d != null) ? d.backdrop() : null;
				if ((bd == null) && (entry instanceof Series s)) bd = s.backdrop;
				String pos = (entry.image != null) ? entry.image : ((d != null) ? d.poster : null);
				XtreamUi.image(backdrop, (bd != null) ? bd : pos, null, true);
				XtreamUi.image(poster, pos, null, false);
				poster.setVisibility((pos != null) ? View.VISIBLE : View.GONE);
				imagesSet = true;
			}

			StringBuilder m = new StringBuilder();
			String year = (d != null) ? d.year() : null;
			if ((year == null) && (entry instanceof Series s)) year = s.year;
			if (year != null) m.append(year);
			float rating = ((d != null) && (d.rating > 0)) ? d.rating : entry.rating;
			if (rating > 0) XtreamUi.sep(m).append("★ ").append(String.format(java.util.Locale.ROOT, "%.1f", rating));
			if (entry.kind == Kind.MOVIE) {
				String dur = (d != null) ? XtreamUi.duration(ctx, d.duration) : null;
				if (dur != null) XtreamUi.sep(m).append(dur);
			} else if ((d != null) && (d.seasons.size() > 1)) {
				XtreamUi.sep(m).append(ctx.getString(R.string.xtream_seasons, d.seasons.size()));
			}
			String genre = (d != null) ? d.genre : null;
			if ((genre == null) && (entry instanceof Series s)) genre = s.genre;
			if (genre != null) XtreamUi.sep(m).append(genre);
			XtreamUi.text(meta, m);

			String p = (d != null) ? d.plot : null;
			if ((p == null) && (entry instanceof Series s)) p = s.plot;
			XtreamUi.text(plot, p);
			StringBuilder c = new StringBuilder();
			if ((d != null) && (d.cast != null)) c.append(ctx.getString(R.string.xtream_cast, d.cast));
			if ((d != null) && (d.director != null)) {
				if (c.length() > 0) c.append('\n');
				c.append(ctx.getString(R.string.xtream_director, d.director));
			}
			XtreamUi.text(credits, c);
			loading.setVisibility(adapter.loaded ? View.GONE : View.VISIBLE);
			bindPlay(ctx);
			bindList();
			bindSeasons(ctx);
		}

		// Watch live, my list, now and next, and the days of past programs as chips
		private void bindChannel(Context ctx) {
			title.setText(entry.name);
			if (!imagesSet) {
				poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
				XtreamUi.image(poster, entry.image, null, false);
				poster.setVisibility((entry.image != null) ? View.VISIBLE : View.GONE);
				imagesSet = true;
			}
			int days = archiveDays();
			StringBuilder m = new StringBuilder(ctx.getString(R.string.xtream_live));
			if (days > 0) XtreamUi.sep(m).append(ctx.getResources()
					.getQuantityString(R.plurals.xtream_catchup_days, days, days));
			XtreamUi.text(meta, m);
			XtreamUi.text(plot, XtreamFragment.nowNext(ctx, guide, System.currentTimeMillis() / 1000));
			credits.setVisibility(View.GONE);
			restart.setVisibility(View.GONE);
			progress.setVisibility(View.GONE);
			loading.setVisibility(adapter.loaded ? View.GONE : View.VISIBLE);
			play.setText(R.string.xtream_watch_live);
			play.setEnabled(true);
			play.setOnClickListener(x -> controller.playLive(entry));
			bindList();

			boolean past = !pastDays.isEmpty();
			episodesTitle.setVisibility((days > 0) && adapter.loaded ? View.VISIBLE : View.GONE);
			episodesTitle.setText(past ? R.string.xtream_catchup : R.string.xtream_catchup_none);
			seasonsScroll.setVisibility((pastDays.size() > 1) ? View.VISIBLE : View.GONE);
			if (pastDays.size() < 2) return;
			seasons.removeAllViews();
			LayoutInflater inf = LayoutInflater.from(ctx);
			for (int i = 0; i < pastDays.size(); i++) {
				int index = i;
				TextView chip = (TextView) inf.inflate(R.layout.xtream_tab, seasons, false);
				chip.setText(dayName(ctx, pastDays.get(i).get(0).start));
				chip.setSelected(i == season);
				chip.setOnClickListener(x -> {
					if (season == index) return;
					season = index;
					adapter.notifyDataSetChanged();
				});
				seasons.addView(chip);
			}
		}

		private void bindPlay(Context ctx) {
			restart.setVisibility(View.GONE);
			if (entry.kind == Kind.MOVIE) {
				UserLibrary.Record r = repo.getLibrary().get(account.id, Kind.MOVIE, entry.id);
				boolean cont = (r != null) && r.isInProgress();
				play.setText(cont ? R.string.xtream_resume : R.string.xtream_play);
				XtreamUi.progress(progress, cont ? r : null);
				if (cont) {
					restart.setVisibility(View.VISIBLE);
					restart.setOnClickListener(x -> controller.playMovie(entry, details, true));
				}
				play.setOnClickListener(x -> controller.playMovie(entry, details, false));
				play.setEnabled(true);
				return;
			}

			Details.Episode ep = nextEpisode();
			progress.setVisibility(View.GONE);
			if (ep == null) {
				play.setText(R.string.xtream_play);
				play.setEnabled(false);
				play.setOnClickListener(null);
				return;
			}
			UserLibrary.Record r = repo.getLibrary().get(account.id, Kind.EPISODE, ep.id);
			boolean cont = (r != null) && r.isInProgress();
			play.setText(ctx.getString(cont ? R.string.xtream_resume_episode : R.string.xtream_play_episode,
					ep.season, ep.number));
			XtreamUi.progress(progress, cont ? r : null);
			play.setEnabled(true);
			play.setOnClickListener(x -> controller.playEpisode((Series) entry, ep, false));
		}

		private void bindList() {
			boolean fav = repo.getLibrary().isFavorite(account.id, entry.kind, entry.id);
			list.setText(fav ? R.string.xtream_my_list_added : R.string.xtream_my_list_add);
			list.setCompoundDrawablesRelativeWithIntrinsicBounds(
					fav ? R.drawable.xtream_check : R.drawable.xtream_add, 0, 0, 0);
		}

		private void bindSeasons(Context ctx) {
			Details d = details;
			boolean series = (d != null) && !d.seasons.isEmpty();
			episodesTitle.setVisibility(series ? View.VISIBLE : View.GONE);
			seasonsScroll.setVisibility(series && (d.seasons.size() > 1) ? View.VISIBLE : View.GONE);
			if (!series || (d.seasons.size() < 2)) return;
			seasons.removeAllViews();
			LayoutInflater inf = LayoutInflater.from(ctx);
			for (Details.Season s : d.seasons) {
				TextView chip = (TextView) inf.inflate(R.layout.xtream_tab, seasons, false);
				String name = ((s.name != null) && !s.name.trim().isEmpty()) ? s.name :
						ctx.getString(R.string.xtream_season_n, s.number);
				chip.setText(name);
				chip.setSelected(s.number == season);
				chip.setOnClickListener(x -> {
					if (season == s.number) return;
					season = s.number;
					adapter.notifyDataSetChanged();
				});
				seasons.addView(chip);
			}
		}
	}

	private final class EpisodeHolder extends RecyclerView.ViewHolder {
		final ImageView image;
		final TextView title;
		final TextView meta;
		final TextView plot;
		final ProgressBar progress;
		Details.Episode ep;
		Program program;

		EpisodeHolder(View v) {
			super(v);
			image = v.findViewById(R.id.xtream_episode_image);
			title = v.findViewById(R.id.xtream_episode_title);
			meta = v.findViewById(R.id.xtream_episode_meta);
			plot = v.findViewById(R.id.xtream_episode_plot);
			progress = v.findViewById(R.id.xtream_episode_progress);
			v.setOnClickListener(x -> {
				if (ep != null) controller.playEpisode((Series) entry, ep, false);
				else if (program != null) controller.playCatchup(entry, program);
			});
		}

		// A past program: its time, its length, what it is about, under the channel's logo
		void bind(Program p) {
			this.ep = null;
			this.program = p;
			Context ctx = itemView.getContext();
			title.setText(p.title);
			java.text.DateFormat t = android.text.format.DateFormat.getTimeFormat(ctx);
			String time = t.format(new Date(p.start * 1000)) + "–" + t.format(new Date(p.end * 1000));
			String dur = XtreamUi.duration(ctx, (int) (p.end - p.start));
			XtreamUi.text(meta, (dur != null) ? time + " · " + dur : time);
			XtreamUi.text(plot, p.description);
			progress.setVisibility(View.GONE);
			image.setScaleType(ImageView.ScaleType.FIT_CENTER);
			XtreamUi.image(image, entry.image, null, false);
		}

		void bind(Details.Episode ep) {
			this.ep = ep;
			this.program = null;
			Context ctx = itemView.getContext();
			String t = (ep.title == null) ? "" : ep.title.trim();
			if (t.isEmpty()) t = ctx.getString(R.string.xtream_episode_n, ep.number);
			title.setText(ep.number + ". " + t);
			XtreamUi.text(meta, XtreamUi.duration(ctx, ep.duration));
			XtreamUi.text(plot, ep.plot);
			UserLibrary.Record r = repo.getLibrary().get(account.id, Kind.EPISODE, ep.id);
			XtreamUi.progress(progress, r);
			String pic = (ep.image != null) ? ep.image :
					((entry instanceof Series s) && (s.backdrop != null)) ? s.backdrop : entry.image;
			XtreamUi.image(image, pic, null, false);
		}
	}

	// Today, Yesterday, else the weekday and the date
	static String dayName(Context ctx, long startSec) {
		Calendar day = Calendar.getInstance();
		day.setTimeInMillis(startSec * 1000);
		Calendar today = Calendar.getInstance();
		if (sameDay(day, today)) return ctx.getString(R.string.xtream_today);
		today.add(Calendar.DAY_OF_YEAR, -1);
		if (sameDay(day, today)) return ctx.getString(R.string.xtream_yesterday);
		return android.text.format.DateFormat.format("EEE dd/MM", day).toString();
	}

	private static boolean sameDay(Calendar a, Calendar b) {
		return (a.get(Calendar.YEAR) == b.get(Calendar.YEAR)) &&
				(a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR));
	}
}
