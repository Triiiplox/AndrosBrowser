package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * A folder of the Xtream tree, as the car and the media session browse it: an account, its live
 * channels, movies or series, a category, or the episodes of a series. The children are listed by
 * the root from the cached catalog.
 */
public final class XtreamFolder extends BrowsableItemBase implements XtreamItem {
	enum Type {ACCOUNT, SECTION, CATEGORY, SERIES}

	final Type type;
	final String accountId;
	final String[] args;
	private final String name;
	@Nullable
	private final String image;
	private final XtreamRootItem root;

	XtreamFolder(String id, XtreamRootItem root, BrowsableItem parent, Type type, String accountId,
							 String name, @Nullable String image, String... args) {
		super(id, parent, null);
		this.root = root;
		this.type = type;
		this.accountId = accountId;
		this.name = name;
		this.image = image;
		this.args = args;
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return root.listChildren(this);
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, me.aap.fermata.media.pref.BrowsableItemPrefs parentPrefs) {
		return completed(name);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (image != null) ? completed(Uri.parse(image)) : completedNull();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}
}
