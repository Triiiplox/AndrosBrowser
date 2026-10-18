package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.Catalog;
import com.triiiplox.iptv.Home;
import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.Program;
import com.triiiplox.iptv.model.Series;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvAddon;
import me.aap.fermata.addon.tv.xtream.XtreamRepo.Account;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.LogoLoadingDrawable;
import me.aap.utils.log.Log;
import me.aap.utils.ui.view.ToolBarView;

/**
 * The IPTV screen, Netflix style: Home / Live / Movies / Series tabs of rows, a details page for
 * movies and series, and a search. The catalog comes from the cache at once and is refreshed in
 * the background when old. Videos play in the app's player, full screen in this screen.
 */
public class XtreamFragment extends MainActivityFragment implements XtreamPageAdapter.Controller,
		XtreamDetails.Controller, FermataServiceUiBinder.Listener {
	private enum Tab {HOME, LIVE, MOVIES, SERIES}

	private final List<Object> overlays = new ArrayList<>();
	private XtreamRepo repo;
	private Tab tab = Tab.HOME;
	@Nullable
	private Account account;
	@Nullable
	private Snapshot snapshot;
	private XtreamPageAdapter adapter;
	private RecyclerView page;
	private LinearLayout tabs;
	private View stateView;
	private ProgressBar stateProgress;
	private TextView stateTitle;
	private TextView stateText;
	private Button stateButton;
	private FrameLayout overlay;
	private View main;
	private int loadSeq;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.xtream_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getString(me.aap.fermata.R.string.addon_name_xtream);
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.Invisible.instance;
	}

	@Override
	public boolean isVideoModeSupported() {
		return true;
	}

	@Override
	public boolean isRootPage() {
		return overlays.isEmpty() && (tab == Tab.HOME);
	}

	@Override
	public boolean onBackPressed() {
		// Back from the video comes back to this screen, as in the library: the screen the video
		// was opened from, not the start section
		BodyLayout body = MainActivityDelegate.get(requireContext()).getBody();
		if (body.isVideoMode()) {
			body.setMode(BodyLayout.Mode.BOTH);
			return true;
		}
		if (!overlays.isEmpty()) {
			popOverlay();
			return true;
		}
		if (tab != Tab.HOME) {
			selectTab(Tab.HOME);
			return true;
		}
		return super.onBackPressed();
	}

	@Override
	public void navBarItemReselected(int itemId) {
		while (!overlays.isEmpty()) popOverlay();
		if (tab != Tab.HOME) selectTab(Tab.HOME);
		else page.smoothScrollToPosition(0);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.xtream_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);
		Context ctx = requireContext();
		repo = XtreamRepo.get(ctx);
		main = v.findViewById(R.id.xtream_main);
		page = v.findViewById(R.id.xtream_page);
		tabs = v.findViewById(R.id.xtream_tabs);
		overlay = v.findViewById(R.id.xtream_overlay);
		stateView = v.findViewById(R.id.xtream_state);
		stateProgress = v.findViewById(R.id.xtream_state_progress);
		stateTitle = v.findViewById(R.id.xtream_state_title);
		stateText = v.findViewById(R.id.xtream_state_text);
		stateButton = v.findViewById(R.id.xtream_state_button);
		stateProgress.setIndeterminateDrawable(new LogoLoadingDrawable(ctx));

		adapter = new XtreamPageAdapter(this);
		page.setLayoutManager(new LinearLayoutManager(ctx));
		page.setAdapter(adapter);
		page.setItemViewCacheSize(4);

		LayoutInflater inf = LayoutInflater.from(ctx);
		for (Tab t : Tab.values()) {
			TextView chip = (TextView) inf.inflate(R.layout.xtream_tab, tabs, false);
			chip.setText(switch (t) {
				case HOME -> R.string.xtream_home;
				case LIVE -> R.string.xtream_live;
				case MOVIES -> R.string.xtream_movies;
				case SERIES -> R.string.xtream_series;
			});
			chip.setOnClickListener(x -> selectTab(t));
			tabs.addView(chip);
		}
		updateTabs();
		v.findViewById(R.id.xtream_search_button).setOnClickListener(x -> openSearch());
		v.findViewById(R.id.xtream_account_button).setOnClickListener(x -> showAccounts());

		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		a.getMediaServiceBinder().addBroadcastListener(this);
		start(a);
	}

	@Override
	public void onDestroyView() {
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a ->
				a.getMediaServiceBinder().removeBroadcastListener(this));
		for (Object o : overlays) if (o instanceof XtreamSearch s) s.close();
		overlays.clear();
		super.onDestroyView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) refreshLibraryRows();
	}

	// --- Loading ---

	private void start(MainActivityDelegate a) {
		TvAddon tv = AddonManager.get().getAddon(TvAddon.class);
		if ((tv == null) || !(a.getLib() instanceof DefaultMediaLib lib)) {
			loadAccount();
			return;
		}
		// The lists of the TV, get.php panels and plain M3U playlists, become accounts here
		repo.importTvSources(tv.getRootItem(lib)).onCompletion((n, err) -> {
			if (err != null) Log.e(err, "Xtream: failed to import the TV sources");
			loadAccount();
		});
	}

	private void loadAccount() {
		if (!isAdded()) return;
		account = repo.getActiveAccount();
		snapshot = null;
		if (account == null) {
			showState(false, getString(R.string.xtream_no_accounts_title),
					getString(R.string.xtream_no_accounts_text), getString(R.string.xtream_add_account),
					this::addAccount);
			return;
		}
		Account acc = account;
		int seq = ++loadSeq;
		Catalog c = repo.catalog(acc);
		if (c.peek() == null) showState(true, getString(R.string.xtream_loading), acc.name, null, null);
		repo.snapshot(acc).main().onCompletion((s, err) -> {
			if ((seq != loadSeq) || !isAdded()) return;
			if (err != null) {
				Log.e(err, "Xtream: failed to load the catalog of ", acc);
				showState(false, getString(R.string.xtream_error, String.valueOf(err.getMessage())),
						acc.name, getString(R.string.xtream_retry), this::loadAccount);
				return;
			}
			snapshot = s;
			hideState();
			render();
			if (c.isStale()) refreshInBackground(acc, seq);
		});
	}

	private void refreshInBackground(Account acc, int seq) {
		repo.run(() -> repo.catalog(acc).refresh()).main().onCompletion((s, err) -> {
			if ((seq != loadSeq) || !isAdded()) return;
			if (err != null) {
				Log.w("Xtream: background refresh failed: ", err.getMessage());
				return;
			}
			snapshot = s;
			// Only when the user is at the top: the page must not jump under the finger
			if (overlays.isEmpty() && !page.canScrollVertically(-1)) render();
		});
	}

	private void render() {
		Snapshot s = snapshot;
		Account acc = account;
		if ((s == null) || (acc == null)) return;
		Tab t = tab;
		int seq = loadSeq;
		UserLibrary lib = repo.getLibrary();
		repo.run(() -> switch (t) {
			case HOME -> Home.start(s, lib, acc.id);
			case LIVE -> Home.of(Kind.LIVE, s, lib, acc.id);
			case MOVIES -> Home.of(Kind.MOVIE, s, lib, acc.id);
			case SERIES -> Home.of(Kind.SERIES, s, lib, acc.id);
		}).main().onSuccess(p -> {
			if ((seq != loadSeq) || (t != tab) || !isAdded()) return;
			adapter.setPage(s, p);
		});
	}

	/** Continue watching and my list change with every playback. */
	private void refreshLibraryRows() {
		if ((snapshot != null) && overlays.isEmpty() && (page != null)) render();
		for (Object o : overlays) if (o instanceof XtreamDetails d) d.refresh();
	}

	private void selectTab(Tab t) {
		if (tab == t) return;
		tab = t;
		updateTabs();
		adapter.clear();
		page.scrollToPosition(0);
		render();
	}

	private void updateTabs() {
		for (int i = 0; i < tabs.getChildCount(); i++) {
			tabs.getChildAt(i).setSelected(i == tab.ordinal());
		}
	}

	private void showState(boolean progress, String title, @Nullable String text,
												 @Nullable String button, @Nullable Runnable action) {
		stateView.setVisibility(View.VISIBLE);
		stateProgress.setVisibility(progress ? View.VISIBLE : View.GONE);
		stateTitle.setText(title);
		XtreamUi.text(stateText, text);
		stateButton.setVisibility((button != null) ? View.VISIBLE : View.GONE);
		stateButton.setText(button);
		stateButton.setOnClickListener((action != null) ? x -> action.run() : null);
	}

	private void hideState() {
		stateView.setVisibility(View.GONE);
	}

	// --- Overlays: details and search ---

	private void pushOverlay(Object o, View v) {
		overlays.add(o);
		overlay.removeAllViews();
		overlay.addView(v);
		overlay.setVisibility(View.VISIBLE);
		main.setVisibility(View.INVISIBLE);
	}

	private void popOverlay() {
		Object o = overlays.remove(overlays.size() - 1);
		if (o instanceof XtreamSearch s) s.close();
		overlay.removeAllViews();
		if (overlays.isEmpty()) {
			overlay.setVisibility(View.GONE);
			main.setVisibility(View.VISIBLE);
			render();
		} else {
			Object top = overlays.get(overlays.size() - 1);
			View v = (top instanceof XtreamDetails d) ? d.getView() : ((XtreamSearch) top).getView();
			overlay.addView(v);
			if (top instanceof XtreamDetails d) d.refresh();
		}
	}

	private void openDetails(Entry e) {
		if (account == null) return;
		XtreamDetails d = new XtreamDetails(requireContext(), this, account, e);
		pushOverlay(d, d.getView());
	}

	private void openSearch() {
		if (snapshot == null) return;
		XtreamSearch s = new XtreamSearch(requireContext(), snapshot, this, this::popOverlay);
		pushOverlay(s, s.getView());
		s.focus();
	}

	@Override
	public void closeDetails() {
		if (!overlays.isEmpty()) popOverlay();
	}

	// --- Cards ---

	@Override
	public void onEntry(Entry e) {
		if (e.kind == Kind.LIVE) playEntry(e);
		else openDetails(e);
	}

	@Override
	public void onRecord(UserLibrary.Record r) {
		Snapshot s = snapshot;
		Account acc = account;
		if ((s == null) || (acc == null)) return;
		switch (r.kind) {
			case LIVE -> {
				Entry e = s.get(Kind.LIVE, r.id);
				if (e != null) playEntry(e);
			}
			case MOVIE -> {
				Entry e = s.get(Kind.MOVIE, r.id);
				if (e == null) return;
				if (r.isInProgress()) playMovie(e, null, false);
				else openDetails(e);
			}
			case SERIES -> {
				Entry e = s.get(Kind.SERIES, r.id);
				if (e != null) openDetails(e);
			}
			case EPISODE -> {
				String id = XtreamIds.episode(acc.id, r.seriesId, r.season, r.id, r.extension);
				MainActivityDelegate a = MainActivityDelegate.get(requireContext());
				a.getLib().getItem(id).main().onCompletion((i, err) -> {
					if (i instanceof PlayableItem pi) play(pi);
					else if (r.seriesId != null) {
						Entry e = s.get(Kind.SERIES, r.seriesId);
						if (e != null) openDetails(e);
					}
				});
			}
		}
	}

	@Override
	public void onLongPress(@Nullable Entry e, @Nullable UserLibrary.Record r) {
		Account acc = account;
		if (acc == null) return;
		UserLibrary.Record rec = (r != null) ? r : (e != null) ? record(acc, e) : null;
		if ((rec == null) || (rec.kind == Kind.EPISODE)) return;
		boolean fav = repo.getLibrary().toggleFavorite(rec);
		Toast.makeText(requireContext(), fav ? R.string.xtream_added_to_list :
				R.string.xtream_removed_from_list, Toast.LENGTH_SHORT).show();
		refreshLibraryRows();
	}

	@Override
	public void onHeroPlay(Entry e) {
		if (e.kind == Kind.SERIES) openDetails(e);
		else playEntry(e);
	}

	@Override
	public void onHeroInfo(Entry e) {
		openDetails(e);
	}

	@Override
	public void toggleMyList(Entry e) {
		if (account != null) repo.getLibrary().toggleFavorite(record(account, e));
	}

	private static UserLibrary.Record record(Account acc, Entry e) {
		return new UserLibrary.Record(acc.id, e.kind, e.id, e.name, null, e.image, null, null, 0, 0,
				0, 0, System.currentTimeMillis());
	}

	// --- Playback ---

	private void playEntry(Entry e) {
		if ((account == null) || (snapshot == null)) return;
		if (e.kind == Kind.MOVIE) playMovie(e, null, false);
		else if (e.kind == Kind.LIVE) play(root().playable(account, snapshot, e));
	}

	@Override
	public void playMovie(Entry movie, @Nullable Details d, boolean fromStart) {
		if ((account == null) || (snapshot == null)) return;
		XtreamPlayable i = root().playable(account, snapshot, movie);
		start(i, (d != null) ? d.duration * 1000L : 0, fromStart);
	}

	@Override
	public void playEpisode(Series series, Details.Episode ep, boolean fromStart) {
		if ((account == null) || (snapshot == null)) return;
		XtreamPlayable i = root().episode(account, snapshot, series, ep);
		start(i, ep.duration * 1000L, fromStart);
	}

	// The app resumes a video only when its duration is known: give it before the playback
	private void start(XtreamPlayable i, long durationMs, boolean fromStart) {
		if (fromStart) i.getPrefs().setPositionPref(0);
		if (durationMs <= 0) {
			play(i);
			return;
		}
		i.getDuration().main().onCompletion((dur, err) -> {
			if ((dur == null) || (dur <= 0)) i.setDuration(durationMs).main().onCompletion((v, e) -> play(i));
			else play(i);
		});
	}

	private void play(PlayableItem i) {
		Log.i("Xtream: play ", i.getId(), " (engine ", i.getPrefs().getVideoEnginePref(), ")");
		MainActivityDelegate a = MainActivityDelegate.get(requireContext());
		a.getBody().playItem(i);
		if ((i instanceof XtreamPlayable xp) && (xp.getKind() == Kind.LIVE)) loadNowNext(xp);
	}

	/**
	 * The guide of a live channel, asked while the stream starts: one short request to the panel
	 * (cached for minutes), not the whole XMLTV. A playlist has no guide of its own.
	 */
	private void loadNowNext(XtreamPlayable xp) {
		Account acc = repo.getAccount(xp.getAccountId());
		if ((acc == null) || acc.isPlaylist()) return;
		Context ctx = requireContext().getApplicationContext();
		repo.run(() -> repo.catalog(acc).epg(xp.getStreamId(), 4)).main().onCompletion((l, err) -> {
			if (err != null) {
				Log.d("Xtream: no guide for the channel: ", err.getMessage());
				return;
			}
			String text = nowNext(ctx, l, System.currentTimeMillis() / 1000);
			if (text != null) xp.setNowNext(text);
		});
	}

	@Nullable
	static String nowNext(Context ctx, @Nullable List<Program> programs, long now) {
		if ((programs == null) || programs.isEmpty()) return null;
		Program cur = null;
		Program next = null;
		for (Program p : programs) {
			if ((cur == null) && (p.start <= now) && (now < p.end)) cur = p;
			else if ((p.start >= now) && ((next == null) || (p.start < next.start))) next = p;
		}
		if (cur == null) return null;
		java.text.DateFormat t = android.text.format.DateFormat.getTimeFormat(ctx);
		String on = cur.title + " (" + t.format(new Date(cur.start * 1000)) + "–" +
				t.format(new Date(cur.end * 1000)) + ")";
		return (next == null) ? ctx.getString(R.string.xtream_live_now, on) :
				ctx.getString(R.string.xtream_now_next, on, next.title);
	}

	private XtreamRootItem root() {
		return XtreamAddon.getRoot(MainActivityDelegate.get(requireContext()));
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		// The library is written a moment after the change
		if ((oldItem instanceof XtreamPlayable) || (newItem instanceof XtreamPlayable)) {
			page.postDelayed(() -> {
				if (isAdded() && !isHidden()) refreshLibraryRows();
			}, 2000);
		}
	}

	// --- Accounts ---

	private void addAccount() {
		XtreamAccountForm.show(MainActivityDelegate.get(requireContext()), a -> {
			repo.setActiveAccount(a);
			loadAccount();
		});
	}

	/**
	 * The accounts: tap one to open it. The open account can be edited, refreshed or removed; the
	 * removal asks first, and drops the account with its cached catalog.
	 */
	private void showAccounts() {
		MainActivityDelegate a = MainActivityDelegate.get(requireContext());
		List<Account> list = repo.getAccounts();
		List<CharSequence> items = new ArrayList<>();
		for (Account acc : list) {
			String kind = getString(acc.isPlaylist() ? R.string.xtream_kind_playlist :
					R.string.xtream_kind_panel);
			items.add(acc.name + "  ·  " + kind);
		}
		int add = items.size();
		items.add("+ " + getString(R.string.xtream_add_account));
		int edit = -1, refresh = -1, remove = -1;
		if (account != null) {
			edit = items.size();
			items.add("✎ " + getString(R.string.xtream_edit_account) + ": " + account.name);
			refresh = items.size();
			items.add("↻ " + getString(R.string.xtream_refresh));
			remove = items.size();
			items.add("✕ " + getString(R.string.xtream_remove_account) + ": " + account.name);
		}
		int fEdit = edit, fRefresh = refresh, fRemove = remove;
		a.createDialogBuilder().setTitle(R.string.xtream_accounts)
				.setSingleChoiceItems(items.toArray(new CharSequence[0]), list.indexOf(account),
						(d, which) -> {
					d.dismiss();
					if (which < add) {
						Account sel = list.get(which);
						if (sel != account) {
							repo.setActiveAccount(sel);
							closeAll();
							loadAccount();
						}
					} else if (which == add) {
						addAccount();
					} else if (which == fEdit) {
						editAccount();
					} else if (which == fRefresh) {
						refreshNow();
					} else if (which == fRemove) {
						confirmRemove();
					}
				}).setNegativeButton(android.R.string.cancel, null).show();
		showExpiry();
	}

	private void editAccount() {
		Account acc = account;
		if (acc == null) return;
		XtreamAccountForm.edit(MainActivityDelegate.get(requireContext()), acc, n -> {
			repo.setActiveAccount(n);
			closeAll();
			loadAccount();
		});
	}

	private void confirmRemove() {
		Account acc = account;
		if (acc == null) return;
		MainActivityDelegate.get(requireContext()).createDialogBuilder()
				.setTitle(R.string.xtream_remove_account)
				.setMessage(getString(R.string.xtream_remove_confirm, acc.name))
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(R.string.xtream_remove, (d, w) -> {
					repo.removeAccount(acc);
					closeAll();
					loadAccount();
				}).show();
	}

	private void refreshNow() {
		Account acc = account;
		if (acc == null) return;
		int seq = ++loadSeq;
		showState(true, getString(R.string.xtream_loading), acc.name, null, null);
		repo.run(() -> repo.catalog(acc).refresh()).main().onCompletion((s, err) -> {
			if ((seq != loadSeq) || !isAdded()) return;
			hideState();
			if (s != null) snapshot = s;
			if (err != null) Log.e(err, "Xtream: refresh failed");
			render();
		});
	}

	// The account status in a toast: a trial or a paid list expires, worth knowing before the trip
	private void showExpiry() {
		Account acc = account;
		if ((acc == null) || acc.isPlaylist()) return; // A playlist has no account to expire
		repo.run(() -> repo.catalog(acc).getClient().accountInfo()).main().onSuccess(info -> {
			if (!isAdded() || (info.expires <= 0)) return;
			String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
					.format(new Date(info.expires * 1000));
			Toast.makeText(requireContext(), getString(R.string.xtream_expires, date),
					Toast.LENGTH_LONG).show();
		});
	}

	private void closeAll() {
		while (!overlays.isEmpty()) popOverlay();
		adapter.clear();
	}
}
