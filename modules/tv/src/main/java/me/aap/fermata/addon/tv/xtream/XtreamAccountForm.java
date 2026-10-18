package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.M3uPlaylist;
import com.triiiplox.iptv.XtreamAccount;
import com.triiiplox.iptv.XtreamClient;
import com.triiiplox.iptv.model.AccountInfo;
import com.triiiplox.iptv.net.HttpTransport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;

/**
 * Adds an account: a get.php link alone or a server with the user and password (Xtream), or the
 * link of a plain M3U playlist. The login, or the playlist, is checked before the account is
 * saved, so a typo shows at once.
 */
final class XtreamAccountForm {
	private XtreamAccountForm() {
	}

	static void show(MainActivityDelegate a, Consumer<XtreamRepo.Account> added) {
		show(a, null, null, null, null, added);
	}

	private static void show(MainActivityDelegate a, @Nullable String link, @Nullable String user,
													 @Nullable String pass, @Nullable String name,
													 Consumer<XtreamRepo.Account> added) {
		Context ctx = a.getContext();
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout form = new LinearLayout(ctx);
		form.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (20 * dp);
		form.setPadding(pad, (int) (8 * dp), pad, 0);
		EditText linkText = field(ctx, form, R.string.xtream_link, link, InputType.TYPE_TEXT_VARIATION_URI);
		EditText userText = field(ctx, form, R.string.xtream_user, user, InputType.TYPE_CLASS_TEXT);
		EditText passText = field(ctx, form, R.string.xtream_password, pass,
				InputType.TYPE_TEXT_VARIATION_PASSWORD);
		EditText nameText = field(ctx, form, R.string.xtream_name, name, InputType.TYPE_CLASS_TEXT);

		a.createDialogBuilder().setTitle(R.string.xtream_add_account).setView(form)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					String l = linkText.getText().toString().trim();
					String u = userText.getText().toString().trim();
					String p = passText.getText().toString();
					String n = nameText.getText().toString().trim();
					XtreamAccount xa = XtreamAccount.fromM3uUrl(l);
					Runnable retry = () -> show(a, l, u, p, n, added);
					if (xa != null) {
						check(a, xa, n, null, retry, added);
					} else if (!l.isEmpty() && !u.isEmpty() && !p.isEmpty()) {
						// A server with a login: an Xtream panel, else a playlist behind a login
						check(a, new XtreamAccount(l, u, p, "ts"), n, playlistLogin(l, u, p), retry, added);
					} else if (u.isEmpty() && p.isEmpty() && XtreamRepo.isWebLink(l)) {
						checkPlaylist(a, l, n, retry, added);
					} else {
						UiUtils.showAlert(ctx, R.string.xtream_invalid_account)
								.onCompletion((v, e) -> retry.run());
					}
				}).show();
	}

	@Nullable
	private static String playlistLogin(String link, String user, String pass) {
		if (!XtreamRepo.isWebLink(link)) return null;
		try {
			return HttpTransport.withLogin(link, user, pass);
		} catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Checks the login with the panel. When the server is no Xtream panel, the link with the login
	 * may still be a playlist protected by a password: that is tried before giving up.
	 */
	private static void check(MainActivityDelegate a, XtreamAccount xa, String name,
														@Nullable String playlistLogin, Runnable retry,
														Consumer<XtreamRepo.Account> added) {
		Context ctx = a.getContext();
		XtreamRepo repo = XtreamRepo.get(ctx);
		repo.run(() -> new XtreamClient(xa, new HttpTransport()).accountInfo()).main()
				.onCompletion((info, err) -> {
					if ((err != null) && (playlistLogin != null)) {
						checkPlaylist(a, playlistLogin, name, retry, added);
						return;
					}
					if ((err != null) || (info == null) || !info.isActive()) {
						String why = (err != null) ? String.valueOf(err.getMessage()) :
								(info != null) ? info.status : "?";
						UiUtils.showAlert(ctx, ctx.getString(R.string.xtream_login_refused, why))
								.onCompletion((v, e) -> retry.run());
						return;
					}
					added.accept(repo.addAccount(xa, name.isEmpty() ? xa.getHost() : name));
				});
	}

	// Only the start of the download: enough to tell a playlist from a login page or an error
	private static void checkPlaylist(MainActivityDelegate a, String url, String name,
																		Runnable retry, Consumer<XtreamRepo.Account> added) {
		Context ctx = a.getContext();
		XtreamRepo repo = XtreamRepo.get(ctx);
		repo.run(() -> {
			try (InputStream in = new HttpTransport().open(url)) {
				byte[] buf = new byte[4096];
				int n = 0;
				for (int r; (n < buf.length) && ((r = in.read(buf, n, buf.length - n)) > 0); n += r) {
				}
				return M3uPlaylist.looksLikePlaylist(new String(buf, 0, n, StandardCharsets.UTF_8));
			}
		}).main().onCompletion((ok, err) -> {
			if ((err != null) || !Boolean.TRUE.equals(ok)) {
				String why = (err != null) ? String.valueOf(err.getMessage()) : "#EXTM3U?";
				UiUtils.showAlert(ctx, ctx.getString(R.string.xtream_not_playlist, why))
						.onCompletion((v, e) -> retry.run());
				return;
			}
			added.accept(repo.addPlaylist(url, name));
		});
	}

	private static EditText field(Context ctx, LinearLayout form, int hint, @Nullable String value,
																int variation) {
		EditText t = new EditText(ctx);
		t.setHint(hint);
		t.setSingleLine(true);
		t.setInputType(InputType.TYPE_CLASS_TEXT | variation);
		if (value != null) t.setText(value);
		form.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		return t;
	}
}
