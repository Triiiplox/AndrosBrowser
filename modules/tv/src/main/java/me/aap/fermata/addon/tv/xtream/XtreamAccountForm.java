package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.triiiplox.iptv.XtreamAccount;
import com.triiiplox.iptv.XtreamClient;
import com.triiiplox.iptv.model.AccountInfo;
import com.triiiplox.iptv.net.HttpTransport;

import java.util.function.Consumer;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;

/**
 * Adds an Xtream account: a get.php link alone, or a server with the user and password. The login
 * is checked with the panel before the account is saved, so a typo shows at once.
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
					if ((xa == null) && !l.isEmpty() && !u.isEmpty() && !p.isEmpty()) {
						xa = new XtreamAccount(l, u, p, "ts");
					}
					if (xa == null) {
						UiUtils.showAlert(ctx, R.string.xtream_invalid_account)
								.onCompletion((v, e) -> show(a, l, u, p, n, added));
						return;
					}
					check(a, xa, n, () -> show(a, l, u, p, n, added), added);
				}).show();
	}

	private static void check(MainActivityDelegate a, XtreamAccount xa, String name,
														Runnable retry, Consumer<XtreamRepo.Account> added) {
		Context ctx = a.getContext();
		XtreamRepo repo = XtreamRepo.get(ctx);
		repo.run(() -> new XtreamClient(xa, new HttpTransport()).accountInfo()).main()
				.onCompletion((info, err) -> {
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
