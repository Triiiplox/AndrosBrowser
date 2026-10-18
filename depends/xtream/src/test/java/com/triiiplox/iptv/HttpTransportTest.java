package com.triiiplox.iptv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.triiiplox.iptv.net.HttpException;
import com.triiiplox.iptv.net.HttpTransport;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

/** HttpTransport against a local server: retries, no retries, gzip and the request headers. */
public class HttpTransportTest {
	private ServerSocket server;
	private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
	private final List<Long> sleeps = new ArrayList<>();

	@After
	public void close() throws IOException {
		if (server != null) server.close();
	}

	@Test
	public void retriesTooManyRequestsHonoringRetryAfter() throws Exception {
		int[] n = {0};
		String url = serve(req -> (n[0]++ == 0) ?
				response(429, "Retry-After: 2\r\n", "slow down".getBytes()) :
				response(200, "", "[1]".getBytes()));
		assertEquals("[1]", read(transport(3).open(url)));
		assertEquals(2, requests.size());
		assertEquals(List.of(2000L), sleeps);
	}

	@Test
	public void loginInTheUrlIsSentAsBasicAuth() throws Exception {
		String url = serve(req -> response(200, "", "#EXTM3U".getBytes()));
		String withLogin = HttpTransport.withLogin(url, "us@er", "p:ss+word");
		assertTrue("the login is encoded", withLogin.contains("us%40er:p%3Ass%2Bword@127.0.0.1"));
		assertEquals("#EXTM3U", read(transport(1).open(withLogin)));
		String req = requests.get(0);
		String expected = java.util.Base64.getEncoder()
				.encodeToString("us@er:p:ss+word".getBytes(StandardCharsets.UTF_8));
		assertTrue(req.contains("Authorization: Basic " + expected));
		assertTrue("no login in the request line", req.startsWith("GET /"));
	}

	@Test
	public void forbiddenIsNotRetried() throws Exception {
		String url = serve(req -> response(403, "", "<html>no</html>".getBytes()));
		try {
			transport(3).open(url);
			fail();
		} catch (HttpException ex) {
			assertEquals(403, ex.status);
			assertTrue("no URL in the message", !ex.getMessage().contains("127.0.0.1"));
		}
		assertEquals(1, requests.size());
	}

	@Test
	public void serverErrorsAreRetriedThenReported() throws Exception {
		String url = serve(req -> response(502, "", new byte[0]));
		try {
			transport(3).open(url);
			fail();
		} catch (HttpException ex) {
			assertEquals(502, ex.status);
		}
		assertEquals(3, requests.size());
		assertEquals("growing pauses", List.of(1000L, 4000L), sleeps);
	}

	@Test
	public void gzipBodyAndPlayerHeaders() throws Exception {
		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (OutputStream o = new GZIPOutputStream(gz)) {
			o.write("{\"ok\":\"ção\"}".getBytes(StandardCharsets.UTF_8));
		}
		String url = serve(req -> response(200, "Content-Encoding: gzip\r\n", gz.toByteArray()));
		assertEquals("{\"ok\":\"ção\"}", read(transport(1).open(url)));
		String req = requests.get(0);
		assertTrue(req, req.contains("User-Agent: " + HttpTransport.PLAYER_AGENT));
		assertTrue(req, req.contains("Accept: */*"));
		assertTrue(req, req.contains("Accept-Encoding: gzip"));
	}

	private HttpTransport transport(int attempts) {
		return new HttpTransport(HttpTransport.PLAYER_AGENT, 5000, 5000, attempts, sleeps::add);
	}

	private String serve(Function<String, byte[]> handler) throws IOException {
		server = new ServerSocket(0);
		Thread t = new Thread(() -> {
			while (!server.isClosed()) {
				try (Socket s = server.accept()) {
					BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(),
							StandardCharsets.ISO_8859_1));
					StringBuilder sb = new StringBuilder();
					for (String l = r.readLine(); (l != null) && !l.isEmpty(); l = r.readLine()) {
						sb.append(l).append('\n');
					}
					String req = sb.toString();
					requests.add(req);
					OutputStream out = s.getOutputStream();
					out.write(handler.apply(req));
					out.flush();
				} catch (IOException ex) {
					return;
				}
			}
		});
		t.setDaemon(true);
		t.start();
		return "http://127.0.0.1:" + server.getLocalPort() + "/player_api.php?username=u&password=p";
	}

	private static byte[] response(int code, String headers, byte[] body) {
		String h = "HTTP/1.1 " + code + " X\r\n" + headers + "Content-Length: " + body.length +
				"\r\nConnection: close\r\n\r\n";
		byte[] hb = h.getBytes(StandardCharsets.ISO_8859_1);
		byte[] all = new byte[hb.length + body.length];
		System.arraycopy(hb, 0, all, 0, hb.length);
		System.arraycopy(body, 0, all, hb.length, body.length);
		return all;
	}

	private static String read(InputStream in) throws IOException {
		try (in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] b = new byte[4096];
			for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
			return out.toString("UTF-8");
		}
	}
}
