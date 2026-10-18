package me.aap.fermata.addon.chat;

import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.chat.ChatListView.URL_PATTERN;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.text.TextUtils.isBlank;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
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
import me.aap.utils.function.Cancellable;
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
		Cancellable interrupt = getActivityDelegate().interruptPlayback();
		tts.speak(text).onCompletion((r, err) -> {
			releaseAudioFocus();
			if ((err != null) && !(err instanceof CancellationException))
				UiUtils.showAlert(requireContext(), getString(R.string.chat_tts_failed, String.valueOf(err)));
			interrupt.cancel();
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

	/** The screen: the conversation, an invitation while it is empty, and the message field. */
	private static final class ChatScreen extends LinearLayout {
		final ChatListView list;
		private final View empty;

		ChatScreen(Context ctx, ChatFragment f) {
			super(ctx);
			setOrientation(VERTICAL);
			FrameLayout content = new FrameLayout(ctx);
			list = new ChatListView(ctx);
			content.addView(list, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			empty = emptyView(ctx);
			content.addView(empty, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT,
					Gravity.CENTER));
			addView(content, new LayoutParams(MATCH_PARENT, 0, 1));
			addView(composer(ctx, f), new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		}

		void updateEmpty() {
			empty.setVisibility(ChatGpt.getInstance().getMessages().isEmpty() ? VISIBLE : GONE);
		}

		private static View emptyView(Context ctx) {
			LinearLayout l = new LinearLayout(ctx);
			l.setOrientation(VERTICAL);
			l.setGravity(Gravity.CENTER);
			int pad = toIntPx(ctx, 24);
			l.setPadding(pad, pad, pad, pad);
			TextView title = new TextView(ctx);
			title.setText(R.string.chat_empty_title);
			title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
			title.setTypeface(Typeface.DEFAULT_BOLD);
			title.setGravity(Gravity.CENTER);
			TextView sub = new TextView(ctx);
			sub.setText(R.string.chat_empty_text);
			sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			sub.setGravity(Gravity.CENTER);
			sub.setPadding(0, toIntPx(ctx, 8), 0, 0);
			l.addView(title);
			l.addView(sub);
			return l;
		}

		// A rounded field and one round button: the microphone while the field is empty, send
		// once something is typed
		private static View composer(Context ctx, ChatFragment f) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			int pad = toIntPx(ctx, 12);
			row.setPadding(pad, toIntPx(ctx, 6), pad, toIntPx(ctx, 10));

			EditText text = new EditText(ctx);
			text.setHint(R.string.chat_hint);
			text.setSingleLine(true);
			text.setImeOptions(EditorInfo.IME_ACTION_SEND);
			text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			int fg = text.getCurrentTextColor();
			GradientDrawable bg = new GradientDrawable();
			bg.setCornerRadius(toIntPx(ctx, 26));
			bg.setColor((fg & 0x00FFFFFF) | 0x1A000000);
			text.setBackground(bg);
			text.setPadding(toIntPx(ctx, 18), toIntPx(ctx, 12), toIntPx(ctx, 18), toIntPx(ctx, 12));
			row.addView(text, new LayoutParams(0, WRAP_CONTENT, 1));

			ImageButton button = new ImageButton(ctx);
			int size = toIntPx(ctx, 48);
			GradientDrawable round = new GradientDrawable();
			round.setShape(GradientDrawable.OVAL);
			round.setColor((fg & 0x00FFFFFF) | 0x26000000);
			button.setBackground(round);
			button.setImageResource(R.drawable.chat_mic);
			button.setScaleType(ImageView.ScaleType.CENTER);
			button.setContentDescription(ctx.getString(R.string.chat_speak));
			LayoutParams blp = new LayoutParams(size, size);
			blp.setMarginStart(toIntPx(ctx, 8));
			row.addView(button, blp);

			Runnable send = () -> {
				f.sendRequest(text.getText().toString(), false);
				text.setText("");
			};
			button.setOnClickListener(v -> {
				if (isBlank(text.getText().toString())) f.startVoiceAssistant();
				else send.run();
			});
			text.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					boolean typed = !isBlank(s.toString());
					button.setImageResource(typed ? R.drawable.send : R.drawable.chat_mic);
					button.setContentDescription(ctx.getString(typed ? R.string.chat_send :
							R.string.chat_speak));
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
			return row;
		}
	}

	private static final class FbMediator implements FloatingButton.Mediator {
		static final FbMediator instance = new FbMediator();

		@Override
		public void enable(FloatingButton b, ActivityFragment f) {
			b.setImageResource(me.aap.fermata.R.drawable.record_voice);
			b.setOnClickListener(v -> ((ChatFragment) f).startVoiceAssistant());
			b.setOnLongClickListener(v -> ((ChatFragment) f).startVoiceAssistant());
			// On the phone the microphone is in the message field; in the car the big round button
			// stays, easier to hit
			b.setSuppressed(!MainActivityDelegate.get(b.getContext()).isCarActivity());
		}
	}
}
