package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.model.Category;
import com.triiiplox.iptv.model.Details;
import com.triiiplox.iptv.model.Entry;
import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.model.Movie;
import com.triiiplox.iptv.model.Series;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.xtream.XtreamFolder.Type;
import me.aap.fermata.addon.tv.xtream.XtreamRepo.Account;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * The root of the Xtream tree: accounts, then live / movies / series, then categories, then
 * channels, movies and series (episodes). Every item is built from the cached catalog, so the
 * ids of the media session (last played, favorites, the car) resolve offline too. When even the
 * catalog is gone, a played item is rebuilt from the user library.
 */
public final class XtreamRootItem extends BrowsableItemBase implements XtreamItem {
	private final DefaultMediaLib lib;
	private final XtreamRepo repo;

	XtreamRootItem(DefaultMediaLib lib) {
		super(XtreamIds.ROOT, null, null);
		this.lib = lib;
		repo = XtreamRepo.get(lib.getContext());
	}

	public XtreamRepo getRepo() {
		return repo;
	}

	// --- Items from the catalog, for the screens ---

	public XtreamFolder accountFolder(Account a) {
		return (XtreamFolder) lib.getOrCreateCachedItem(XtreamIds.account(a.id),
				id -> new XtreamFolder(id, this, this, Type.ACCOUNT, a.id, a.name, null));
	}

	public XtreamFolder sectionFolder(Account a, Kind kind) {
		BrowsableItem parent = accountFolder(a);
		return (XtreamFolder) lib.getOrCreateCachedItem(XtreamIds.section(a.id, kind),
				id -> new XtreamFolder(id, this, parent, Type.SECTION, a.id, sectionName(kind), null,
						kind.code));
	}

	public XtreamFolder categoryFolder(Account a, Category c) {
		BrowsableItem parent = sectionFolder(a, c.kind);
		return (XtreamFolder) lib.getOrCreateCachedItem(XtreamIds.category(a.id, c.kind, c.id),
				id -> new XtreamFolder(id, this, parent, Type.CATEGORY, a.id, c.name, null, c.kind.code,
						c.id));
	}

	public XtreamFolder seriesFolder(Account a, Snapshot s, Series series) {
		BrowsableItem parent = parentOf(a, s, series);
		return (XtreamFolder) lib.getOrCreateCachedItem(XtreamIds.series(a.id, series.id),
				id -> new XtreamFolder(id, this, parent, Type.SERIES, a.id, series.name, series.image,
						series.id));
	}

	/** The playable of a channel or a movie. */
	public XtreamPlayable playable(Account a, Snapshot s, Entry e) {
		String ext = (e instanceof Movie m) ? m.extension : null;
		// A playlist entry has its own stream; an Xtream one is made from the account
		String url = (e.url != null) ? e.url :
				(e.kind == Kind.LIVE) ? a.xtream.liveUrl(e.id) : a.xtream.movieUrl(e.id, ext);
		BrowsableItem parent = parentOf(a, s, e);
		// The app resumes a video only when its duration is known: the one of the last playback
		long dur = (e.kind == Kind.LIVE) ? 0 : knownDuration(a, e.kind, e.id, 0);
		return (XtreamPlayable) lib.getOrCreateCachedItem(XtreamIds.playable(a.id, e.kind, e.id, ext),
				id -> new XtreamPlayable(id, parent, url, a.id, e.kind, e.id, e.name, parent.getName(),
						e.image, dur, null, 0, 0, ext));
	}

	private long knownDuration(Account a, Kind kind, String id, long fallback) {
		UserLibrary.Record r = repo.getLibrary().get(a.id, kind, id);
		return ((r != null) && (r.duration > 0)) ? r.duration : fallback;
	}

	/** The playable of an episode of a series. */
	public XtreamPlayable episode(Account a, Snapshot s, Series series, Details.Episode ep) {
		XtreamFolder parent = seriesFolder(a, s, series);
		return episode(a, parent, series.name, series.image, ep);
	}

	private XtreamPlayable episode(Account a, BrowsableItem parent, String seriesName,
																 @Nullable String seriesImage, Details.Episode ep) {
		String url = (ep.url != null) ? ep.url : a.xtream.episodeUrl(ep.id, ep.extension);
		String title = episodeTitle(ep);
		String sub = seriesName + " · " + lib.getContext().getString(R.string.xtream_season_episode,
				ep.season, ep.number);
		String image = (ep.image != null) ? ep.image : seriesImage;
		String seriesId = ((XtreamFolder) parent).args[0];
		long dur = knownDuration(a, Kind.EPISODE, ep.id, ep.duration * 1000L);
		return (XtreamPlayable) lib.getOrCreateCachedItem(
				XtreamIds.episode(a.id, seriesId, ep.season, ep.id, ep.extension),
				id -> new XtreamPlayable(id, parent, url, a.id, Kind.EPISODE, ep.id, title, sub, image,
						dur, seriesId, ep.season, ep.number, ep.extension));
	}

	public String episodeTitle(Details.Episode ep) {
		String t = (ep.title == null) ? "" : ep.title.trim();
		return t.isEmpty() ? lib.getContext().getString(R.string.xtream_episode_n, ep.number) : t;
	}

	public String sectionName(Kind kind) {
		return lib.getContext().getString(switch (kind) {
			case LIVE -> R.string.xtream_live;
			case MOVIE -> R.string.xtream_movies;
			default -> R.string.xtream_series;
		});
	}

	private BrowsableItem parentOf(Account a, Snapshot s, Entry e) {
		Category c = (e.categoryId != null) ? s.category(e.kind, e.categoryId) : null;
		return (c != null) ? categoryFolder(a, c) : sectionFolder(a, e.kind);
	}

	// --- Children, as the car browses ---

	FutureSupplier<List<Item>> listChildren(XtreamFolder f) {
		Account a = repo.getAccount(f.accountId);
		if (a == null) return completedEmptyList();

		switch (f.type) {
			case ACCOUNT:
				return repo.snapshot(a).main().map(s -> {
					List<Item> l = new ArrayList<>(3);
					for (Kind k : new Kind[]{Kind.LIVE, Kind.MOVIE, Kind.SERIES}) {
						if (s.size(k) > 0) l.add(sectionFolder(a, k));
					}
					return l;
				});
			case SECTION: {
				Kind kind = Kind.of(f.args[0]);
				return repo.snapshot(a).main().map(s -> {
					List<Item> l = new ArrayList<>();
					for (Category c : s.rows(kind).keySet()) l.add(categoryFolder(a, c));
					return l;
				});
			}
			case CATEGORY: {
				Kind kind = Kind.of(f.args[0]);
				String cat = f.args[1];
				return repo.snapshot(a).main().map(s -> {
					List<Entry> entries = s.inCategory(kind, cat);
					List<Item> l = new ArrayList<>(entries.size());
					for (Entry e : entries) {
						l.add((e instanceof Series se) ? seriesFolder(a, s, se) : playable(a, s, e));
					}
					return l;
				});
			}
			case SERIES: {
				String seriesId = f.args[0];
				return repo.snapshot(a).then(s -> {
					Entry e = s.get(Kind.SERIES, seriesId);
					if (!(e instanceof Series se)) return completedEmptyList();
					return repo.run(() -> repo.catalog(a).details(se)).main().map(d -> {
						List<Item> l = new ArrayList<>();
						for (Details.Season season : d.seasons) {
							for (Details.Episode ep : season.episodes) l.add(episode(a, f, se.name, se.image, ep));
						}
						return l;
					});
				});
			}
			default:
				return completedEmptyList();
		}
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<Item> l = new ArrayList<>();
		for (Account a : repo.getAccounts()) l.add(accountFolder(a));
		return completed(l);
	}

	// --- Ids back to items ---

	@Nullable
	FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return XtreamIds.ROOT.equals(id) ? completed(this) : null;
		if (!XtreamIds.SCHEME.equals(scheme)) return null;
		Item cached = lib.getCachedItem(id);
		if (cached != null) return completed(cached);
		String[] p = XtreamIds.parse(id);
		if (p == null) return completedNull();
		Account a = repo.getAccount(p[1]);
		if (a == null) return completedNull();

		switch (p[0]) {
			case "a":
				return completed(accountFolder(a));
			case "s": {
				Kind kind = (p.length > 2) ? Kind.of(p[2]) : null;
				return (kind == null) ? completedNull() : completed(sectionFolder(a, kind));
			}
			case "c": {
				if (p.length < 4) return completedNull();
				Kind kind = Kind.of(p[2]);
				return repo.snapshot(a).main().map(s -> {
					Category c = s.category(kind, p[3]);
					return (c != null) ? categoryFolder(a, c) : null;
				});
			}
			case "S":
				if (p.length < 3) return completedNull();
				return repo.snapshot(a).main().map(s -> {
					Entry e = s.get(Kind.SERIES, p[2]);
					return (e instanceof Series se) ? seriesFolder(a, s, se) : null;
				});
			case "p": {
				if (p.length < 4) return completedNull();
				Kind kind = Kind.of(p[2]);
				return repo.snapshot(a).main().map(s -> {
					Entry e = s.get(kind, p[3]);
					return (e != null) ? playable(a, s, e) : fromLibrary(a, kind, p[3], p);
				}).ifFail(err -> fromLibrary(a, kind, p[3], p));
			}
			case "e": {
				if (p.length < 6) return completedNull();
				String seriesId = p[2];
				String epId = p[4];
				return repo.snapshot(a).then(s -> {
					Entry e = s.get(Kind.SERIES, seriesId);
					if (!(e instanceof Series se)) return completed(fromLibrary(a, Kind.EPISODE, epId, p));
					return repo.run(() -> repo.catalog(a).details(se)).main().map(d -> {
						for (Details.Season season : d.seasons) {
							for (Details.Episode ep : season.episodes) {
								if (ep.id.equals(epId)) return episode(a, s, se, ep);
							}
						}
						return fromLibrary(a, Kind.EPISODE, epId, p);
					});
				}).ifFail(err -> fromLibrary(a, Kind.EPISODE, epId, p));
			}
			default:
				return completedNull();
		}
	}

	// Without the catalog (offline, cache cleared): from what the user library remembers
	@Nullable
	private XtreamPlayable fromLibrary(Account a, Kind kind, String streamId, String[] p) {
		// The stream of a playlist entry is only in the list
		if ((kind == null) || (a.xtream == null)) return null;
		UserLibrary.Record r = repo.getLibrary().get(a.id, kind, streamId);
		if (r == null) return null;
		String ext = r.extension;
		BrowsableItem parent = sectionFolder(a, (kind == Kind.EPISODE) ? Kind.SERIES : kind);
		String url = switch (kind) {
			case LIVE -> a.xtream.liveUrl(streamId);
			case MOVIE -> a.xtream.movieUrl(streamId, ext);
			default -> a.xtream.episodeUrl(streamId, ext);
		};
		String id = (kind == Kind.EPISODE) ?
				XtreamIds.episode(a.id, r.seriesId, r.season, streamId, ext) :
				XtreamIds.playable(a.id, kind, streamId, ext);
		return (XtreamPlayable) lib.getOrCreateCachedItem(id,
				i -> new XtreamPlayable(i, parent, url, a.id, kind, streamId, r.title, r.subtitle, r.image,
						r.duration, r.seriesId, r.season, r.episode, ext));
	}

	// --- Root plumbing, as the TV root does ---

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(me.aap.fermata.R.string.addon_name_xtream);
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_xtream));
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return buildTitle();
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}
}
