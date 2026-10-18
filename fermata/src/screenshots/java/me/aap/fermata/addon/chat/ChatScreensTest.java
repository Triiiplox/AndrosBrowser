package me.aap.fermata.addon.chat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.screens.Screens;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, application = FermataApplication.class)
public class ChatScreensTest {

	@Test
	@Config(qualifiers = "pt-rBR-w800dp-h400dp-land-mdpi")
	public void car() throws Exception {
		shoot("car");
	}

	@Test
	@Config(qualifiers = "pt-rBR-w393dp-h852dp-port-xxhdpi")
	public void phone() throws Exception {
		shoot("phone");
	}

	private static void shoot(String size) throws Exception {
		MainActivity a = Screens.open().get();
		AddonManager.get().getOrInstallAddon("chat");
		Screens.settle();
		MainActivityDelegate d = MainActivityDelegate.get(a);
		d.showFragment(me.aap.fermata.R.id.chat_addon);
		View root = a.getWindow().getDecorView();
		Screens.save(root, size + "-chat-empty");

		ChatGpt gpt = ChatGpt.getInstance();
		gpt.addMessage("Qual o caminho mais rápido para o centro agora?", ChatGpt.Role.USER);
		gpt.addMessage("Pela Marginal Pinheiros o trânsito está livre até a Ponte Estaiada. " +
				"Depois siga pela Av. Paulista. Leva cerca de 25 minutos.", ChatGpt.Role.ASSISTANT);
		gpt.addMessage("E tem posto no caminho?", ChatGpt.Role.USER);
		Screens.settle();
		ChatFragment f = (ChatFragment) d.getActiveFragment();
		View screen = f.requireView();
		ChatListView list = find(screen, ChatListView.class);
		list.getAdapter().notifyDataSetChanged();
		list.setWaiting(true);
		Screens.save(root, size + "-chat-conversation");

		EditText text = find(screen, EditText.class);
		text.setText("Toca uma música tranquila");
		Screens.save(root, size + "-chat-typing");
		text.setText("");

		VoiceOverlay voice = (VoiceOverlay) field(screen, "voice");
		voice.show();
		voice.setState(VoiceOverlay.State.LISTENING);
		voice.setCaption("Qual o caminho mais rápido para…");
		voice.setLevel(6f);
		Screens.save(root, size + "-chat-voice");
		voice.setState(VoiceOverlay.State.PAUSED);
		Screens.save(root, size + "-chat-voice-paused");
	}

	@SuppressWarnings("unchecked")
	private static <V extends View> V find(View v, Class<V> c) {
		if (c.isInstance(v)) return (V) v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				V found = find(g.getChildAt(i), c);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static Object field(Object o, String name) throws ReflectiveOperationException {
		Field fl = o.getClass().getDeclaredField(name);
		fl.setAccessible(true);
		return fl.get(o);
	}
}
