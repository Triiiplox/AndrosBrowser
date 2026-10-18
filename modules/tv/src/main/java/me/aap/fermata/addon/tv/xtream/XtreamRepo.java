package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.Catalog;
import com.triiiplox.iptv.M3uPlaylist;
import com.triiiplox.iptv.Snapshot;
import com.triiiplox.iptv.UserLibrary;
import com.triiiplox.iptv.XtreamAccount;
import com.triiiplox.iptv.net.HttpTransport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.log.Log;

/**
 * The Xtream accounts of the app, their catalogs and the user library. Accounts are kept in their
 * own preferences; the ones of the TV sources added as get.php links are imported once, so there
 * is nothing to type. Catalog work runs on two background threads of its own: parsing a big
 * panel takes seconds and must not hold the app's pool.
 */
public final class XtreamRepo {
	private static final String PREFS = "xtream";
	private static final String ACCOUNTS = "accounts";
	private static final String ACTIVE = "active";
	private static final String DISMISSED = "dismissed";
	private static volatile XtreamRepo instance;
	private final Context ctx;
	private final SharedPreferences prefs;
	private final Map<String, Catalog> catalogs = new HashMap<>();
	private final UserLibrary library;
	private final ExecutorService exec = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Xtream");
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private List<Account> accounts;

	private XtreamRepo(Context ctx) {
		this.ctx = ctx.getApplicationContext();
		prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		library = new UserLibrary(new File(this.ctx.getFilesDir(), "xtream/library.json"));
	}

	public static XtreamRepo get(Context ctx) {
		XtreamRepo r = instance;
		if (r != null) return r;
		synchronized (XtreamRepo.class) {
			if (instance == null) instance = new XtreamRepo(ctx);
			return instance;
		}
	}

	public UserLibrary getLibrary() {
		return library;
	}

	public synchronized List<Account> getAccounts() {
		if (accounts == null) accounts = loadAccounts();
		return Collections.unmodifiableList(accounts);
	}

	@Nullable
	public synchronized Account getAccount(String id) {
		for (Account a : getAccounts()) if (a.id.equals(id)) return a;
		return null;
	}

	/** The account last opened, or the first one, or null without accounts. */
	@Nullable
	public synchronized Account getActiveAccount() {
		List<Account> l = getAccounts();
		if (l.isEmpty()) return null;
		Account a = getAccount(prefs.getString(ACTIVE, ""));
		return (a != null) ? a : l.get(0);
	}

	public synchronized void setActiveAccount(Account a) {
		prefs.edit().putString(ACTIVE, a.id).apply();
	}

	public synchronized Account addAccount(XtreamAccount xa, String name) {
		return add(new Account(xa, name));
	}

	/** Adds a plain M3U playlist: a link alone, no login. */
	public synchronized Account addPlaylist(String url, String name) {
		return add(Account.playlist(url, name));
	}

	private Account add(Account a) {
		List<Account> l = new ArrayList<>(getAccounts());
		l.removeIf(x -> x.id.equals(a.id));
		l.add(a);
		accounts = l;
		Set<String> dismissed = new HashSet<>(prefs.getStringSet(DISMISSED, Collections.emptySet()));
		dismissed.remove(a.id);
		prefs.edit().putStringSet(DISMISSED, dismissed).apply();
		saveAccounts();
		return a;
	}

	public synchronized void removeAccount(Account a) {
		List<Account> l = new ArrayList<>(getAccounts());
		l.removeIf(x -> x.id.equals(a.id));
		accounts = l;
		// An imported account removed by the user is not imported again
		Set<String> dismissed = new HashSet<>(prefs.getStringSet(DISMISSED, Collections.emptySet()));
		dismissed.add(a.id);
		prefs.edit().putStringSet(DISMISSED, dismissed).apply();
		saveAccounts();
		Catalog c;
		synchronized (catalogs) {
			c = catalogs.remove(a.id);
		}
		if (c != null) exec.execute(c::clearCache);
	}

	/**
	 * Adds the TV sources: the get.php links as Xtream accounts, the other web links as playlists.
	 *
	 * @return the number of accounts added
	 */
	public FutureSupplier<Integer> importTvSources(TvRootItem tv) {
		return tv.getSources().main().map(sources -> {
			int added = 0;
			Set<String> dismissed = prefs.getStringSet(DISMISSED, Collections.emptySet());
			for (Map.Entry<String, String> src : sources.entrySet()) {
				String url = src.getKey();
				XtreamAccount xa = XtreamAccount.fromM3uUrl(url);
				String id = (xa != null) ? xa.id() : isWebLink(url) ? M3uPlaylist.accountId(url) : null;
				if ((id == null) || dismissed.contains(id) || (getAccount(id) != null)) continue;
				if (xa != null) addAccount(xa, xa.getHost());
				else addPlaylist(url, src.getValue());
				added++;
			}
			if (added > 0) Log.i("Xtream: imported ", added, " account(s) from the TV sources");
			return added;
		});
	}

	public Catalog catalog(Account a) {
		synchronized (catalogs) {
			Catalog c = catalogs.get(a.id);
			if (c == null) {
				File dir = new File(ctx.getCacheDir(), "xtream/" + a.id);
				c = (a.xtream != null) ? new Catalog(a.xtream, new HttpTransport(), dir) :
						Catalog.playlist(a.playlist, new HttpTransport(), dir);
				catalogs.put(a.id, c);
			}
			return c;
		}
	}

	/** The snapshot of an account, at once when it is loaded. */
	public FutureSupplier<Snapshot> snapshot(Account a) {
		Catalog c = catalog(a);
		Snapshot s = c.peek();
		if (s != null) return completed(s);
		return run(() -> {
			long start = System.currentTimeMillis();
			Snapshot snap = c.snapshot();
			Log.i("Xtream: catalog of ", a, " loaded in ", System.currentTimeMillis() - start,
					" ms: ", snap.size(com.triiiplox.iptv.model.Entry.Kind.LIVE), " channels, ",
					snap.size(com.triiiplox.iptv.model.Entry.Kind.MOVIE), " movies, ",
					snap.size(com.triiiplox.iptv.model.Entry.Kind.SERIES), " series");
			return snap;
		});
	}

	/** Runs a blocking task on the Xtream threads. */
	public <T> FutureSupplier<T> run(CheckedSupplier<T, Throwable> task) {
		Promise<T> p = new Promise<>();
		exec.execute(() -> {
			try {
				p.complete(task.get());
			} catch (Throwable ex) {
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	private List<Account> loadAccounts() {
		List<Account> l = new ArrayList<>();
		String json = prefs.getString(ACCOUNTS, null);
		if (json == null) return l;
		try {
			JSONArray a = new JSONArray(json);
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				if ("m3u".equals(o.optString("type"))) {
					l.add(Account.playlist(o.getString("url"), o.optString("name", null)));
					continue;
				}
				XtreamAccount xa = new XtreamAccount(o.getString("server"), o.getString("user"),
						o.getString("password"), o.optString("format", "ts"));
				l.add(new Account(xa, o.optString("name", xa.getHost())));
			}
		} catch (JSONException ex) {
			Log.e(ex, "Xtream: broken accounts preference");
		}
		return l;
	}

	private void saveAccounts() {
		JSONArray a = new JSONArray();
		try {
			for (Account acc : accounts) {
				JSONObject o = new JSONObject();
				if (acc.xtream == null) {
					o.put("type", "m3u");
					o.put("url", acc.playlist);
				} else {
					o.put("server", acc.xtream.getServer());
					o.put("user", acc.xtream.getUser());
					o.put("password", acc.xtream.getPassword());
					o.put("format", acc.xtream.getLiveFormat());
				}
				o.put("name", acc.name);
				a.put(o);
			}
		} catch (JSONException ex) {
			throw new IllegalStateException(ex);
		}
		prefs.edit().putString(ACCOUNTS, a.toString()).apply();
	}

	/** True for an http(s) link, the only playlists the app downloads. */
	public static boolean isWebLink(String url) {
		String u = url.trim().toLowerCase(Locale.ROOT);
		return u.startsWith("http://") || u.startsWith("https://");
	}

	/** An Xtream account, or a plain M3U playlist: exactly one of xtream and playlist is set. */
	public static final class Account {
		public final String id;
		public final String name;
		@Nullable
		public final XtreamAccount xtream;
		/** The link of a playlist. Like the passwords, never logged nor shown. */
		@Nullable
		public final String playlist;

		Account(XtreamAccount xtream, String name) {
			this(xtream, null, xtream.id(), name, xtream.getHost());
		}

		private Account(@Nullable XtreamAccount xtream, @Nullable String playlist, String id,
										@Nullable String name, String defaultName) {
			this.xtream = xtream;
			this.playlist = playlist;
			this.id = id;
			this.name = ((name == null) || name.trim().isEmpty()) ? defaultName : name.trim();
		}

		static Account playlist(String url, @Nullable String name) {
			String u = url.trim();
			return new Account(null, u, M3uPlaylist.accountId(u), name, host(u));
		}

		public boolean isPlaylist() {
			return playlist != null;
		}

		private static String host(String url) {
			try {
				String h = new java.net.URI(url).getHost();
				return (h != null) ? h : "M3U";
			} catch (Exception ex) {
				return "M3U";
			}
		}

		@Override
		public String toString() {
			return "Account{" + name + ", " + id + '}';
		}
	}
}
