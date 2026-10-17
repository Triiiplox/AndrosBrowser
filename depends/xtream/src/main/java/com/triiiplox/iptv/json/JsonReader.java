package com.triiiplox.iptv.json;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * A small streaming (pull) JSON reader: values are read one at a time, so a catalog of tens of
 * thousands of entries is never held in memory as a tree. Plain Java, no platform dependency.
 * <p>
 * IPTV panels are loose with types: an id may come as a number or a string, a list as an array
 * or an object, an absent value as null, "" or false. {@link #nextStringOrNull()} reads any
 * scalar as text and nested values as null, so callers stay simple.
 */
public final class JsonReader implements Closeable {
	private static final int MAX_DEPTH = 256;
	private final Reader in;
	private final char[] buf = new char[8192];
	private int pos;
	private int limit;
	// Per depth: what is open and where we are in it
	private final byte[] stack = new byte[MAX_DEPTH];
	private int depth;
	private JsonToken peeked;
	private final StringBuilder sb = new StringBuilder();

	private static final byte EMPTY_DOC = 0;
	private static final byte NONEMPTY_DOC = 1;
	private static final byte EMPTY_ARRAY = 2;
	private static final byte NONEMPTY_ARRAY = 3;
	private static final byte EMPTY_OBJECT = 4;
	private static final byte DANGLING_NAME = 5;
	private static final byte NONEMPTY_OBJECT = 6;

	public JsonReader(Reader in) {
		this.in = in;
		stack[depth++] = EMPTY_DOC;
	}

	public JsonToken peek() throws IOException {
		if (peeked != null) return peeked;
		return peeked = doPeek();
	}

	public void beginArray() throws IOException {
		expect(JsonToken.BEGIN_ARRAY);
		push(EMPTY_ARRAY);
	}

	public void endArray() throws IOException {
		expect(JsonToken.END_ARRAY);
		depth--;
	}

	public void beginObject() throws IOException {
		expect(JsonToken.BEGIN_OBJECT);
		push(EMPTY_OBJECT);
	}

	public void endObject() throws IOException {
		expect(JsonToken.END_OBJECT);
		depth--;
	}

	public boolean hasNext() throws IOException {
		JsonToken t = peek();
		return (t != JsonToken.END_OBJECT) && (t != JsonToken.END_ARRAY) &&
				(t != JsonToken.END_DOCUMENT);
	}

	public String nextName() throws IOException {
		expect(JsonToken.NAME);
		String name = readString();
		stack[depth - 1] = DANGLING_NAME;
		return name;
	}

	public String nextString() throws IOException {
		JsonToken t = peek();
		if (t == JsonToken.STRING) {
			peeked = null;
			return readString();
		}
		if (t == JsonToken.NUMBER) {
			peeked = null;
			return readLiteral();
		}
		throw syntax("Expected a string but was " + t);
	}

	public boolean nextBoolean() throws IOException {
		JsonToken t = peek();
		if (t != JsonToken.BOOLEAN) throw syntax("Expected a boolean but was " + t);
		peeked = null;
		return readLiteral().equals("true");
	}

	public void nextNull() throws IOException {
		JsonToken t = peek();
		if (t != JsonToken.NULL) throw syntax("Expected null but was " + t);
		peeked = null;
		readLiteral();
	}

	/**
	 * Any scalar as text (numbers as written, booleans as "true"/"false"), null for null and for
	 * arrays and objects, which are skipped. Empty strings are returned as null too.
	 */
	public String nextStringOrNull() throws IOException {
		switch (peek()) {
			case STRING:
			case NUMBER: {
				String s = nextString();
				return s.isEmpty() ? null : s;
			}
			case BOOLEAN:
				return String.valueOf(nextBoolean());
			case NULL:
				nextNull();
				return null;
			default:
				skipValue();
				return null;
		}
	}

	public void skipValue() throws IOException {
		int open = 0;
		do {
			switch (peek()) {
				case BEGIN_ARRAY:
					beginArray();
					open++;
					break;
				case BEGIN_OBJECT:
					beginObject();
					open++;
					break;
				case END_ARRAY:
					endArray();
					open--;
					break;
				case END_OBJECT:
					endObject();
					open--;
					break;
				case NAME:
					nextName();
					break;
				case STRING:
				case NUMBER:
					nextString();
					break;
				case BOOLEAN:
					nextBoolean();
					break;
				case NULL:
					nextNull();
					break;
				case END_DOCUMENT:
					throw syntax("Unexpected end of document");
			}
		} while (open > 0);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	private void expect(JsonToken t) throws IOException {
		JsonToken p = peek();
		if (p != t) throw syntax("Expected " + t + " but was " + p);
		peeked = null;
	}

	private void push(byte state) throws IOException {
		if (depth == MAX_DEPTH) throw syntax("Nesting too deep");
		stack[depth++] = state;
	}

	private JsonToken doPeek() throws IOException {
		byte state = stack[depth - 1];
		int c;

		switch (state) {
			case EMPTY_ARRAY:
				stack[depth - 1] = NONEMPTY_ARRAY;
				c = nextNonWhitespace();
				if (c == ']') return JsonToken.END_ARRAY;
				pos--;
				break;
			case NONEMPTY_ARRAY:
				c = nextNonWhitespace();
				if (c == ']') return JsonToken.END_ARRAY;
				if (c != ',') throw syntax("Expected ',' or ']'");
				break;
			case EMPTY_OBJECT:
			case NONEMPTY_OBJECT:
				c = nextNonWhitespace();
				if (c == '}') return JsonToken.END_OBJECT;
				if (state == NONEMPTY_OBJECT) {
					if (c != ',') throw syntax("Expected ',' or '}'");
					c = nextNonWhitespace();
				}
				if (c != '"') throw syntax("Expected a name");
				stack[depth - 1] = NONEMPTY_OBJECT;
				return JsonToken.NAME;
			case DANGLING_NAME:
				c = nextNonWhitespace();
				if (c != ':') throw syntax("Expected ':'");
				stack[depth - 1] = NONEMPTY_OBJECT;
				break;
			case EMPTY_DOC:
				stack[depth - 1] = NONEMPTY_DOC;
				break;
			default:
				if (nextNonWhitespace() == -1) return JsonToken.END_DOCUMENT;
				throw syntax("More than one value in the document");
		}

		c = nextNonWhitespace();
		switch (c) {
			case -1:
				throw syntax("Unexpected end of document");
			case '[':
				return JsonToken.BEGIN_ARRAY;
			case '{':
				return JsonToken.BEGIN_OBJECT;
			case '"':
				return JsonToken.STRING;
			case 't':
			case 'f':
				pos--;
				return JsonToken.BOOLEAN;
			case 'n':
				pos--;
				return JsonToken.NULL;
			default:
				if ((c == '-') || ((c >= '0') && (c <= '9'))) {
					pos--;
					return JsonToken.NUMBER;
				}
				throw syntax("Unexpected character '" + (char) c + "'");
		}
	}

	// After the opening quote
	private String readString() throws IOException {
		sb.setLength(0);
		for (; ; ) {
			int start = pos;
			while (pos < limit) {
				char c = buf[pos++];
				if (c == '"') {
					sb.append(buf, start, pos - start - 1);
					return sb.toString();
				}
				if (c == '\\') {
					sb.append(buf, start, pos - start - 1);
					readEscape();
					start = pos;
				}
			}
			sb.append(buf, start, pos - start);
			if (!fill()) throw syntax("Unterminated string");
		}
	}

	private void readEscape() throws IOException {
		int c = read();
		switch (c) {
			case 'n' -> sb.append('\n');
			case 't' -> sb.append('\t');
			case 'r' -> sb.append('\r');
			case 'b' -> sb.append('\b');
			case 'f' -> sb.append('\f');
			case 'u' -> {
				int v = 0;
				for (int i = 0; i < 4; i++) {
					int h = read();
					int d = Character.digit(h, 16);
					if (d < 0) throw syntax("Invalid unicode escape");
					v = (v << 4) | d;
				}
				sb.append((char) v);
			}
			case -1 -> throw syntax("Unterminated escape");
			default -> sb.append((char) c); // \" \\ \/ and lenient others
		}
	}

	// A number, true, false or null: up to the next delimiter
	private String readLiteral() throws IOException {
		sb.setLength(0);
		for (; ; ) {
			while (pos < limit) {
				char c = buf[pos];
				if ((c == ',') || (c == ']') || (c == '}') || (c == ':') || (c <= ' ')) {
					if (sb.length() == 0) throw syntax("Empty literal");
					return sb.toString();
				}
				sb.append(c);
				pos++;
			}
			if (!fill()) {
				if (sb.length() == 0) throw syntax("Empty literal");
				return sb.toString();
			}
		}
	}

	private int read() throws IOException {
		if ((pos == limit) && !fill()) return -1;
		return buf[pos++];
	}

	private int nextNonWhitespace() throws IOException {
		for (; ; ) {
			int c = read();
			if ((c != ' ') && (c != '\n') && (c != '\r') && (c != '\t') && (c != '﻿')) return c;
		}
	}

	private boolean fill() throws IOException {
		int n = in.read(buf, 0, buf.length);
		if (n <= 0) {
			pos = limit = 0;
			return false;
		}
		pos = 0;
		limit = n;
		return true;
	}

	private IOException syntax(String msg) {
		return new IOException("JSON: " + msg);
	}
}
