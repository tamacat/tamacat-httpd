package org.tamacat.httpd.filter.acl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AccessUrlCacheTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testAccessUrlCache() {
		AccessUrlCache cache = new AccessUrlCache(3, 10000);
		cache.put("", new AccessUrl() {
			
			@Override
			public boolean isCacheExpired(long expire) {
				return false;
			}
			
			@Override
			public boolean isSuccess() {
				return false;
			}
		});
	}

}
