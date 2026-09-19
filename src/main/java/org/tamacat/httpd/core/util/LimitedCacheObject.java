/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

public interface LimitedCacheObject {

	boolean isCacheExpired(long expire);
}
