/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.util.Collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilities of Collection.
 */
public class CollectionUtils {

	public static Map<String, String> convertMap(
			final Collection<Map<String, String>> list, final Map<String, String> remake,
			final String key, final String value) {
		for (Map<String, String> map : list) {
			remake.put(map.get(key), map.get(value));
		}
		return remake;
	}
	
	public static <K, V> Map<K, V> convertMap(
			final Map<K, V> map, final Collection<K> keys, final Collection<V> values) {
		if (keys.size() != values.size()) {
			throw new IllegalArgumentException("Invalid Collection. Collection size is not equals.");
		}
		Iterator<V> valueIt = values.iterator();
		for (K key : keys) {
			map.put(key, valueIt.next());
		}
		return map;
	}
	
	public static <E> List<E> newArrayList() {
		return new ArrayList<E>();
	}
	
	public static <E> Set<E> newHashSet() {
		return new HashSet<E>();
	}
	
	public static <K, V> Map<K, V> newHashMap() {
		return new HashMap<K, V>();
	}
	
	public static <K, V> Map<K, V> newLinkedHashMap() {
		return new LinkedHashMap<K, V>();
	}
}
