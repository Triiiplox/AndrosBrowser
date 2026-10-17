package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.net.NetHandler;

/**
 * An IPTV panel that refuses the default agent, or any agent: an error page must not be saved as
 * the playlist, which then opened empty.
 */
public class HttpFileDownloaderTest extends Assertions {
	private static final String M3U = "#EXTM3U\n#EXTINF:-1,Channel\nhttp://example.com/1.ts\n";
	private static ExecutorService exec;
	private static NetHandler handler;

	@BeforeAll
	public static void setUpClass() throws Exception {
		TestUtils.enableTestMode();
		exec = new NetThreadPool(2);
		handler = NetHandler.create(o -> o.executor = exec);
	}

	@AfterAll
	public static void tearDownClass() {
		handler.close();
		exec.shutdown();
	}

	@Test
	public void testRetryAsPlayerOn403() throws Exception {
		List<String> agents = new CopyOnWriteArrayList<>();
		File dst = File.createTempFile("list", ".m3u");
		assertTrue(dst.delete());

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = serve(server, 2, agents, ua -> ua.startsWith("VLC/") ? ok(M3U) : status(403));
			download(server, dst).get(10, TimeUnit.SECONDS);
			t.join(5000);
		}

		assertEquals(2, agents.size());
		assertFalse(agents.get(0).startsWith("VLC/"));
		assertTrue(agents.get(1).startsWith("VLC/"));
		assertEquals(M3U, new String(Files.readAllBytes(dst.toPath()), US_ASCII));
		assertTrue(dst.delete());
	}

	@Test
	public void testErrorPageIsNotSaved() throws Exception {
		List<String> agents = new CopyOnWriteArrayList<>();
		File dst = File.createTempFile("list", ".m3u");
		assertTrue(dst.delete());

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = serve(server, 2, agents, ua -> status(403));
			ExecutionException ex = assertThrows(ExecutionException.class,
					() -> download(server, dst).get(10, TimeUnit.SECONDS));
			assertTrue(ex.getCause().getMessage().contains("HTTP 403"), ex.getCause().getMessage());
			t.join(5000);
		}

		assertEquals(2, agents.size());
		assertFalse(dst.exists());
	}

	@Test
	public void testNoRetryOn404() throws Exception {
		List<String> agents = new CopyOnWriteArrayList<>();
		File dst = File.createTempFile("list", ".m3u");
		assertTrue(dst.delete());

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = serve(server, 1, agents, ua -> status(404));
			ExecutionException ex = assertThrows(ExecutionException.class,
					() -> download(server, dst).get(10, TimeUnit.SECONDS));
			assertTrue(ex.getCause().getMessage().contains("HTTP 404"), ex.getCause().getMessage());
			t.join(5000);
		}

		assertEquals(1, agents.size());
		assertFalse(dst.exists());
	}

	private static me.aap.utils.async.FutureSupplier<HttpFileDownloader.Status> download(
			ServerSocket server, File dst) {
		HttpFileDownloader d = new HttpFileDownloader();
		d.setNetHandler(handler);
		return d.download("http://localhost:" + server.getLocalPort() + "/get.php?type=m3u", dst);
	}

	private static String ok(String body) {
		return "HTTP/1.1 200 OK\r\nContent-Type: audio/x-mpegurl\r\nContent-Length: " + body.length() +
				"\r\nConnection: close\r\n\r\n" + body;
	}

	private static String status(int code) {
		String body = "<html>" + code + "</html>";
		return "HTTP/1.1 " + code + " Error\r\nContent-Type: text/html\r\nContent-Length: " +
				body.length() + "\r\nConnection: close\r\n\r\n" + body;
	}

	// Answers n requests, one per connection, by the User-Agent of each
	private static Thread serve(ServerSocket server, int n, List<String> agents,
															Function<String, String> response) {
		Thread t = new Thread(() -> {
			for (int i = 0; i < n; i++) {
				try (Socket s = server.accept()) {
					InputStream in = s.getInputStream();
					byte[] buf = new byte[4096];
					StringBuilder req = new StringBuilder();
					while (req.indexOf("\r\n\r\n") < 0) {
						int len = in.read(buf);
						if (len < 0) break;
						req.append(new String(buf, 0, len, US_ASCII));
					}
					String ua = "";
					for (String l : req.toString().split("\r\n")) {
						if (l.regionMatches(true, 0, "User-Agent:", 0, 11)) ua = l.substring(11).trim();
					}
					agents.add(ua);
					OutputStream out = s.getOutputStream();
					out.write(response.apply(ua).getBytes(US_ASCII));
					out.flush();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		t.start();
		return t;
	}
}
