/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

public class LimitedCacheLRU<K, V extends LimitedCacheObject> extends CacheLRU<K, V> {
	
	protected long expire;
	
	public LimitedCacheLRU(int maxSize, long expire) {
		super(maxSize);
		this.expire = expire;
	}
	
	@Override
	public V get(K key) {
		V obj = super.get(key);
		if (obj != null) {
			if (obj.isCacheExpired(expire)) {
				super.remove(key);
				return null;
			}
		}
		return obj;
	}
}
