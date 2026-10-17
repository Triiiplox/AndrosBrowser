package com.triiiplox.iptv.net;

import java.io.IOException;
import java.io.InputStream;

/** Opens a URL for reading. The body is returned decoded (not gzipped). */
public interface Transport {
	InputStream open(String url) throws IOException;
}
