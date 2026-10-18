package me.aap.fermata.addon.chat;

import static me.aap.fermata.util.Utils.openUrl;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.utils.app.App;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class ChatAddon implements FermataFragmentAddon {
	private static final AddonInfo info = FermataAddon.findAddonInfo(ChatAddon.class.getName());
	private static final Pref<Supplier<String>> OPENAI_KEY = Pref.s("OPENAI_KEY", "");
	// The list of old builds, kept for the choice saved by them; the list shown comes from the
	// account itself (OpenAI's /v1/models), so it does not get old
	private static final String[] MODELS =
			new String[]{"gpt-4o", "chatgpt-4o-latest", "gpt-4o-mini", "o1", "o1-mini", "o1-preview"};
	// Shown only until the account answers, or if it cannot be reached
	private static final String[] CURRENT_MODELS =
			new String[]{"gpt-6.1-sol", "gpt-6-sol", "gpt-6-luna", "gpt-5.5", "gpt-5-mini"};
	private static final String DEFAULT_MODEL = "gpt-5-mini";
	private static final Pref<IntSupplier> MODEL = Pref.i("MODEL", 2);
	private static final Pref<Supplier<String>> MODEL_ID = Pref.s("MODEL_ID", "");
	private static final Pref<Supplier<String[]>> MODELS_FOUND =
			Pref.sa("MODELS_FOUND", new String[0]);
	private static final Pattern NOT_CHAT = Pattern.compile(
			"audio|realtime|tts|transcribe|image|embedding|search|moderation|instruct|codex|dall-e|" +
					"whisper|-\\d{4}-\\d{2}-\\d{2}$");
	private static final Pref<Supplier<String>> MODEL_OTHER = Pref.s("MODEL_OTHER", "");
	private static final Pref<Supplier<String>> CHAT_LANG =
			Pref.s("CHAT_LANG", () -> Locale.getDefault().toLanguageTag());

	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.chat_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new ChatFragment();
	}

	/** The official ChatGPT app, for its voice mode on the user's own plan. */
	public static final String CHATGPT_APP = "com.openai.chatgpt";

	/**
	 * Opens the ChatGPT app, or its store page when it is not installed. Starting another app needs
	 * no package visibility, so nothing is declared for it.
	 */
	public static void openChatGptApp(Context ctx) {
		Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
				.setPackage(CHATGPT_APP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			ctx.startActivity(i);
		} catch (ActivityNotFoundException ex) {
			openUrl(ctx, "https://play.google.com/store/apps/details?id=" + CHATGPT_APP);
		}
	}

	/**
	 * The authorization of the requests: the user's API key. A sign-in with the ChatGPT account
	 * (OAuth, billed to the user's plan) would give its access token here, once OpenAI hands
	 * this app a client id.
	 */
	String getAuthorization() {
		String key = getOpenaiKey();
		return ((key == null) || (key = key.trim()).isEmpty()) ? null : "Bearer " + key;
	}

	public String getOpenaiKey() {
		return FermataApplication.get().getPreferenceStore().getStringPref(OPENAI_KEY);
	}

	public String getModel() {
		var ps = FermataApplication.get().getPreferenceStore();
		var other = ps.getStringPref(MODEL_OTHER).trim();
		if (!other.isEmpty()) return other;
		var id = ps.getStringPref(MODEL_ID).trim();
		if (!id.isEmpty()) return id;
		// A choice of an old build stays; without any, a current model, cheap and quick
		if (!ps.hasPref(MODEL)) return DEFAULT_MODEL;
		int i = ps.getIntPref(MODEL);
		return (i >= 0) && (i < MODELS.length) ? MODELS[i] : DEFAULT_MODEL;
	}

	public String getGetChatLang() {
		return FermataApplication.get().getPreferenceStore().getStringPref(CHAT_LANG);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addButton(o -> {
			o.title = R.string.open_chatgpt_app;
			o.subtitle = R.string.open_chatgpt_app_sub;
			o.onClick = () -> openChatGptApp(ctx);
		});
		set.addStringPref(o -> {
			String keyUrl = "https://platform.openai.com/api-keys";
			String sub = App.get().getString(R.string.openai_key_sub, keyUrl);
			o.store = store;
			o.pref = OPENAI_KEY;
			o.title = R.string.openai_key;
			o.csubtitle = HtmlCompat.fromHtml(sub, HtmlCompat.FROM_HTML_MODE_COMPACT);
			o.clickListener = v -> openUrl(v.getContext(), keyUrl);
		});
		set.addListPref(o -> {
			o.setStringValues(store, MODEL_ID, modelChoices(store));
			o.initList = l -> {
				l.setStringValues(store, MODEL_ID, modelChoices(store));
				if (store.getStringArrayPref(MODELS_FOUND).length == 0) refreshModels(store);
			};
			o.title = R.string.openai_model;
			o.subtitle = me.aap.fermata.R.string.string_format;
			o.formatSubtitle = true;
		});
		refreshModels(store);
		set.addStringPref(o -> {
			o.store = store;
			o.pref = MODEL_OTHER;
			o.title = R.string.openai_model_other;
			o.hint = R.string.openai_model_other_hint;
		});
		set.addTtsLocalePref(o -> {
			o.store = store;
			o.pref = CHAT_LANG;
			o.title = me.aap.fermata.R.string.lang;
			o.subtitle = me.aap.fermata.R.string.string_format;
			o.formatSubtitle = true;
		});
	}

	/** The models of the account, newest first, with the one in use on top if it is not there. */
	private List<Pair<String, String>> modelChoices(PreferenceStore store) {
		List<String> ids = new ArrayList<>(Arrays.asList(store.getStringArrayPref(MODELS_FOUND)));
		if (ids.isEmpty()) ids.addAll(Arrays.asList(CURRENT_MODELS));
		String cur = getModel();
		if (!ids.contains(cur)) ids.add(0, cur);
		List<Pair<String, String>> l = new ArrayList<>(ids.size());
		for (String id : ids) l.add(new Pair<>(id, id));
		return l;
	}

	/**
	 * Asks OpenAI for the models the key can use and keeps the chat ones, newest first. Runs in the
	 * background; the list of the settings reads them each time it opens.
	 */
	private void refreshModels(PreferenceStore store) {
		String key = getOpenaiKey();
		if ((key == null) || (key = key.trim()).isEmpty()) return;
		String k = key;
		App.get().execute(() -> {
			HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/models")
					.openConnection();
			try {
				c.setConnectTimeout(15000);
				c.setReadTimeout(30000);
				c.setRequestProperty("Authorization", "Bearer " + k);
				c.setRequestProperty("Accept", "application/json");
				int code = c.getResponseCode();
				if (code != HttpURLConnection.HTTP_OK) {
					Log.w("ChatGPT: the list of models was not loaded: HTTP ", code);
					return;
				}
				String json;
				try (InputStream in = c.getInputStream()) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
					json = out.toString("UTF-8");
				}
				String[] ids = chatModels(json);
				Log.i("ChatGPT: ", ids.length, " chat models in the account");
				if (ids.length > 0) App.get().run(() -> store.applyStringArrayPref(MODELS_FOUND, ids));
			} finally {
				c.disconnect();
			}
		}).onFailure(err -> Log.w("ChatGPT: the list of models was not loaded: ", err.getMessage()));
	}

	static String[] chatModels(String json) throws JSONException {
		JSONArray data = new JSONObject(json).getJSONArray("data");
		List<JSONObject> models = new ArrayList<>(data.length());
		for (int i = 0; i < data.length(); i++) {
			JSONObject m = data.getJSONObject(i);
			String id = m.optString("id");
			boolean chat = id.startsWith("gpt-") || id.startsWith("chatgpt-") || id.matches("o\\d.*");
			if (chat && !NOT_CHAT.matcher(id).find()) models.add(m);
		}
		models.sort((a, b) -> Long.compare(b.optLong("created"), a.optLong("created")));
		String[] ids = new String[models.size()];
		for (int i = 0; i < ids.length; i++) ids[i] = models.get(i).optString("id");
		return ids;
	}
}
