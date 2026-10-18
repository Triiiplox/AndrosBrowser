package me.aap.fermata.addon.tv.xtream;

import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.recyclerview.widget.RecyclerView;

import com.triiiplox.iptv.XtreamAccount;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.screens.Screens;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * Taps through the app as a user would: the nav bar, the IPTV tabs and cards, Back from the
 * details, the ChatGPT bar and its voice mode, adding a folder, the settings. Each step is written
 * to build/screenshots/<size>-navigation.txt as OK or FAIL, the key ones as screenshots.
 */
public abstract class NavigationTest {

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w800dp-h400dp-land-mdpi")
	public static class Car {
		@Test
		public void tapThrough() throws Exception {
			new Run("car").all();
		}
	}

	@RunWith(RobolectricTestRunner.class)
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	@Config(sdk = 35, application = FermataApplication.class,
			qualifiers = "pt-rBR-w393dp-h852dp-port-xxhdpi")
	public static class Phone {
		@Test
		public void tapThrough() throws Exception {
			new Run("phone").all();
		}
	}

	static final class Run {
		final String size;
		final List<String> report = new ArrayList<>();
		MainActivity a;
		MainActivityDelegate d;
		View root;
		int fails;
		XtreamPlayable channel;

		Run(String size) {
			this.size = size;
		}

		void all() throws Exception {
			try (FakeXtreamServer panel = new FakeXtreamServer()) {
				a = Screens.open().get();
				d = MainActivityDelegate.get(a);
				root = a.getWindow().getDecorView();
				AddonManager.get().getOrInstallAddon("chat");
				AddonManager.get().getOrInstallAddon("me.aap.fermata.addon.web.yt.YoutubeAddon");
				XtreamRepo repo = XtreamRepo.get(a);
				repo.setActiveAccount(repo.addAccount(
						new XtreamAccount(panel.url, "andro", "senha", "ts"), "Meu IPTV"));
				Screens.settle();
				step(this::navBar, "barra de navegação");
				step(this::iptv, "IPTV");
				step(this::chat, "ChatGPT");
				step(this::steeringWheel, "Volante: três toques no ⏯");
				step(this::folders, "Pastas");
				step(this::settings, "Configurações");
				step(this::resume, "Retomar ao ligar o carro");
			} finally {
				write();
			}
			if (fails > 0) {
				throw new AssertionError(fails + " falha(s): build/screenshots/" + size + "-navigation.txt");
			}
		}

		// --- Nav bar: each button opens its screen ---

		void navBar() throws Exception {
			report.add("       barra logo após ativar os complementos: " + d.getNavBar().getChildCount() +
					" botões");
			d.showFragment(R.id.folders_fragment);
			Screens.waitReal(2);
			ViewGroup nav = d.getNavBar();
			List<View> navs = new ArrayList<>();
			collect(root, v -> v instanceof me.aap.utils.ui.view.NavBarView, navs);
			StringBuilder nb = new StringBuilder();
			for (View n : navs) {
				nb.append(n == nav ? "[getNavBar] " : "").append(((ViewGroup) n).getChildCount())
						.append(" filhos, visível ").append(n.isShown()).append("; ");
				if ((((ViewGroup) n).getChildCount() > 0) && n.isShown()) nav = (ViewGroup) n;
			}
			report.add("       barras na tela: " + nb);
			List<View> buttons = new ArrayList<>();
			for (int i = 0; i < nav.getChildCount(); i++) {
				View c = nav.getChildAt(i);
				if ((c.getId() != View.NO_ID) && (c.getVisibility() == View.VISIBLE)) buttons.add(c);
			}
			check(!buttons.isEmpty(), "botões na barra: " + buttons.size() + " " + names(buttons) +
					" (" + nav.getClass().getSimpleName() + ", " + nav.getChildCount() + " filhos, visível " +
					nav.isShown() + ")");
			for (View b : buttons) {
				String name = name(b.getId());
				if ((b.getId() == R.id.menu) || name.startsWith("nav_")) continue;
				// Robolectric's WebView claims features it lacks: the web pages are tested in
				// Chromium instead (the YouTube flow), only their buttons here
				if ((b.getId() == R.id.youtube_fragment) || (b.getId() == R.id.web_browser_fragment)) continue;
				b.performClick();
				Screens.waitReal(1);
				ActivityFragment f = d.getActiveFragment();
				check((f != null) && (f.getFragmentId() == b.getId()),
						"toque em " + name + " abre " + name(f));
			}
			View yt = nav.findViewById(R.id.youtube_fragment);
			check(yt != null, "botão do YouTube na barra");
		}

		// --- IPTV: tabs, a card opens the details, Back closes them ---

		void iptv() throws Exception {
			d.showFragment(R.id.xtream_fragment);
			XtreamFragment f = (XtreamFragment) d.getActiveFragment();
			Screens.waitFor(() -> field(f, "snapshot") != null, 20);
			Screens.waitReal(2);
			ViewGroup tabs = (ViewGroup) field(f, "tabs");
			tabs.getChildAt(2).performClick();
			Screens.waitReal(2);
			check(tabs.getChildAt(2).isSelected() && !tabs.getChildAt(0).isSelected(),
					"aba Filmes selecionada");

			RecyclerView page = (RecyclerView) field(f, "page");
			View card = first(page, v -> (v.findViewById(me.aap.fermata.addon.tv.R.id.xtream_card_image) != null)
					&& v.hasOnClickListeners() && (v.getParent() instanceof RecyclerView r) && (r != page));
			check(card != null, "um card de filme para tocar");
			if (card == null) return;
			card.performClick();
			Screens.waitReal(2);
			List<?> overlays = (List<?>) field(f, "overlays");
			check(overlays.size() == 1, "toque no card abre os detalhes");
			Screens.save(root, size + "-nav-iptv-details");

			View back = root.findViewById(me.aap.fermata.addon.tv.R.id.xtream_details_back);
			check((back != null) && back.isShown(), "botão ← dos detalhes visível");
			d.onBackPressed();
			Screens.waitReal(1);
			check(overlays.isEmpty() && (d.getActiveFragment() == f),
					"Voltar fecha os detalhes e fica no IPTV");

			card = first(page, v -> (v.findViewById(me.aap.fermata.addon.tv.R.id.xtream_card_image) != null)
					&& v.hasOnClickListeners() && (v.getParent() instanceof RecyclerView r) && (r != page));
			if (card != null) {
				card.performClick();
				Screens.waitReal(2);
				back = root.findViewById(me.aap.fermata.addon.tv.R.id.xtream_details_back);
				if (back != null) back.performClick();
				Screens.waitReal(1);
				check(overlays.isEmpty(), "botão ← fecha os detalhes");
			}

			// The steering wheel's next and previous: the channel after or before in its category
			com.triiiplox.iptv.Snapshot snap = (com.triiiplox.iptv.Snapshot) field(f, "snapshot");
			XtreamRepo.Account acc = (XtreamRepo.Account) field(f, "account");
			java.lang.reflect.Method rootOf = XtreamFragment.class.getDeclaredMethod("root");
			rootOf.setAccessible(true);
			XtreamRootItem tvRoot = (XtreamRootItem) rootOf.invoke(f);
			List<com.triiiplox.iptv.model.Entry> live = snap.entries(com.triiiplox.iptv.model.Entry.Kind.LIVE);
			com.triiiplox.iptv.model.Entry ch = live.get(0);
			List<com.triiiplox.iptv.model.Entry> sameCat = new ArrayList<>();
			for (com.triiiplox.iptv.model.Entry e : live) if (e.categoryId.equals(ch.categoryId)) sameCat.add(e);
			XtreamPlayable p = tvRoot.playable(acc, snap, ch);
			channel = p;
			me.aap.fermata.media.lib.MediaLib.PlayableItem next = get(p.getNextPlayable());
			check((next != null) && next.getName().equals(sameCat.get(1).name),
					"⏭ num canal toca o próximo da categoria (" + ch.name + " → " +
							((next == null) ? "nada" : next.getName()) + ")");
			me.aap.fermata.media.lib.MediaLib.PlayableItem prev = (next == null) ? null : get(next.getPrevPlayable());
			check((prev != null) && prev.getName().equals(ch.name), "⏮ volta ao canal anterior");
			XtreamPlayable last = tvRoot.playable(acc, snap, sameCat.get(sameCat.size() - 1));
			me.aap.fermata.media.lib.MediaLib.PlayableItem after = get(last.getNextPlayable());
			check(after != null, "⏭ no último canal da categoria passa para a seguinte (" +
					((after == null) ? "nada" : after.getName()) + ")");

			root.findViewById(me.aap.fermata.addon.tv.R.id.xtream_search_button).performClick();
			Screens.waitReal(1);
			check(overlays.size() == 1, "lupa abre a busca");
			d.onBackPressed();
			Screens.waitReal(1);
			check(overlays.isEmpty(), "Voltar fecha a busca");
		}

		// --- ChatGPT: no key, the voice mode and its buttons, Back ---

		void chat() throws Exception {
			d.showFragment(R.id.chat_addon);
			Screens.settle();
			View screen = ((ViewGroup) d.getActiveFragment().requireView()).getChildAt(0);
			View action = first(screen, v -> (v instanceof ImageButton) && v.isShown() &&
					getString(me.aap.fermata.addon.chat.R.string.chat_voice_mode).contentEquals(v.getContentDescription()));
			check(action != null, "botão azul do modo voz");
			if (action == null) return;

			action.performClick();
			Screens.settle();
			// Fermata's dialogs are drawn in its own menu, not as Android dialogs
			OverlayMenu dlg = d.getContextMenu();
			check(((View) dlg).isShown(), "sem chave: aviso de como conseguir a chave");
			Screens.save(root, size + "-nav-chat-nokey");
			dlg.hide();
			Screens.settle();

			PreferenceStore ps = FermataApplication.get().getPreferenceStore();
			ps.applyStringPref(PreferenceStore.Pref.s("OPENAI_KEY", ""), "sk-teste");
			Application app = (Application) a.getApplicationContext();
			shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
			action.performClick();
			Screens.waitReal(1);
			View voice = (View) field(screen, "voice");
			check(voice.isShown(), "com chave: botão azul abre o modo voz");
			Screens.save(root, size + "-nav-chat-voice");
			View close = first(voice, v -> (v instanceof ImageButton) &&
					getString(me.aap.fermata.addon.chat.R.string.chat_close_voice).contentEquals(v.getContentDescription()));
			if (close != null) close.performClick();
			Screens.settle();
			check(!voice.isShown(), "X fecha o modo voz");

			action.performClick();
			Screens.waitReal(1);
			boolean open = voice.isShown();
			d.onBackPressed();
			Screens.settle();
			check(open && !voice.isShown() && (d.getActiveFragment().getFragmentId() == R.id.chat_addon),
					"Voltar fecha o modo voz e fica no chat (aberto antes: " + open + ", depois: " +
							voice.isShown() + ", tela: " + name(d.getActiveFragment()) + ")");

			EditText text = (EditText) first(screen, v -> v instanceof EditText);
			text.setText("Oi");
			Screens.settle();
			check(getString(me.aap.fermata.addon.chat.R.string.chat_send).contentEquals(action.getContentDescription()),
					"com texto o botão azul vira Enviar");
			text.setText("");

			View plus = first(screen, v -> (v instanceof ImageButton) &&
					getString(me.aap.fermata.addon.chat.R.string.chat_more).contentEquals(v.getContentDescription()));
			if (plus != null) plus.performClick();
			Screens.settle();
			OverlayMenu menu = d.getContextMenu();
			check(((View) menu).isShown(), "\"+\" abre o menu (Nova conversa, app do ChatGPT)");
			Screens.save(root, size + "-nav-chat-menu");
			menu.hide();
			Screens.settle();

			// The account mode: the chatgpt.com site in place of the chat, and back
			me.aap.utils.pref.PreferenceStore.Pref<me.aap.utils.function.IntSupplier> mode =
					me.aap.utils.pref.PreferenceStore.Pref.i("CHAT_MODE", 0);
			ps.applyIntPref(mode, 1);
			Screens.waitReal(1);
			View content = ((ViewGroup) d.getActiveFragment().requireView()).getChildAt(0);
			android.webkit.WebView web = (android.webkit.WebView) first(content, v -> v instanceof android.webkit.WebView);
			check((web != null) && "https://chatgpt.com/".equals(shadowOf(web).getLastLoadedUrl()),
					"modo conta: abre o chatgpt.com (" + ((web == null) ? "sem WebView" :
							shadowOf(web).getLastLoadedUrl()) + ")");
			check(!d.getFloatingButton().isShown(), "modo conta: sem o botão redondo (o site tem o dele)");
			Screens.save(root, size + "-nav-chat-site");
			ps.applyIntPref(mode, 0);
			Screens.settle();
			content = ((ViewGroup) d.getActiveFragment().requireView()).getChildAt(0);
			check(first(content, v -> v instanceof EditText) != null, "volta ao chat com chave");
		}

		// --- Resume when the car starts: the channel of the last drive plays again ---

		void resume() throws Exception {
			check(channel != null, "um canal do IPTV para tocar");
			if (channel == null) return;
			me.aap.fermata.media.service.FermataServiceUiBinder b = d.getMediaServiceBinder();
			b.playItem(channel);
			Screens.waitReal(2);
			check(channel.equals(get(d.getLib().getLastPlayedItem())), "o canal fica como o último tocado");
			b.stop();
			Screens.waitReal(1);
			d.showFragment(R.id.folders_fragment);
			Screens.settle();
			// The car: what defaultIntent() does when the car's screen opens with nothing on it
			Field mirror = FermataApplication.class.getDeclaredField("mirroringMode");
			mirror.setAccessible(true);
			mirror.setInt(FermataApplication.get(), 1);
			try {
				check(d.isCarActivity(), "modo carro");
				// No video decoder here: the play request is what tells, not the playing
				List<Integer> states = new ArrayList<>();
				me.aap.fermata.media.service.MediaSessionCallback.Listener l = new me.aap.fermata.media.service.MediaSessionCallback.Listener() {
					@Override
					public void onPlaybackStateChanged(me.aap.fermata.media.service.MediaSessionCallback cb,
																						 android.support.v4.media.session.PlaybackStateCompat s) {
						states.add(s.getState());
					}
				};
				b.getMediaSessionCallback().addBroadcastListener(l);
				java.lang.reflect.Method m = MainActivityDelegate.class.getDeclaredMethod("resumeInCar");
				m.setAccessible(true);
				check((Boolean) m.invoke(d), "ao abrir no carro, retoma o último item");
				Screens.waitReal(3);
				ActivityFragment f = d.getActiveFragment();
				check(f instanceof XtreamFragment, "abre na tela do IPTV (tela: " + name(f) + ")");
				me.aap.fermata.ui.view.BodyLayout body = d.getBody();
				check(body.isVideoMode(), "a tela de vídeo fica aberta à espera da imagem (modo: " +
						body.getMode() + ")");
				// The surface Android would create for the visible video view
				if (field(body.getVideoView(), "createSurface") instanceof me.aap.utils.async.Promise<?> s) {
					s.complete(null);
				}
				Screens.waitReal(2);
				b.getMediaSessionCallback().removeBroadcastListener(l);
				check(states.contains(android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING)
								&& channel.equals(get(d.getLib().getLastPlayedItem())),
						"o canal é pedido para tocar de novo (estados: " + states + ", modo: " +
								d.getBody().getMode() + ", atual: " + b.getCurrentItem() + ")");
				Screens.save(root, size + "-nav-resume");
				b.stop();
				Screens.waitReal(1);
			} finally {
				mirror.setInt(FermataApplication.get(), 0);
			}
		}

		// --- Steering wheel: three presses of play/pause open ChatGPT's voice mode ---

		void steeringWheel() throws Exception {
			d.showFragment(R.id.folders_fragment);
			Screens.settle();
			for (int i = 0; i < 3; i++) {
				press(android.view.KeyEvent.ACTION_DOWN);
				Screens.waitReal(0);
				ShadowWait.ms(80);
				press(android.view.KeyEvent.ACTION_UP);
				ShadowWait.ms(120);
			}
			Screens.waitReal(2);
			ActivityFragment f = d.getActiveFragment();
			boolean chat = (f != null) && (f.getFragmentId() == R.id.chat_addon);
			View voice = chat ? (View) field(((ViewGroup) f.requireView()).getChildAt(0), "voice") : null;
			check(chat && (voice != null) && voice.isShown(),
					"três toques abrem o modo voz do ChatGPT (tela: " + name(f) + ")");
			if (chat) {
				Screens.save(root, size + "-nav-wheel-voice");
				d.onBackPressed();
				Screens.settle();
			}
		}

		void press(int action) {
			long t = android.os.SystemClock.uptimeMillis();
			me.aap.fermata.action.KeyEventHandler.handleKeyEvent(d,
					new android.view.KeyEvent(t, t, action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0),
					(c, e) -> false);
		}

		// Real time and the main looper together, for the press timings
		static final class ShadowWait {
			static void ms(long ms) throws InterruptedException {
				long end = System.currentTimeMillis() + ms;
				while (System.currentTimeMillis() < end) {
					org.robolectric.shadows.ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.MILLISECONDS);
					Thread.sleep(10);
				}
			}
		}

		// --- Folders: the button opens the add menu ---

		void folders() throws Exception {
			d.showFragment(R.id.folders_fragment);
			Screens.settle();
			View fb = d.getFloatingButton();
			check(fb.isShown(), "botão de adicionar pasta visível");
			fb.performClick();
			Screens.settle();
			OverlayMenu menu = d.getContextMenu();
			check(((View) menu).isShown(), "botão abre \"Adicionar pasta\"");
			Screens.save(root, size + "-nav-folders-add");
			d.onBackPressed();
			Screens.settle();
			check(!((View) menu).isShown(), "Voltar fecha o menu");
		}

		// --- Settings: a row opens its page, Back returns ---

		void settings() throws Exception {
			int before = d.getActiveFragment().getFragmentId();
			d.showFragment(R.id.settings_fragment);
			Screens.settle();
			RecyclerView list = (RecyclerView) root.findViewById(me.aap.utils.R.id.prefs_list_view);
			check(!d.getFloatingButton().isShown(), "sem o botão redondo nas configurações");
			View row = list.getChildAt(0);
			row.performClick();
			Screens.settle();
			Screens.save(root, size + "-nav-settings-interface");
			ActivityFragment sf = d.getActiveFragment();
			Object adapter = field(sf, "adapter");
			java.lang.reflect.Method set = adapter.getClass().getMethod("getPreferenceSet");
			check(((me.aap.utils.pref.PreferenceSet) set.invoke(adapter)).getParent() != null,
					"toque em Interface abre a página dela");
			d.onBackPressed();
			Screens.settle();
			check((d.getActiveFragment() == sf) &&
							(((me.aap.utils.pref.PreferenceSet) set.invoke(adapter)).getParent() == null),
					"Voltar volta à lista de configurações");
			d.onBackPressed();
			Screens.settle();
			check(d.getActiveFragment().getFragmentId() != R.id.settings_fragment,
					"Voltar sai das configurações (para " + name(d.getActiveFragment()) + ", antes " +
							name(before) + ")");
		}

		// --- Helpers ---

		interface Step {
			void run() throws Exception;
		}

		void step(Step s, String name) {
			report.add("== " + name);
			try {
				s.run();
			} catch (Throwable ex) {
				fails++;
				report.add("FALHA  " + name + ": erro " + ex);
				StackTraceElement[] st = ex.getStackTrace();
				for (int i = 0; i < Math.min(4, st.length); i++) report.add("         at " + st[i]);
			}
		}

		void check(boolean ok, String what) {
			if (!ok) fails++;
			report.add((ok ? "OK     " : "FALHA  ") + what);
		}

		void write() throws IOException {
			File dir = new File(System.getProperty("screenshots.dir", "build/screenshots"));
			dir.mkdirs();
			try (FileWriter w = new FileWriter(new File(dir, size + "-navigation.txt"))) {
				for (String l : report) w.write(l + "\n");
				w.write("\n" + fails + " falha(s)\n");
			}
		}

		String getString(int id) {
			return a.getString(id);
		}

		String name(ActivityFragment f) {
			return (f == null) ? "nada" : name(f.getFragmentId());
		}

		String name(int id) {
			try {
				return a.getResources().getResourceEntryName(id);
			} catch (Exception ex) {
				return String.valueOf(id);
			}
		}

		String names(List<View> views) {
			StringBuilder sb = new StringBuilder();
			for (View v : views) sb.append(sb.length() == 0 ? "" : ", ").append(name(v.getId()));
			return sb.toString();
		}

		static void collect(View v, Predicate<View> p, List<View> out) {
			if (p.test(v)) out.add(v);
			if (v instanceof ViewGroup g) {
				for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), p, out);
			}
		}

		static View first(View v, Predicate<View> p) {
			List<View> l = new ArrayList<>();
			collect(v, p, l);
			return l.isEmpty() ? null : l.get(0);
		}

		static <T> T get(me.aap.utils.async.FutureSupplier<T> f) throws Exception {
			Screens.waitFor(f::isDone, 10);
			return f.get();
		}

		static Object field(Object o, String name) {
			for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					return f.get(o);
				} catch (NoSuchFieldException ignore) {
				} catch (IllegalAccessException ex) {
					throw new IllegalStateException(ex);
				}
			}
			throw new IllegalStateException("No field " + name);
		}
	}
}
