package org.tamacat.httpd.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServerUtilsTest {

	@BeforeEach
	public void setUp() throws Exception {
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetServerDocsRoot() {
		String serverHome = System.getProperty("server.home");
		String userDir = System.getProperty("user.dir");
		String home = serverHome != null ? serverHome : userDir;
		assertEquals((home + "/htdocs/root").replace("\\", "/"), ServerUtils.getServerDocsRoot("${server.home}/htdocs/root"));
	}
}
