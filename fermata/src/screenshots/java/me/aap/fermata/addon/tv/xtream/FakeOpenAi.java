package me.aap.fermata.addon.tv.xtream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The chat completions API of OpenAI on a local port: it answers the queued replies in order and
 * keeps the bodies of the requests, to see what the app sent.
 */
final class FakeOpenAi implements AutoCloseable {
	final String url;
	final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
	private final Deque<String> replies = new ArrayDeque<>();
	private final ServerSocket server;

	FakeOpenAi() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
		url = "http://127.0.0.1:" + server.getLocalPort() + "/v1/chat/completions";
		Thread t = new Thread(() -> {
			while (!server.isClosed()) {
				try {
					Socket c = server.accept();
					Thread h = new Thread(() -> handle(c), "Fake OpenAI request");
					h.setDaemon(true);
					h.start();
				} catch (IOException ignore) {
				}
			}
		}, "Fake OpenAI");
		t.setDaemon(true);
		t.start();
	}

	/** The model calls a function. */
	synchronized void replyCall(String id, String name, String args) {
		replies.add("{\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\",\"message\":{" +
				"\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"" + id +
				"\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":" +
				quote(args) + "}}]}}]}");
	}

	/** The model answers. */
	synchronized void replyText(String text) {
		replies.add("{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{" +
				"\"role\":\"assistant\",\"content\":" + quote(text) + "}}]}");
	}

	@Override
	public void close() throws IOException {
		server.close();
	}

	private void handle(Socket c) {
		try (c; OutputStream out = c.getOutputStream()) {
			InputStream in = c.getInputStream();
			String head = readHead(in);
			int len = 0;
			for (String h : head.split("\r\n")) {
				if (h.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
					len = Integer.parseInt(h.substring(15).trim());
				}
			}
			byte[] body = in.readNBytes(len);
			bodies.add(new String(body, StandardCharsets.UTF_8));
			String reply;
			synchronized (this) {
				reply = replies.poll();
			}
			if (reply == null) reply = "{\"error\":{\"message\":\"no reply queued\"}}";
			byte[] b = reply.getBytes(StandardCharsets.UTF_8);
			out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " +
					b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
			out.write(b);
		} catch (IOException ignore) {
		}
	}

	private static String readHead(InputStream in) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int n = 0;
		for (int c = in.read(); c >= 0; c = in.read()) {
			b.write(c);
			n = ((c == '\r') || (c == '\n')) ? n + 1 : 0;
			if (n == 4) break;
		}
		return b.toString(StandardCharsets.ISO_8859_1);
	}

	private static String quote(String s) {
		return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}
