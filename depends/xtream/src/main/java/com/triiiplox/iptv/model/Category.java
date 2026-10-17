package com.triiiplox.iptv.model;

public final class Category {
	public final Entry.Kind kind;
	public final String id;
	public final String name;

	public Category(Entry.Kind kind, String id, String name) {
		this.kind = kind;
		this.id = id;
		this.name = name;
	}

	@Override
	public String toString() {
		return kind.code + " category " + id + ' ' + name;
	}
}
