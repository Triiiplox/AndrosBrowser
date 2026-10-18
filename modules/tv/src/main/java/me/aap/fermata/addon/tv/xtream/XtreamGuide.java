package me.aap.fermata.addon.tv.xtream;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.model.Program;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import me.aap.fermata.addon.tv.xtream.XtreamRepo.Account;
import me.aap.utils.log.Log;

/**
 * What is on now on the channels of the cards on the screen: one short guide request per channel,
 * asked one at a time (panels refuse requests made at once), the cards seen last first, and kept
 * until the program ends. Main thread only.
 */
final class XtreamGuide {
	// Cards scrolled away long ago are asked again when they show again
	private static final int MAX_PENDING = 24;
	private static final long NOTHING_TTL = 10 * 60_000;
	private final XtreamRepo repo;
	private final Account account;
	private final Map<String, Now> known = new HashMap<>();
	private final Map<String, List<Consumer<String>>> waiting = new HashMap<>();
	private final ArrayDeque<String> pending = new ArrayDeque<>();
	private boolean asking;
	private boolean closed;

	XtreamGuide(XtreamRepo repo, Account account) {
		this.repo = repo;
		this.account = account;
	}

	Account getAccount() {
		return account;
	}

	/**
	 * Calls back with the title on air, at once when known, or "" when the channel has none. A card
	 * rebound to another channel must ignore an answer for the former one.
	 */
	void now(String streamId, Consumer<String> cb) {
		if (closed) return;
		Now n = known.get(streamId);
		if ((n != null) && (System.currentTimeMillis() < n.until)) {
			cb.accept(n.title);
			return;
		}
		List<Consumer<String>> l = waiting.get(streamId);
		if (l == null) waiting.put(streamId, l = new ArrayList<>());
		l.add(cb);
		pending.remove(streamId);
		pending.addFirst(streamId);
		while (pending.size() > MAX_PENDING) waiting.remove(pending.removeLast());
		next();
	}

	void close() {
		closed = true;
		pending.clear();
		waiting.clear();
	}

	private void next() {
		if (asking || closed || pending.isEmpty()) return;
		String id = pending.removeFirst();
		asking = true;
		repo.run(() -> repo.catalog(account).epg(id, 2)).main().onCompletion((l, err) -> {
			asking = false;
			if (closed) return;
			if (err != null) Log.d("Xtream: no guide for ", id, ": ", err.getMessage());
			Now n = now(l, System.currentTimeMillis());
			known.put(id, n);
			List<Consumer<String>> cbs = waiting.remove(id);
			if (cbs != null) for (Consumer<String> cb : cbs) cb.accept(n.title);
			next();
		});
	}

	static Now now(@Nullable List<Program> programs, long nowMs) {
		long sec = nowMs / 1000;
		if (programs != null) {
			for (Program p : programs) {
				if (p.isOnAir(sec)) return new Now(p.title, p.end * 1000);
			}
		}
		return new Now("", nowMs + NOTHING_TTL);
	}

	static final class Now {
		final String title;
		final long until;

		Now(String title, long until) {
			this.title = title;
			this.until = until;
		}
	}
}
