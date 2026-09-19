/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

public class BeanAdapter<T> {

	private T instance;

	private String id;

	private Class<T> type;

	public BeanAdapter(String id, Class<T> type, T instance) {
		this.id = id;
		this.type = type;
		this.instance = instance;
	}

	public String getId() {
		return id;
	}

	public T getInstance() {
		return instance;
	}

	public Class<T> getType() {
		return type;
	}
}
