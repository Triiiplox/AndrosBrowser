package me.aap.fermata.addon.chat;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.chat.ChatListView.URL_PATTERN;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.text.TextUtils.isBlank;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.Manifest;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.util.Utils;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ToolBarView;
import me.aap.utils.voice.TextToSpeech;

/**
 * @author Andrey Pavlenko
 */
public class ChatFragment extends MainActivityFragment
		implements ChatGpt.Listener, PreferenceStore.Listener {
	private final ChatGpt chat = new ChatGpt();
	private TextToSpeech tts;
	private String chatLang;
	private Pattern openPattern;
	private AudioFocusRequestCompat audioFocusReq;
	// The voice mode: listen, answer aloud, listen again, until closed
	private SpeechRecognizer recognizer;
	private boolean voiceMode;
	private boolean muted;
	@Nullable
	private Cancellable voiceInterrupt;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.chat_addon;
	}

	@Nullable
	@org.jetbrains.annotations.Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return new ChatScreen(requireContext(), this);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		MainActivityDelegate.get(requireContext()).getPrefs().addBroadcastListener(this);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		MainActivityDelegate.get(requireContext()).getPrefs().removeBroadcastListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		ChatGpt gpt = ChatGpt.getInstance();
		List<ChatGpt.Message> messages = gpt.getMessages();
		gpt.addBroadcastListener(this);
		if (!messages.isEmpty()) getList().scrollToPosition(messages.size() - 1);
		getScreen().updateEmpty();
	}

	@Override
	public void onPause() {
		super.onPause();
		closeVoiceMode();
		if (recognizer != null) recognizer.destroy();
		recognizer = null;
		ChatGpt.getInstance().removeBroadcastListener(this);
		if (tts != null) tts.close();
		tts = null;
		chatLang = null;
	}

	@Override
	public void messageAdded(ChatGpt.Message msg, int index) {
		ChatListView.ChatListAdapter a = getList().getAdapter();
		if (a != null) a.messageAdded(index);
		getScreen().updateEmpty();
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ChatToolMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FbMediator.instance;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
				getList().scale(a.getTextIconSize());
			}
		});
	}

	@Override
	public boolean startVoiceAssistant() {
		ChatAddon a = AddonManager.get().getAddon(ChatAddon.class);
		if (a == null) return false;
		if (tts != null) tts.stop();
		MainActivityDelegate.get(getContext()).startSpeechRecognizer(a.getGetChatLang(), false)
				.onSuccess(q -> {
					if (q.isEmpty()) return;
					var msg = q.get(0);
					var addon = requireNonNull(AddonManager.get().getAddon(ChatAddon.class));
					var lang = addon.getGetChatLang();

					if ((openPattern == null) || !lang.equals(chatLang)) {
						var ctx = requireContext();
						var cfg = new Configuration(ctx.getResources().getConfiguration());
						cfg.setLocale(Locale.forLanguageTag(lang));
						var res = ctx.createConfigurationContext(cfg).getResources();
						openPattern = Pattern.compile(res.getString(me.aap.fermata.R.string.vcmd_action_open));
					}

					if (openPattern.matcher(msg).matches()) {
						var last = ChatGpt.getInstance().getLastMessages(1);
						if (!last.isEmpty()) {
							var content = last.get(0).content;
							var m = URL_PATTERN.matcher(content);
							if (m.find()) {
								var url = content.substring(m.start(), m.end());
								Utils.openUrl(requireContext(), url);
								return;
							}
						}
					}

					sendRequest(msg, true);
				});

		return true;
	}

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		sendRequest(cmd.getQuery(), true);
	}

	@Override
	public boolean onBackPressed() {
		if (voiceMode) {
			closeVoiceMode();
			return true;
		}
		return super.onBackPressed();
	}

	// --- Voice mode ---

	void openVoiceMode() {
		ChatAddon addon = AddonManager.get().getAddon(ChatAddon.class);
		if ((addon == null) || (addon.getAuthorization() == null)) {
			showNoKey();
			return;
		}
		MainActivityDelegate a = MainActivityDelegate.get(requireContext());
		FutureSupplier<int[]> check = a.isCarActivityNotMirror() ?
				completed(new int[]{PERMISSION_GRANTED}) :
				a.getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO);
		check.main().onSuccess(r -> {
			if (!isAdded()) return;
			if (r[0] != PERMISSION_GRANTED) {
				UiUtils.showAlert(requireContext(), me.aap.fermata.R.string.err_no_audio_record_perm);
				return;
			}
			if (tts != null) tts.stop();
			voiceMode = true;
			muted = false;
			if (voiceInterrupt == null) voiceInterrupt = a.interruptPlayback();
			getScreen().voice.show();
			listen();
		});
	}

	void closeVoiceMode() {
		if (!voiceMode) return;
		voiceMode = false;
		if (recognizer != null) recognizer.cancel();
		if (tts != null) tts.stop();
		if (getView() != null) getScreen().voice.hide();
		Cancellable c = voiceInterrupt;
		voiceInterrupt = null;
		if (c != null) c.cancel();
	}

	private void toggleMute() {
		if (!voiceMode) return;
		muted = !muted;
		if (muted) {
			if (recognizer != null) recognizer.cancel();
			if (tts != null) tts.stop();
			getScreen().voice.setState(VoiceOverlay.State.PAUSED);
		} else {
			listen();
		}
	}

	private void listen() {
		if (!voiceMode || muted || (getView() == null)) return;
		ChatAddon addon = AddonManager.get().getAddon(ChatAddon.class);
		if (addon == null) return;
		if (recognizer == null) {
			recognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
			recognizer.setRecognitionListener(new VoiceListener());
		}
		Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
		i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, addon.getGetChatLang());
		i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		getScreen().voice.setState(VoiceOverlay.State.LISTENING);
		recognizer.startListening(i);
	}

	private void voicePaused() {
		muted = true;
		if (getView() != null) getScreen().voice.setState(VoiceOverlay.State.PAUSED);
	}

	private final class VoiceListener implements RecognitionListener {
		@Override
		public void onReadyForSpeech(Bundle params) {
		}

		@Override
		public void onBeginningOfSpeech() {
		}

		@Override
		public void onRmsChanged(float rmsdB) {
			if (voiceMode && (getView() != null)) getScreen().voice.setLevel(rmsdB);
		}

		@Override
		public void onBufferReceived(byte[] buffer) {
		}

		@Override
		public void onEndOfSpeech() {
		}

		@Override
		public void onError(int error) {
			if (!voiceMode || muted) return;
			// Silence: keep listening. Anything else: wait for a tap on the microphone
			if ((error == SpeechRecognizer.ERROR_NO_MATCH) ||
					(error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
				listen();
			} else {
				Log.w("ChatGPT voice: recognition error ", error);
				voicePaused();
			}
		}

		@Override
		public void onResults(Bundle results) {
			if (!voiceMode || (getView() == null)) return;
			List<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			String text = ((r == null) || r.isEmpty()) ? null : r.get(0);
			if ((text == null) || isBlank(text)) {
				listen();
				return;
			}
			VoiceOverlay v = getScreen().voice;
			v.setState(VoiceOverlay.State.THINKING);
			v.setCaption(text);
			sendRequest(text, true);
		}

		@Override
		public void onPartialResults(Bundle partial) {
			if (!voiceMode || (getView() == null)) return;
			List<String> r = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			if ((r != null) && !r.isEmpty() && !isBlank(r.get(0))) getScreen().voice.setCaption(r.get(0));
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
		}
	}

	/** Writes what is said in the field, to read it before sending. */
	void dictate(EditText field) {
		ChatAddon addon = AddonManager.get().getAddon(ChatAddon.class);
		if (addon == null) return;
		MainActivityDelegate.get(requireContext()).startSpeechRecognizer(addon.getGetChatLang(), true)
				.onSuccess(q -> {
					if (q.isEmpty()) return;
					field.setText(q.get(0));
					field.setSelection(field.length());
				});
	}

	void showMoreMenu() {
		MainActivityDelegate a = MainActivityDelegate.get(requireContext());
		a.getContextMenu().show(b -> {
			b.addItem(R.id.chat_new, R.string.chat_new);
			b.addItem(R.id.open_chatgpt_app, R.string.open_chatgpt_app);
			b.setSelectionHandler(i -> {
				if (i.getItemId() == R.id.chat_new) newConversation();
				else ChatAddon.openChatGptApp(requireContext());
				return true;
			});
		});
	}

	private void newConversation() {
		ChatGpt.getInstance().clear();
		ChatListView.ChatListAdapter a = getList().getAdapter();
		if (a != null) a.notifyDataSetChanged();
		getScreen().updateEmpty();
	}

	// --- Screen ---

	private ChatScreen getScreen() {
		return (ChatScreen) requireView();
	}

	private ChatListView getList() {
		return getScreen().list;
	}

	void sendRequest(String text, boolean voice) {
		if ((text == null) || isBlank(text = text.trim())) return;
		ChatAddon chatAddon = AddonManager.get().getAddon(ChatAddon.class);
		if ((chatAddon == null) || (chatAddon.getAuthorization() == null)) {
			showNoKey();
			return;
		}
		ChatGpt gpt = ChatGpt.getInstance();
		gpt.addMessage(text, ChatGpt.Role.USER);
		getList().setWaiting(true);
		chat.sendRequest(gpt.getLastMessages(6)).onCompletion((resp, err) -> {
			if (!isAdded()) return;
			getList().setWaiting(false);
			if (err != null) {
				UiUtils.showAlert(requireContext(),
						getString(R.string.chat_failed, String.valueOf(err.getMessage())));
				if (voiceMode) voicePaused();
			} else {
				ChatGpt.getInstance().addMessage(resp, ChatGpt.Role.ASSISTANT);
				if (!voice) return;
				var addon = requireNonNull(AddonManager.get().getAddon(ChatAddon.class));
				var lang = addon.getGetChatLang();
				if ((tts == null) || !lang.equals(chatLang)) {
					if (tts != null) tts.close();
					tts = null;
					chatLang = lang;
					Locale locale = Locale.forLanguageTag(lang);
					TextToSpeech.create(requireContext(), locale).onCompletion((tts, terr) -> {
						if (terr != null) {
							UiUtils.showAlert(requireContext(), getString(R.string.chat_tts_failed, String.valueOf(terr)));
						} else {
							this.tts = tts;
							speak(resp);
						}
					});
				} else {
					speak(resp);
				}
			}
		});
	}

	/**
	 * Without a key there is nothing to send to: say how to get one, or use the ChatGPT app, which
	 * runs on the user's own plan.
	 */
	private void showNoKey() {
		MainActivityDelegate.get(requireContext()).createDialogBuilder()
				.setTitle(R.string.chat_no_key_title)
				.setMessage(R.string.chat_no_key)
				.setNegativeButton(android.R.string.ok, null)
				.setPositiveButton(R.string.open_chatgpt_app,
						(d, w) -> ChatAddon.openChatGptApp(requireContext())).show();
	}

	private void speak(String text) {
		assert tts != null;
		requestAudioFocus();
		// The voice mode holds the playback paused for all of its turns
		Cancellable interrupt = voiceMode ? null : getActivityDelegate().interruptPlayback();
		if (voiceMode && (getView() != null)) {
			VoiceOverlay v = getScreen().voice;
			v.setState(VoiceOverlay.State.SPEAKING);
			v.setCaption(text);
		}
		tts.speak(text).onCompletion((r, err) -> {
			releaseAudioFocus();
			if ((err != null) && !(err instanceof CancellationException))
				UiUtils.showAlert(requireContext(), getString(R.string.chat_tts_failed, String.valueOf(err)));
			if (interrupt != null) interrupt.cancel();
			if (voiceMode) listen();
		});
	}

	private void requestAudioFocus() {
		AudioManager am = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
		if (am == null) return;
		AudioManagerCompat.requestAudioFocus(am, getAudioFocusReq());
	}

	private void releaseAudioFocus() {
		AudioManager am = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
		if (am == null) return;
		AudioManagerCompat.abandonAudioFocusRequest(am, getAudioFocusReq());
	}

	public AudioFocusRequestCompat getAudioFocusReq() {
		if (audioFocusReq == null) {
			AudioAttributesCompat attrs = new AudioAttributesCompat.Builder()
					.setUsage(AudioAttributesCompat.USAGE_MEDIA)
					.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
					.build();
			audioFocusReq = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
					.setAudioAttributes(attrs)
					.setWillPauseWhenDucked(false)
					.setOnAudioFocusChangeListener(ignore -> {})
					.build();
		}
		return audioFocusReq;
	}

	// The message is typed at the bottom of the screen, as in chat apps: the tool bar keeps the
	// title and the button to the ChatGPT app
	private static final class ChatToolMediator implements ToolBarView.Mediator {
		static final ChatToolMediator instance = new ChatToolMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			dynCtx(tb.getContext());
			addButton(tb, R.drawable.chatgpt_app, v -> ChatAddon.openChatGptApp(v.getContext()),
					R.id.open_chatgpt_app, RIGHT);
			ToolBarView.Mediator.super.enable(tb, f);
		}
	}

	/**
	 * The screen: the conversation, an invitation while it is empty, the message bar at the bottom,
	 * and the voice mode over all of it.
	 */
	private static final class ChatScreen extends FrameLayout {
		private static final int VOICE_BLUE = 0xFF3D6FF0;
		final ChatListView list;
		final VoiceOverlay voice;
		private final View empty;

		ChatScreen(Context ctx, ChatFragment f) {
			super(ctx);
			LinearLayout main = new LinearLayout(ctx);
			main.setOrientation(LinearLayout.VERTICAL);
			FrameLayout content = new FrameLayout(ctx);
			list = new ChatListView(ctx);
			content.addView(list, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
			empty = emptyView(ctx);
			content.addView(empty, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER));
			main.addView(content, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
			main.addView(messageBar(ctx, f), new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			addView(main, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
			voice = new VoiceOverlay(ctx, new VoiceOverlay.Listener() {
				@Override
				public void onMuteToggled() {
					f.toggleMute();
				}

				@Override
				public void onClose() {
					f.closeVoiceMode();
				}
			});
			addView(voice, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		}

		void updateEmpty() {
			empty.setVisibility(ChatGpt.getInstance().getMessages().isEmpty() ? VISIBLE : GONE);
		}

		private static View emptyView(Context ctx) {
			TextView title = new TextView(ctx);
			title.setText(R.string.chat_empty_title);
			title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
			title.setTypeface(Typeface.DEFAULT_BOLD);
			title.setGravity(Gravity.CENTER);
			int pad = toIntPx(ctx, 24);
			title.setPadding(pad, pad, pad, pad);
			return title;
		}

		/*
		 * One pill, as in the ChatGPT app: "+" (new conversation, the ChatGPT app), the field, the
		 * microphone that writes what is said, and a round blue button: the voice mode while the
		 * field is empty, send once something is typed.
		 */
		private static View messageBar(Context ctx, ChatFragment f) {
			LinearLayout outer = new LinearLayout(ctx);
			int op = toIntPx(ctx, 12);
			outer.setPadding(op, toIntPx(ctx, 6), op, toIntPx(ctx, 10));

			LinearLayout pill = new LinearLayout(ctx);
			pill.setOrientation(LinearLayout.HORIZONTAL);
			pill.setGravity(Gravity.CENTER_VERTICAL);
			pill.setMinimumHeight(toIntPx(ctx, 56));
			int pp = toIntPx(ctx, 6);
			pill.setPadding(pp, pp, pp, pp);
			EditText text = new EditText(ctx);
			int fg = text.getCurrentTextColor();
			GradientDrawable bg = new GradientDrawable();
			bg.setCornerRadius(toIntPx(ctx, 30));
			bg.setColor((fg & 0x00FFFFFF) | 0x1A000000);
			bg.setStroke(toIntPx(ctx, 1), (fg & 0x00FFFFFF) | 0x22000000);
			pill.setBackground(bg);
			outer.addView(pill, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

			ImageButton plus = iconButton(ctx, R.drawable.chat_add, R.string.chat_more);
			plus.setOnClickListener(v -> f.showMoreMenu());
			pill.addView(plus, new LinearLayout.LayoutParams(toIntPx(ctx, 44), toIntPx(ctx, 44)));

			text.setHint(R.string.chat_hint);
			text.setSingleLine(true);
			text.setImeOptions(EditorInfo.IME_ACTION_SEND);
			text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
			text.setBackground(null);
			text.setPadding(toIntPx(ctx, 6), 0, toIntPx(ctx, 6), 0);
			pill.addView(text, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

			ImageButton dictate = iconButton(ctx, R.drawable.chat_mic, R.string.chat_dictate);
			dictate.setOnClickListener(v -> f.dictate(text));
			pill.addView(dictate, new LinearLayout.LayoutParams(toIntPx(ctx, 44), toIntPx(ctx, 44)));

			ImageButton action = new ImageButton(ctx);
			GradientDrawable round = new GradientDrawable();
			round.setShape(GradientDrawable.OVAL);
			round.setColor(VOICE_BLUE);
			action.setBackground(round);
			action.setImageResource(R.drawable.chat_voice);
			action.setImageTintList(ColorStateList.valueOf(Color.WHITE));
			action.setScaleType(ImageView.ScaleType.CENTER);
			action.setContentDescription(ctx.getString(R.string.chat_voice_mode));
			int size = toIntPx(ctx, 44);
			LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(size, size);
			alp.setMarginStart(toIntPx(ctx, 4));
			pill.addView(action, alp);

			Runnable send = () -> {
				f.sendRequest(text.getText().toString(), false);
				text.setText("");
			};
			action.setOnClickListener(v -> {
				if (isBlank(text.getText().toString())) f.openVoiceMode();
				else send.run();
			});
			text.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					boolean typed = !isBlank(s.toString());
					action.setImageResource(typed ? R.drawable.chat_send_up : R.drawable.chat_voice);
					action.setContentDescription(ctx.getString(typed ? R.string.chat_send :
							R.string.chat_voice_mode));
					dictate.setVisibility(typed ? GONE : VISIBLE);
				}

				@Override
				public void afterTextChanged(Editable s) {
				}
			});
			text.setOnEditorActionListener((v, actionId, event) -> {
				boolean enter = (event != null) && (event.getAction() == KeyEvent.ACTION_DOWN) &&
						((event.getKeyCode() == KEYCODE_ENTER) || (event.getKeyCode() == KEYCODE_NUMPAD_ENTER));
				if ((actionId != EditorInfo.IME_ACTION_SEND) && !enter) return false;
				send.run();
				return true;
			});
			return outer;
		}

		private static ImageButton iconButton(Context ctx, int icon, int description) {
			ImageButton b = new ImageButton(ctx);
			b.setBackground(null);
			b.setImageResource(icon);
			b.setScaleType(ImageView.ScaleType.CENTER);
			b.setContentDescription(ctx.getString(description));
			return b;
		}
	}

	private static final class FbMediator implements FloatingButton.Mediator {
		static final FbMediator instance = new FbMediator();

		@Override
		public void enable(FloatingButton b, ActivityFragment f) {
			b.setImageResource(me.aap.fermata.R.drawable.record_voice);
			b.setOnClickListener(v -> ((ChatFragment) f).openVoiceMode());
			b.setOnLongClickListener(v -> ((ChatFragment) f).startVoiceAssistant());
			// On the phone the microphone is in the message field; in the car the big round button
			// stays, easier to hit
			b.setSuppressed(!MainActivityDelegate.get(b.getContext()).isCarActivity());
		}
	}
}
