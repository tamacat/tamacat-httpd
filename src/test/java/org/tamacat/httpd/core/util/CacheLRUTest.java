/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CacheLRUTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
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
