package org.tamacat.httpd.core.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LimitedCacheLRUTest {

	LimitedCacheLRU<String, CacheImpl> cache;

	static class CacheImpl implements LimitedCacheObject {
		long created = System.currentTimeMillis();

		@Override
		public boolean isCacheExpired(long expire) {
			return System.currentTimeMillis() - created > expire;
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		cache = new LimitedCacheLRU<>(5, 10);
	}

	@AfterEach
	public void tearDown() throws Exception {
		cache.clear();
	}

	@Test
	public void testGet() {
		assertNull(cache.get("key1"));
	}

	@Test
	public void testLimitedCacheLRU() throws Exception {

		cache.put("test", new CacheImpl());
		assertNotNull(cache.get("test"));
		Thread.sleep(100);
		assertNull(cache.get("test"));

		cache = new LimitedCacheLRU<String, CacheImpl>(5, 1000);
		cache.put("test", new CacheImpl());
		assertNotNull(cache.get("test"));
		assertNotNull(cache.get("test"));
	}

}
