/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.core.di.fixture.Core;
import org.tamacat.httpd.core.di.fixture.SampleCore;
import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineMap;
import org.tamacat.httpd.core.util.ClassUtils;

public class DITest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testConfigure() {
		DIContainer di = DI.configure("test.xml");
		assertEquals(SampleCore.class, di.getBean("Core").getClass());
	}
	
	@Test
	public void testConfigureBeanDefine() {
		BeanDefine define = new BeanDefine();
		define.setId("Core");
		define.setType(SampleCore.class);
		DIContainer di = DI.configure(define);
		assertEquals(SampleCore.class, di.getBean("Core").getClass());
	}
	
	@Test
	public void testConfigureBeanDefineMap() {
		BeanDefineMap map = new BeanDefineMap();
		BeanDefine define = new BeanDefine();
		define.setId("Core");
		define.setType(SampleCore.class);
		map.add(define);
		
		DIContainer di = DI.configure(map);
		assertEquals(SampleCore.class, di.getBean("Core").getClass());
	}
	
	@Test
	public void testDI() {
		DI di = ClassUtils.newInstance(DI.class);
		assertNull(di);
	}
	
	@Test
	public void testRepeat() {
		DIContainer di = DI.configure("test.xml");
		Core core = di.getBean("Core3", Core.class);
		assertEquals("CoreName", core.getCoreName());
		
		Core core2 = di.getBean("Core3", Core.class);
		assertEquals("CoreName", core2.getCoreName());
		
		assertNotSame(core, core2);
	}
}
