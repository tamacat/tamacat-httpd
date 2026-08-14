/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CacheLRUTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGet() {
		int maxSize = 3;
		CacheLRU<String,String> cache = new CacheLRU<String,String>(maxSize);
		cache.put("1", "1");
		cache.put("2", "2");
		cache.put("3", "3");
		assertEquals(maxSize, cache.size());
		//cache.get("2");
		//cache.get("1");
		//System.out.println(cache.toString());
		cache.put("4", "4");
		cache.put("5", "5");
		//System.out.println(cache.toString());
		assertEquals(maxSize, cache.size());
	}

	@Test
	public void testKeySet() {
		CacheLRU<String,String> cache = new CacheLRU<String,String>();
		cache.put("1", "1");
		cache.put("2", "2");
		cache.put("3", "3");
		assertTrue(3 == cache.keySet().size());

		assertTrue(3 == cache.values().size());

		assertNotNull(cache.toString());
	}
}
