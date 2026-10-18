package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.triiiplox.iptv.model.Entry.Kind;
import com.triiiplox.iptv.net.HttpTransport;

import java.util.Objects;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * A live channel, a movie or an episode of an Xtream account. Played from its stream URL by the
 * app's engines; the metadata comes from the catalog, nothing is probed over the network.
 */
public final class XtreamPlayable extends PlayableItemBase implements XtreamItem {
	private final String accountId;
	private final Kind kind;
	private final String streamId;
	private final String title;
	private final String subtitle;
	@Nullable
	private final String image;
	private final long durationMs;
	@Nullable
	private final String seriesId;
	private final int season;
	private final int episode;
	@Nullable
	private final String extension;
	// The programme on air and the next one, for a live channel
	@Nullable
	private volatile String nowNext;
	// A past program of the channel, from the panel's recording: it has an end, and can be seeked
	private boolean catchup;

	XtreamPlayable(String id, BrowsableItem parent, String url, String accountId, Kind kind,
								 String streamId, String title, String subtitle, @Nullable String image,
								 long durationMs, @Nullable String seriesId, int season, int episode,
								 @Nullable String extension) {
		super(id, parent, GenericFileSystem.getInstance().create(Rid.create(url)));
		this.accountId = accountId;
		this.kind = kind;
		this.streamId = streamId;
		this.title = title;
		this.subtitle = subtitle;
		this.image = image;
		this.durationMs = durationMs;
		this.seriesId = seriesId;
		this.season = season;
		this.episode = episode;
		this.extension = extension;
	}

	public String getAccountId() {
		return accountId;
	}

	public Kind getKind() {
		return kind;
	}

	public boolean isCatchup() {
		return catchup;
	}

	XtreamPlayable setCatchup() {
		catchup = true;
		return this;
	}

	public String getStreamId() {
		return streamId;
	}

	@NonNull
	@Override
	public String getName() {
		return title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	@Nullable
	public String getImage() {
		return image;
	}

	@Nullable
	public String getSeriesId() {
		return seriesId;
	}

	public int getSeason() {
		return season;
	}

	public int getEpisode() {
		return episode;
	}

	@Nullable
	public String getExtension() {
		return extension;
	}

	/**
	 * Shows the programme on air and the next one under the channel name, in the player and on the
	 * car screen, in place of the category.
	 */
	void setNowNext(@Nullable String text) {
		if (Objects.equals(nowNext, text)) return;
		nowNext = text;
		setMeta(buildMeta(new MetadataBuilder()));
		updateTitles();
	}

	@Nullable
	private String shownSubtitle() {
		String nn = nowNext;
		return (nn != null) ? nn : subtitle;
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@Override
	public boolean isStream() {
		return (kind == Kind.LIVE) && !catchup;
	}

	@Override
	public boolean isSeekable() {
		return (kind != Kind.LIVE) || catchup;
	}

	@Nullable
	@Override
	public String getUserAgent() {
		return HttpTransport.PLAYER_AGENT;
	}

	/**
	 * VLC unless chosen otherwise for this item: the panels refuse the streams to a client that does
	 * not say it is a player, and of the app's engines only VLC sends the user agent (the Android
	 * player and ExoPlayer send their own). VLC also copes best with live MPEG-TS.
	 */
	@Override
	public int getVideoEnginePref() {
		return hasPref(VIDEO_ENGINE, false) ? super.getVideoEnginePref() : MEDIA_ENG_VLC;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
		String sub = shownSubtitle();
		if (sub != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, sub);
		if (durationMs > 0) meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
		if (image != null) meta.setImageUri(image);
		return completed(meta.build());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		String sub = shownSubtitle();
		return completed((sub == null) ? "" : sub);
	}
}
