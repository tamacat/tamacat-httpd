package org.tamacat.httpd.core.jmx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class URLBasicCounterTest {
	
	URLBasicCounter counter;
	
	@BeforeEach
	public void setUp() throws Exception {
		counter = new URLBasicCounter();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetCounter() {
		assertNull(counter.getCounter("test"));
		
		counter.register("test");
		assertNotNull(counter.getCounter("test"));
	}

	@Test
	public void testSetObjectName() {
		counter.setObjectName("org.tamacat.httpd:type=URL#");
	}

}
