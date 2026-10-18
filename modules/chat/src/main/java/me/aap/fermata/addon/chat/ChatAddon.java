package me.aap.fermata.addon.chat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.fermata.util.Utils.openUrl;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.net.http.HttpHeader;
import me.aap.utils.net.http.HttpMethod;
import me.aap.utils.net.http.HttpStatusCode;
import me.aap.utils.function.Supplier;
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

	public String getOpenaiKey() {
		return FermataApplication.get().getPreferenceStore().getStringPref(OPENAI_KEY);
	}

	public String getModel() {
		var ps = FermataApplication.get().getPreferenceStore();
		var other = ps.getStringPref(MODEL_OTHER).trim();
		if (!other.isEmpty()) return other;
		var id = ps.getStringPref(MODEL_ID).trim();
		if (!id.isEmpty()) return id;
		int i = ps.getIntPref(MODEL);
		return (i >= 0) && (i < MODELS.length) ? MODELS[i] :
				MODELS[MODEL.getDefaultValue().getAsInt()];
	}

	public String getGetChatLang() {
		return FermataApplication.get().getPreferenceStore().getStringPref(CHAT_LANG);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
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
		if (ids.isEmpty()) ids.addAll(Arrays.asList(MODELS));
		String cur = getModel();
		if (!ids.contains(cur)) ids.add(0, cur);
		List<Pair<String, String>> l = new ArrayList<>(ids.size());
		for (String id : ids) l.add(new Pair<>(id, id));
		return l;
	}

	/**
	 * Asks OpenAI for the models the key can use and keeps the chat ones, newest first. The list
	 * of the settings shows them the next time it opens.
	 */
	private void refreshModels(PreferenceStore store) {
		String key = getOpenaiKey();
		if ((key == null) || (key = key.trim()).isEmpty()) return;
		String k = key;
		HttpConnection.connect(o -> {
			o.url("https://api.openai.com/v1/models");
			o.method = HttpMethod.GET;
			o.builder = b -> {
				b.addHeader(HttpHeader.ACCEPT);
				b.addHeader(HttpHeader.AUTHORIZATION, "Bearer " + k);
				return b.build();
			};
		}, (resp, err) -> {
			if ((err != null) || (resp.getStatusCode() != HttpStatusCode.OK)) {
				Log.w("ChatGPT: the list of models was not loaded: ",
						(err != null) ? err.getMessage() : resp.getReason());
				return completedVoid();
			}
			return resp.getPayload((bb, perr) -> {
				if (perr != null) return completedVoid();
				try {
					bb = IoUtils.getFrom(bb);
					JSONArray data = new JSONObject(new String(bb.array(), bb.arrayOffset(),
							bb.remaining(), UTF_8)).getJSONArray("data");
					List<JSONObject> models = new ArrayList<>(data.length());
					for (int i = 0; i < data.length(); i++) {
						JSONObject m = data.getJSONObject(i);
						String id = m.optString("id");
						boolean chat = id.startsWith("gpt-") || id.startsWith("chatgpt-") ||
								id.matches("o\\d.*");
						if (chat && !NOT_CHAT.matcher(id).find()) models.add(m);
					}
					models.sort((a, b) -> Long.compare(b.optLong("created"), a.optLong("created")));
					String[] ids = new String[models.size()];
					for (int i = 0; i < ids.length; i++) ids[i] = models.get(i).optString("id");
					if (ids.length > 0) App.get().run(() -> store.applyStringArrayPref(MODELS_FOUND, ids));
				} catch (JSONException ex) {
					Log.w("ChatGPT: unexpected list of models: ", ex.getMessage());
				}
				return completedVoid();
			});
		});
	}
}
