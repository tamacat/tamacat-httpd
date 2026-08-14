/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.core.di.impl.DIContainerFactory;

public class DIContainerFactoryTest {

	
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetInstance() {
		DIContainer di1 = new DIContainerFactory(null).getInstance("test.xml");
		DIContainer di2 = new DIContainerFactory(null).getInstance("test.xml");
		assertSame(di1, di2);
	}

}
