/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

public interface StringValueConverter<T> {

	Class<T> getType();

	T convert(String param);
}
