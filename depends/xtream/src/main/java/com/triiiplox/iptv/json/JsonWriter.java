package com.triiiplox.iptv.json;

import java.io.IOException;
import java.io.Writer;

/** A minimal JSON writer: objects, arrays, strings, numbers, booleans. No pretty printing. */
public final class JsonWriter implements AutoCloseable {
	private final Writer out;
	private boolean comma;

	public JsonWriter(Writer out) {
		this.out = out;
	}

	public JsonWriter beginObject() throws IOException {
		sep();
		out.write('{');
		comma = false;
		return this;
	}

	public JsonWriter endObject() throws IOException {
		out.write('}');
		comma = true;
		return this;
	}

	public JsonWriter beginArray() throws IOException {
		sep();
		out.write('[');
		comma = false;
		return this;
	}

	public JsonWriter endArray() throws IOException {
		out.write(']');
		comma = true;
		return this;
	}

	public JsonWriter name(String name) throws IOException {
		sep();
		string(name);
		out.write(':');
		comma = false;
		return this;
	}

	public JsonWriter value(String v) throws IOException {
		sep();
		if (v == null) out.write("null");
		else string(v);
		comma = true;
		return this;
	}

	public JsonWriter value(long v) throws IOException {
		sep();
		out.write(Long.toString(v));
		comma = true;
		return this;
	}

	public JsonWriter value(boolean v) throws IOException {
		sep();
		out.write(v ? "true" : "false");
		comma = true;
		return this;
	}

	/** name + value, skipped when the value is null. */
	public JsonWriter field(String name, String v) throws IOException {
		if (v != null) name(name).value(v);
		return this;
	}

	public JsonWriter field(String name, long v) throws IOException {
		return name(name).value(v);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	private void sep() throws IOException {
		if (comma) out.write(',');
	}

	private void string(String s) throws IOException {
		out.write('"');
		for (int i = 0, n = s.length(); i < n; i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.write("\\\"");
				case '\\' -> out.write("\\\\");
				case '\n' -> out.write("\\n");
				case '\r' -> out.write("\\r");
				case '\t' -> out.write("\\t");
				default -> {
					if (c < 0x20) out.write(String.format("\\u%04x", (int) c));
					else out.write(c);
				}
			}
		}
		out.write('"');
	}
}
