package org.tamacat.httpd.core.di.define;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class BeanDefineTest {

	@Test
	public void testClone() {
		//Setup BeanDefine.
		BeanDefine org = new BeanDefine();
		org.addConstructorArgs(new BeanConstructorParam());
		org.getPropertyList().add(new BeanDefineParam());
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
