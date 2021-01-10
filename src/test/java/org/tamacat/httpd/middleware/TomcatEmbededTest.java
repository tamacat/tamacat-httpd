package org.tamacat.httpd.middleware;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

public class TomcatEmbededTest {

	TomcatEmbeded app = new TomcatEmbeded();
	
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testStartup() {
		app.startup();
		assertNotNull(app);
	}

	@Test
	public void testShutdown() {
		app.shutdown();
	}

}
