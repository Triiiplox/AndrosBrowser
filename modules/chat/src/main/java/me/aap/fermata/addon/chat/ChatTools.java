package me.aap.fermata.addon.chat;

import static me.aap.utils.async.Completed.completed;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * What ChatGPT can do in the app (function calling): play something by its name, control the
 * playback, open a screen, tell what is playing. It goes through the voice control of the screens,
 * as "play Globo in IPTV" said to it, so it reaches the other addons. The answers are facts for
 * the model to word: what started playing, or that nothing did. Main thread only.
 */
final class ChatTools {
	// A channel or a video takes a moment to start: what is playing is told once it changed
	private static final long PLAY_WAIT = 6000;
	private static final long PLAY_POLL = 250;
	private final MainActivityDelegate activity;
	// Something was done (played, paused, a screen opened): the answer needs no voice of its own
	private boolean acted;
	// The playback was paused or stopped on purpose: the voice mode must not resume it
	private boolean paused;

	ChatTools(MainActivityDelegate activity) {
		this.activity = activity;
	}

	boolean hasActed() {
		return acted;
	}

	boolean hasPaused() {
		return paused;
	}

	static String systemPrompt() {
		return "Você é o assistente do Fermata Auto, um app de mídia usado no carro e no celular. " +
				"Responda curto, em frases para serem ouvidas, na língua do usuário. Quando ele pedir " +
				"para tocar, pausar, trocar, abrir uma tela ou perguntar o que está tocando, use as " +
				"funções em vez de explicar como fazer. Diga só o que a função confirmou.";
	}

	JSONArray definitions() throws JSONException {
		JSONArray a = new JSONArray();
		a.put(function("play", "Toca pelo nome: um canal de TV, um filme ou uma série do IPTV, " +
						"música e vídeos das pastas e dos favoritos. No YouTube, abre a busca com os resultados " +
						"(o vídeo não começa sozinho).",
				new JSONObject()
						.put("query", new JSONObject().put("type", "string")
								.put("description", "O nome, como o usuário disse: \"Globo\", \"Duna\", \"Coldplay\""))
						.put("where", enumOf("Onde procurar. iptv para canais, filmes e séries.",
								"iptv", "youtube", "folders", "favorites", "playlists")),
				"query", "where"));
		a.put(function("control", "Controla o que está tocando.",
				new JSONObject().put("action", enumOf("A ação.", "play", "pause", "stop", "next",
						"previous")), "action"));
		a.put(function("open", "Abre uma tela do app.",
				new JSONObject().put("screen", enumOf("A tela.", "iptv", "youtube", "folders",
						"favorites", "playlists", "settings")), "screen"));
		a.put(function("now_playing", "Diz o que está tocando agora, e se está tocando ou pausado.",
				new JSONObject()));
		return a;
	}

	/** The result of a call, as text for the model. Never fails: an error is a result too. */
	FutureSupplier<String> call(String name, @Nullable JSONObject args) {
		try {
			Log.i("ChatGPT calls ", name, " ", args);
			return switch (name) {
				case "play" -> play(args.getString("query"), args.getString("where"));
				case "control" -> completed(control(args.getString("action")));
				case "open" -> completed(open(args.getString("screen")));
				case "now_playing" -> completed(nowPlaying());
				default -> completed("Função desconhecida: " + name);
			};
		} catch (Exception ex) {
			Log.e(ex, "ChatGPT: the call failed: ", name);
			return completed("Erro: " + ex.getMessage());
		}
	}

	private FutureSupplier<String> play(String query, String where) {
		int id = screen(where);
		if (!isAvailable(id)) return completed("Indisponível neste app: " + where);
		acted = true;
		PlayableItem before = activity.getMediaServiceBinder().getCurrentItem();
		String lastBefore = activity.getLib().getPrefs().getLastPlayedItemPref();
		boolean wasPlaying = isStarting(activity.getMediaSessionCallback().getPlaybackState());
		activity.voiceCommand(id, new VoiceCommand(query, VoiceCommand.ACTION_PLAY));
		if (id == R.id.youtube_fragment) {
			return completed("Abri a busca do YouTube por \"" + query + "\". O usuário escolhe o vídeo.");
		}
		Promise<String> p = new Promise<>();
		waitPlaying(before, lastBefore, wasPlaying, query, p, System.currentTimeMillis() + PLAY_WAIT);
		return p;
	}

	// Started: the player has another item, or (while its engine starts) the last played changed,
	// or the same item, stopped before, plays again
	private void waitPlaying(@Nullable PlayableItem before, @Nullable String lastBefore,
													 boolean wasPlaying, String query, Promise<String> p, long until) {
		PlayableItem cur = activity.getMediaServiceBinder().getCurrentItem();
		String last = activity.getLib().getPrefs().getLastPlayedItemPref();
		boolean started = !wasPlaying && isStarting(activity.getMediaSessionCallback().getPlaybackState());
		if ((cur != null) && (cur != before)) {
			p.complete("Tocando: " + describe(cur));
		} else if (started || ((last != null) && !last.equals(lastBefore))) {
			activity.getLib().getLastPlayedItem().main().onCompletion((i, err) ->
					p.complete((i != null) ? "Tocando: " + describe(i) : "Começou a tocar."));
		} else if (System.currentTimeMillis() >= until) {
			p.complete("Nada começou a tocar para \"" + query + "\". A busca ficou aberta na tela.");
		} else {
			activity.postDelayed(() -> waitPlaying(before, lastBefore, wasPlaying, query, p, until),
					PLAY_POLL);
		}
	}

	private static boolean isStarting(PlaybackStateCompat st) {
		int s = st.getState();
		return (s == PlaybackStateCompat.STATE_PLAYING) || (s == PlaybackStateCompat.STATE_BUFFERING) ||
				(s == PlaybackStateCompat.STATE_CONNECTING);
	}

	private String control(String action) {
		MediaSessionCallback cb = activity.getMediaSessionCallback();
		acted = true;
		switch (action) {
			case "play" -> cb.onPlay();
			case "pause" -> {
				paused = true;
				cb.onPause();
			}
			case "stop" -> {
				paused = true;
				cb.onStop();
			}
			case "next" -> cb.onSkipToNext();
			case "previous" -> cb.onSkipToPrevious();
			default -> {
				acted = false;
				return "Ação desconhecida: " + action;
			}
		}
		return "Feito: " + action;
	}

	private String open(String screen) {
		int id = screen(screen);
		if (!isAvailable(id)) return "Indisponível neste app: " + screen;
		acted = true;
		activity.showFragment(id);
		return "Aberta: " + screen;
	}

	private String nowPlaying() {
		PlayableItem i = activity.getMediaServiceBinder().getCurrentItem();
		if (i == null) return "Nada tocando.";
		int st = activity.getMediaSessionCallback().getPlaybackState().getState();
		String state = (st == PlaybackStateCompat.STATE_PLAYING) ? "tocando" :
				(st == PlaybackStateCompat.STATE_PAUSED) ? "pausado" : "parado";
		return describe(i) + " (" + state + ")";
	}

	// The name, and what the item shows under it: the category, the program on air, the artist
	private static String describe(PlayableItem i) {
		MediaMetadataCompat md = i.getMediaData().peek();
		String sub = (md == null) ? null : md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
		return ((sub == null) || sub.isEmpty()) ? i.getName() : i.getName() + " — " + sub;
	}

	// The library screens are part of the app; the others come with their addon
	private static boolean isAvailable(int id) {
		if (id == 0) return false;
		boolean addon = (id == R.id.xtream_fragment) || (id == R.id.youtube_fragment);
		return !addon || AddonManager.get().hasAddon(id);
	}

	private static int screen(String name) {
		return switch (name) {
			case "iptv" -> R.id.xtream_fragment;
			case "youtube" -> R.id.youtube_fragment;
			case "folders" -> R.id.folders_fragment;
			case "favorites" -> R.id.favorites_fragment;
			case "playlists" -> R.id.playlists_fragment;
			case "settings" -> R.id.settings_fragment;
			default -> 0;
		};
	}

	private static JSONObject function(String name, String description, JSONObject props,
																		 String... required) throws JSONException {
		JSONObject params = new JSONObject().put("type", "object").put("properties", props)
				.put("required", new JSONArray(required)).put("additionalProperties", false);
		return new JSONObject().put("type", "function").put("function", new JSONObject()
				.put("name", name).put("description", description).put("parameters", params));
	}

	private static JSONObject enumOf(String description, String... values) throws JSONException {
		return new JSONObject().put("type", "string").put("description", description)
				.put("enum", new JSONArray(values));
	}
}
