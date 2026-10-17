package com.triiiplox.iptv.net;

import java.io.IOException;

/** An HTTP error status. The message never carries the URL: it has the password in it. */
public final class HttpException extends IOException {
	public final int status;

	public HttpException(int status, String reason) {
		super("HTTP " + status + ((reason == null) ? "" : " " + reason));
		this.status = status;
	}

	/** Worth trying again later: too many requests or a server failure. */
	public boolean isTransient() {
		return (status == 429) || (status >= 500);
	}
}
