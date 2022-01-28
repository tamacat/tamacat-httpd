/*
 * Copyright 2022 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TomcatHandlerTest {

	TomcatHandler handler;

	@Before
	public void setUp() throws Exception {
		handler = new TomcatHandler();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetUseWarDeploy() {
		assertEquals(true, handler.useWarDeploy);
		handler.setUseWarDeploy(false);
		assertEquals(false, handler.useWarDeploy);
	}

	@Test
	public void testSeUseBodyEncodingForURI() {
		assertEquals(null, handler.useBodyEncodingForURI);
		handler.seUseBodyEncodingForURI(false);
		assertEquals(false, handler.useBodyEncodingForURI);
		handler.seUseBodyEncodingForURI(true);
		assertEquals(true, handler.useBodyEncodingForURI);
	}

	@Test
	public void testSetBindAddress() {
		assertEquals("127.0.0.1", handler.bindAddress);
		handler.setBindAddress("0.0.0.0");
		assertEquals("0.0.0.0", handler.bindAddress);
	}

	@Test
	public void testSetScanBootstrapClassPath() {
		assertEquals(false, handler.scanBootstrapClassPath);
		handler.setScanBootstrapClassPath(true);
		assertEquals(true, handler.scanBootstrapClassPath);
	}

	@Test
	public void testSetScanClassPath() {
		assertEquals(true, handler.scanClassPath);
		handler.setScanClassPath(false);
		assertEquals(false, handler.scanClassPath);
	}

	@Test
	public void testSetScanManifest() {
		assertEquals(false, handler.scanManifest);
		handler.setScanManifest(true);
		assertEquals(true, handler.scanManifest);
	}

	@Test
	public void testSetScanAllDirectories() {
		assertEquals(true, handler.scanAllDirectories);
		handler.setScanAllDirectories(false);
		assertEquals(false, handler.scanAllDirectories);
	}

	@Test
	public void testSetScanAllFiles() {
		assertEquals(false, handler.scanAllFiles);
		handler.setScanAllFiles(true);
		assertEquals(true, handler.scanAllFiles);
	}
}
