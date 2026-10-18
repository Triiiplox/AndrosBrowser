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
				step(this::folders, "Pastas");
				step(this::settings, "Configurações");
			} finally {
				write();
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
			View screen = d.getActiveFragment().requireView();
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
