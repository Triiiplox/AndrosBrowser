package com.triiiplox.iptv.net;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps the responses on disk, gzipped, named by a hash of the URL (no credential in the file
 * names). A fresh copy is served without a request; a stale one is refreshed, and still served
 * when the panel cannot be reached, so the catalog opens offline and survives a bad network.
 * A response is written to a temporary file and renamed: a cut download never replaces a good
 * copy.
 */
public final class CachingTransport implements Transport {
	private final Transport net;
	private final File dir;
	private final TtlPolicy ttl;
	private final Clock clock;
	private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

	public CachingTransport(Transport net, File dir, TtlPolicy ttl) {
		this(net, dir, ttl, System::currentTimeMillis);
	}

	public CachingTransport(Transport net, File dir, TtlPolicy ttl, Clock clock) {
		this.net = net;
		this.dir = dir;
		this.ttl = ttl;
		this.clock = clock;
	}

	@Override
	public InputStream open(String url) throws IOException {
		return open(url, false);
	}

	/** @param refresh ignore a fresh copy and ask the panel, e.g. on a pull to refresh */
	public InputStream open(String url, boolean refresh) throws IOException {
		long maxAge = ttl.maxAge(url);
		if (maxAge <= 0) return net.open(url);
		String key = hash(url);
		File f = new File(dir, key + ".json.gz");

		synchronized (locks.computeIfAbsent(key, k -> new Object())) {
			if (!refresh && f.isFile() && (clock.now() - f.lastModified() < maxAge)) return read(f);
			try {
				download(url, f);
			} catch (IOException ex) {
				if (f.isFile()) return read(f);
				throw ex;
			}
			return read(f);
		}
	}

	/** The age of the cached copy in milliseconds, or -1 if none. */
	public long age(String url) {
		File f = new File(dir, hash(url) + ".json.gz");
		return f.isFile() ? Math.max(0, clock.now() - f.lastModified()) : -1;
	}

	public void clear() {
		File[] files = dir.listFiles();
		if (files == null) return;
		//noinspection ResultOfMethodCallIgnored
		for (File f : files) f.delete();
	}

	private void download(String url, File f) throws IOException {
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
		File tmp = new File(dir, f.getName() + ".part");
		try (InputStream in = net.open(url);
				 OutputStream out = new GZIPOutputStream(new FileOutputStream(tmp), 65536)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
		} catch (IOException ex) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw ex;
		}
		if (!tmp.renameTo(f)) {
			//noinspection ResultOfMethodCallIgnored
			f.delete();
			if (!tmp.renameTo(f)) {
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
				throw new IOException("Cannot write " + f);
			}
		}
		//noinspection ResultOfMethodCallIgnored
		f.setLastModified(clock.now());
	}

	private static InputStream read(File f) throws IOException {
		return new GZIPInputStream(new BufferedInputStream(new FileInputStream(f), 65536), 65536);
	}

	private static String hash(String s) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(32);
			for (int i = 0; i < 16; i++) {
				sb.append(Character.forDigit((h[i] >> 4) & 0xF, 16)).append(Character.forDigit(h[i] & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public interface TtlPolicy {
		/** Milliseconds a response stays fresh; 0 to never cache it. */
		long maxAge(String url);
	}

	public interface Clock {
		long now();
	}
}
