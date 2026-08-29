package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RequestParametersTest {

	RequestParameters target;

	@BeforeEach
	public void setUp() throws Exception {
		target = new RequestParameters();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetParameter() {
		assertNull(target.getParameter("test"));

		target.setParameter("test", (String[])null);
		assertNull(target.getParameter("test"));
		assertEquals(null, target.getParameter("test-null"));

		target = new RequestParameters();
		target.setParameter("test", "ok");
		assertEquals("ok", target.getParameter("test"));
		assertEquals(null, target.getParameter("test-null"));

		target = new RequestParameters();
		target.setParameter("test", "");
		assertEquals("", target.getParameter("test"));
		
		assertEquals(null, target.getParameter("test-null"));
		
	}

	@Test
	public void testGetParameters() {
		assertNull(target.getParameters("test"));

		target.setParameter("test", "1","2","3");
		assertEquals(3, target.getParameters("test").length);
		
		assertEquals(null, target.getParameter("test-null"));
	}

	@Test
	public void testGetParameterNames() {
		assertNotNull(target.getParameterNames());
		assertEquals(0, target.getParameterNames().size());
	}

	@Test
	public void testGetParameterMap() {
		assertNotNull(target.getParameterMap());
		assertEquals(0, target.getParameterMap().size());
	}

}
