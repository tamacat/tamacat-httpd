package org.tamacat.httpd.middleware;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TomcatEmbededTest {

	TomcatEmbeded app = new TomcatEmbeded();
	
	public void setUp() throws Exception {
	}

	@AfterEach
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
