package me.aap.fermata.addon.chat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.addon.AddonManager;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.net.http.HttpHeader;
import me.aap.utils.net.http.HttpMethod;
import me.aap.utils.net.http.HttpStatusCode;

/**
 * @author Andrey Pavlenko
 */
class ChatGpt implements EventBroadcaster<ChatGpt.Listener> {
	private static final ChatGpt instance = new ChatGpt();
	private final Collection<ListenerRef<Listener>> listeners = new LinkedList<>();
	private final ChatAddon addon = requireNonNull(AddonManager.get().getAddon(ChatAddon.class));

	// A conversation where ChatGPT asks for a tool again and again ends there
	private static final int MAX_TOOL_ROUNDS = 4;
	// The tests point it at a local server
	static String apiUrl = "https://api.openai.com/v1/chat/completions";
	private final List<ChatGpt.Message> messages = new ArrayList<>();

	public static ChatGpt getInstance() {
		return instance;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}

	void addMessage(String text, ChatGpt.Role role) {
		Message msg = new Message(role, text);
		messages.add(msg);
		int idx = messages.size() - 1;
		fireBroadcastEvent(l -> l.messageAdded(msg, idx));
	}

	/** Starts a new conversation. */
	public void clear() {
		messages.clear();
	}

	public List<Message> getMessages() {
		return messages;
	}

	List<Message> getLastMessages(int count) {
		var s = messages.size();
		if (s == 0) return Collections.emptyList();
		return messages.subList(s - Math.min(count, s), s);
	}

	public FutureSupplier<String> sendRequest(List<Message> messages) {
		return sendRequest(messages, null);
	}

	/**
	 * The answer to the conversation. With tools, ChatGPT may call them first (function calling):
	 * each call is run here, its result sent back, and the model asked again, a few rounds at most.
	 */
	public FutureSupplier<String> sendRequest(List<Message> messages, @Nullable ChatTools tools) {
		var auth = addon.getAuthorization();
		if (auth == null) return failed(new IllegalStateException("OpenAI key is not specified"));
		JSONArray history = new JSONArray();
		JSONArray defs;
		try {
			if (tools != null) {
				history.put(new JSONObject().put("role", "system").put("content", ChatTools.systemPrompt()));
				defs = tools.definitions();
			} else {
				defs = null;
			}
			for (Message msg : messages) {
				history.put(new JSONObject().put("role", msg.role.toString()).put("content", msg.content));
			}
		} catch (JSONException ex) {
			return failed(ex);
		}
		Promise<String> p = new Promise<>();
		ask(auth, history, defs, tools, 0, p);
		return p.main();
	}

	private void ask(String auth, JSONArray history, @Nullable JSONArray defs,
									 @Nullable ChatTools tools, int round, Promise<String> p) {
		JSONObject req;
		try {
			req = new JSONObject().put("model", addon.getModel()).put("messages", history);
			// No temperature: the reasoning models (o*, gpt-5 and later) refuse any but their own
			if ((defs != null) && (round < MAX_TOOL_ROUNDS)) req.put("tools", defs);
		} catch (JSONException ex) {
			p.completeExceptionally(ex);
			return;
		}
		post(auth, req).main().onCompletion((resp, err) -> {
			if (err != null) {
				p.completeExceptionally(err);
				return;
			}
			try {
				JSONObject msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
				JSONArray calls = msg.optJSONArray("tool_calls");
				if ((tools == null) || (calls == null) || (calls.length() == 0)) {
					p.complete(msg.optString("content", "").trim());
					return;
				}
				history.put(msg);
				runCalls(tools, calls, 0, history).onCompletion((v, cerr) -> {
					if (cerr != null) p.completeExceptionally(cerr);
					else ask(auth, history, defs, tools, round + 1, p);
				});
			} catch (JSONException ex) {
				p.completeExceptionally(ex);
			}
		});
	}

	// One call after the other: they act on the same screen and player
	private FutureSupplier<Void> runCalls(ChatTools tools, JSONArray calls, int i, JSONArray history)
			throws JSONException {
		if (i == calls.length()) return completedVoid();
		JSONObject call = calls.getJSONObject(i);
		JSONObject fn = call.getJSONObject("function");
		JSONObject args;
		try {
			String a = fn.optString("arguments", "");
			args = a.isEmpty() ? new JSONObject() : new JSONObject(a);
		} catch (JSONException ex) {
			args = null;
		}
		return tools.call(fn.getString("name"), args).then(result -> {
			history.put(new JSONObject().put("role", "tool").put("tool_call_id", call.getString("id"))
					.put("content", result));
			return runCalls(tools, calls, i + 1, history);
		});
	}

	private FutureSupplier<JSONObject> post(String auth, JSONObject json) {
		var p = new Promise<JSONObject>();
		HttpConnection.connect(o -> {
			o.url(apiUrl);
			o.method = HttpMethod.POST;
			o.builder = b -> {
				b.addHeader(HttpHeader.ACCEPT);
				b.addHeader(HttpHeader.CONTENT_TYPE, "application/json");
				b.addHeader(HttpHeader.AUTHORIZATION, auth);
				Log.d("Request: ", json.toString());
				return b.build(ByteBuffer.wrap(json.toString().getBytes(UTF_8)));
			};
		}, (resp, err) -> {
			if (err != null) {
				p.completeExceptionally(err);
				return failed(err);
			} else if (resp.getStatusCode() != HttpStatusCode.OK) {
				// OpenAI says why in the body: a wrong key, no credit, an unknown model
				String reason = String.valueOf(resp.getReason());
				return resp.getPayload((bb, perr) -> {
					String msg = reason;
					if (perr == null) {
						try {
							bb = IoUtils.getFrom(bb);
							String body = new String(bb.array(), bb.arrayOffset(), bb.remaining(), UTF_8);
							Log.d("Response: ", body);
							msg = new JSONObject(body).getJSONObject("error").optString("message", reason);
						} catch (Exception ignore) {
						}
					}
					p.completeExceptionally(new IOException(msg));
					return completedVoid();
				});
			}

			return resp.getPayload((bb, perr) -> {
				try {
					if (perr != null) {
						p.completeExceptionally(perr);
						return failed(perr);
					}
					bb = IoUtils.getFrom(bb);
					String jsonString = new String(bb.array(), bb.arrayOffset(), bb.remaining(), UTF_8);
					Log.d("Response: ", jsonString);
					p.complete(new JSONObject(jsonString));
				} catch (JSONException ex) {
					p.completeExceptionally(ex);
					return failed(ex);
				}
				return completedVoid();
			});
		});
		return p;
	}

	enum Role {
		USER, ASSISTANT;
		private final String name;

		Role() {
			this.name = name().toLowerCase();
		}

		@NonNull
		@Override
		public String toString() {
			return name;
		}
	}

	static final class Message {
		final Role role;
		final String content;

		Message(Role role, String content) {
			this.role = role;
			this.content = content;
		}
	}

	interface Listener {
		void messageAdded(Message msg, int index);
	}
}
