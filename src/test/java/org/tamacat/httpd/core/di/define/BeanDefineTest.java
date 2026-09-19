package org.tamacat.httpd.core.di.define;

import junit.framework.TestCase;

import org.junit.Test;

public class BeanDefineTest extends TestCase {

	// Security/encapsulation regression: getPropertyList()/getConstructorArgs() must not expose
	// the live internal list — callers add via addProperty()/addConstructorArgs() instead
	// (CodeQL java/internal-representation-exposure).
	// Note: this class extends junit.framework.TestCase, so its tests are discovered by the
	// JUnit 3 "testXxx" naming convention, not by the @Test annotation (which is ignored in
	// JUnit 3-compatibility mode) — the method name must start with "test" to actually run.
	@Test
	public void testGetterListsAreUnmodifiable() {
		BeanDefine bean = new BeanDefine();
		bean.addProperty(new BeanDefineParam());
		bean.addConstructorArgs(new BeanConstructorParam());

		try {
			bean.getPropertyList().add(new BeanDefineParam());
			fail("getPropertyList() must return an unmodifiable view");
		} catch (UnsupportedOperationException expected) {
		}
		try {
			bean.getConstructorArgs().add(new BeanConstructorParam());
			fail("getConstructorArgs() must return an unmodifiable view");
		} catch (UnsupportedOperationException expected) {
		}
		assertEquals(1, bean.getPropertyList().size());
		assertEquals(1, bean.getConstructorArgs().size());
	}

	@Test
	public void testClone() {
		//Setup BeanDefine.
		BeanDefine org = new BeanDefine();
		org.addConstructorArgs(new BeanConstructorParam());
		org.addProperty(new BeanDefineParam());
		org.setId("Test");
		org.setAliases("t");
		org.setType(Integer.class);
		org.setSingleton(false);
		
		//Execute cloning.
		BeanDefine clone = org.clone();
		
		//Test of instance is equals.
		assertEquals(org.getId(), clone.getId());
		assertEquals(org.getAliases()[0], clone.getAliases()[0]);
		assertEquals(org.isSingleton(), clone.isSingleton());
		assertEquals(org.getType(), clone.getType());
		
		//Test of instance is not same. (org != clone)
		assertNotSame(org, clone);
		assertNotSame(org.getConstructorArgs(), clone.getConstructorArgs());
		assertNotSame(org.getPropertyList(), clone.getPropertyList());
	}
}
