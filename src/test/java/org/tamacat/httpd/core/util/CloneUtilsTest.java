package org.tamacat.httpd.core.util;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Test;

public class CloneUtilsTest {

	@Test
	public void testClone() throws CloneNotSupportedException {
		new CloneUtils();
		
		assertNull(CloneUtils.clone(null));
		try {
			assertNull(CloneUtils.clone(new Target()));
			fail();
		} catch (CloneNotSupportedException e) {
			//OK
		}
		try {
			assertNull(CloneUtils.clone(new TargetCloneable()));
			fail();
		} catch (NoSuchMethodError e) {
			//OK
		}
		HashMap<String, String> test = new HashMap<String, String>();
		test.put("key1", "value1");
		HashMap<String, String> clone = CloneUtils.clone(test);
		assertEquals(clone.get("key1"), test.get("key1"));
	}

	static class Target {
		String name;
	}
	
	static class TargetCloneable implements Cloneable {
		String name;
	}
}
