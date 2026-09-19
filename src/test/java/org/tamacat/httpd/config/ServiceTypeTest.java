/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.*;

import org.junit.Test;

public class ServiceTypeTest {

	@Test
	public void testFind() {
		assertEquals(ServiceType.NORMAL, ServiceType.find("normal"));
		assertEquals(ServiceType.REVERSE, ServiceType.find("reverse"));
		assertEquals(ServiceType.ERROR, ServiceType.find("error"));

		try {
			ServiceType.find("test");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(true);
		}

		// type="lb" (load balancing) removed in 1.6.0 along with the config.lb
		// package [BR-9]; ServiceType.LB no longer exists, so "lb" now fails
		// the same way as any other unrecognized type string (required
		// remediation of this existing assertion, which previously read
		// assertEquals(ServiceType.LB, ServiceType.find("lb")) and no longer
		// compiles).
		try {
			ServiceType.find("lb");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(true);
		}
	}
}
