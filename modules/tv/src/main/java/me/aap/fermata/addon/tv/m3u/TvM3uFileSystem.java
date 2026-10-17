package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.resource.Rid;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFileSystem extends M3uFileSystem {
	public static final String SCHEME_TVM3U = "tvm3u";
	private static final TvM3uFileSystem instance = new TvM3uFileSystem();

	public static TvM3uFileSystem getInstance() {
		return instance;
	}

	@Override
	public String getScheme() {
		return SCHEME_TVM3U;
	}

	@NonNull
	@Override
	public FutureSupplier<TvM3uFile> getResource(Rid rid) {
		return super.getResource(rid).cast();
	}

	protected TvM3uFile createM3uFile(Rid rid) {
		return new TvM3uFile(rid);
	}

	// Many IPTV panels refuse the M3U download (HTTP 403, or an empty list) and serve only the apps
	// that log in with the user and password: the list is then built from the panel's Xtream API
	@Override
	protected FutureSupplier<M3uFile> load(M3uFile file) {
		XtreamM3u x = XtreamM3u.of(file.getUrl());
		if (x == null) return super.load(file);
		return super.load(file).then(f -> hasEntries(f.getLocalFile()) ? completed(f) :
				fromApi(x, f, new IOException("The M3U list has no channels")), err -> fromApi(x, file, err));
	}

	private static FutureSupplier<M3uFile> fromApi(XtreamM3u x, M3uFile file, Throwable cause) {
		Log.i("M3U download of an Xtream panel failed (", cause.getMessage(),
				"), building the list from its API");
		String movies = App.get().getString(R.string.tv_movies);
		return App.get().execute(() -> {
			x.build(file.getLocalFile(), movies);
			file.getPrefs().applyLongPref(HttpFileDownloader.TIMESTAMP, System.currentTimeMillis());
			if ((file instanceof TvM3uFile tv) && ((tv.getEpgUrl() == null) || tv.getEpgUrl().isEmpty())) {
				tv.setEpgUrl(x.epgUrl());
			}
			return file;
		}).ifFail(err -> {
			Log.e(err, "Failed to build the list from the Xtream API");
			throw (cause instanceof Exception) ? (Exception) cause : new IOException(cause);
		});
	}

	// An error page, an empty body or a list without a single entry
	private static boolean hasEntries(File f) {
		if (!f.isFile() || (f.length() == 0)) return false;
		try (BufferedReader r = new BufferedReader(new FileReader(f))) {
			char[] buf = new char[65536];
			int n = r.read(buf);
			return (n > 0) && new String(buf, 0, n).contains("#EXTINF");
		} catch (IOException ex) {
			return false;
		}
	}
}
