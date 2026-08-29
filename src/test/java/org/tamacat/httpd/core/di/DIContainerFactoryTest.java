/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.core.di.impl.DIContainerFactory;

public class DIContainerFactoryTest {

	
	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetInstance() {
		DIContainer di1 = new DIContainerFactory(null).getInstance("test.xml");
		DIContainer di2 = new DIContainerFactory(null).getInstance("test.xml");
		assertSame(di1, di2);
	}

}
