package com.triiiplox.iptv.net;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * HTTP GET with the habits of a media player, which IPTV panels expect: a player User-Agent, an
 * Accept header and gzip. Transient failures (429, 5xx, timeouts, dropped connections) are
 * retried with a growing pause, honoring a short Retry-After.
 */
public final class HttpTransport implements Transport {
	public static final String PLAYER_AGENT = "VLC/3.0.20 LibVLC/3.0.20";
	private final String agent;
	private final int connectTimeout;
	private final int readTimeout;
	private final int attempts;
	private final Sleeper sleeper;

	public HttpTransport() {
		this(PLAYER_AGENT, 20000, 60000, 3, Thread::sleep);
	}

	public HttpTransport(String agent, int connectTimeout, int readTimeout, int attempts,
											 Sleeper sleeper) {
		this.agent = agent;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.attempts = Math.max(1, attempts);
		this.sleeper = sleeper;
	}

	@Override
	public InputStream open(String url) throws IOException {
		retryAfter.remove();
		IOException last = null;
		for (int i = 0; i < attempts; i++) {
			if (i > 0) pause(i, last);
			try {
				return connect(url);
			} catch (HttpException ex) {
				if (!ex.isTransient()) throw ex;
				last = ex;
			} catch (SocketTimeoutException ex) {
				last = ex;
			} catch (IOException ex) {
				// A dropped connection; an unknown host or a refused connection will not get better
				if (ex instanceof java.net.UnknownHostException) throw ex;
				if (ex instanceof java.net.ConnectException) throw ex;
				last = ex;
			}
		}
		throw last;
	}

	private InputStream connect(String url) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(connectTimeout);
		c.setReadTimeout(readTimeout);
		c.setInstanceFollowRedirects(true);
		c.setRequestProperty("User-Agent", agent);
		c.setRequestProperty("Accept", "*/*");
		c.setRequestProperty("Accept-Encoding", "gzip");
		int code = c.getResponseCode();

		if (code != HttpURLConnection.HTTP_OK) {
			String retry = c.getHeaderField("Retry-After");
			c.disconnect();
			HttpException ex = new HttpException(code, c.getResponseMessage());
			if (retry != null) retryAfter.set(parseRetryAfter(retry));
			throw ex;
		}

		InputStream in = c.getInputStream();
		if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in, 65536);
		return new FilterInputStream(in) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					c.disconnect();
				}
			}
		};
	}

	private final ThreadLocal<Long> retryAfter = new ThreadLocal<>();

	private void pause(int attempt, IOException cause) throws IOException {
		Long ra = retryAfter.get();
		retryAfter.remove();
		long ms = (ra != null) ? Math.min(ra, 10000) : 1000L * attempt * attempt;
		try {
			sleeper.sleep(ms);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw cause;
		}
	}

	private static long parseRetryAfter(String v) {
		try {
			return Long.parseLong(v.trim()) * 1000;
		} catch (NumberFormatException ex) {
			return 2000;
		}
	}

	public interface Sleeper {
		void sleep(long ms) throws InterruptedException;
	}
}
