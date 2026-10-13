package org.tamacat.httpd.core.di.define;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class BeanDefineTest {

	// Security/encapsulation regression: getPropertyList()/getConstructorArgs() must not expose
	// the live internal list — callers add via addProperty()/addConstructorArgs() instead
	// (CodeQL java/internal-representation-exposure).
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
