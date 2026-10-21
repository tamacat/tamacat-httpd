/*
 * Copyright 2022 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.config.ServiceUrl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class TomcatHandlerTest {

	TomcatHandler handler;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new TomcatHandler();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetUseWarDeploy() {
		assertEquals(false, handler.useWarDeploy);
		handler.setUseWarDeploy(true);
		assertEquals(true, handler.useWarDeploy);
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

	/**
	 * A service path that cannot form a URL leaves the reverse URL unset, so every
	 * request to the service is answered with 503. Startup still continues as
	 * before, but the cause is now logged as a WARN naming the path.
	 */
	@Test
	public void testInvalidServicePathLogsWarn() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(TomcatHandler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		int port = TomcatEmbeddedStartupTest.findFreePort();
		TomcatHandler handler = new TomcatEmbeddedStartupTest().newHandler(port);
		ServiceUrl serviceUrl = new TomcatEmbeddedStartupTest().newServiceUrl("/bad path/");
		//Give the webapp a docBase, so Tomcat does not log a NoSuchFileException
		//trace while looking for its context.xml. Only the reverse URL is under test.
		File docBase = new File(handler.getWebapps(), "bad path");
		assertTrue(docBase.isDirectory() || docBase.mkdirs(), "cannot create " + docBase);
		Tomcat tomcat = null;
		try {
			handler.setServiceUrl(serviceUrl); //must not throw: startup continues
			tomcat = handler.tomcat;

			assertNull(serviceUrl.getReverseUrl(), "the behaviour is unchanged: no reverse URL is set");
			ILoggingEvent warn = null;
			for (ILoggingEvent event : appender.list) {
				if (event.getLevel() == Level.WARN
						&& event.getFormattedMessage().contains("\"/bad path/\"")) {
					warn = event;
				}
			}
			assertNotNull(warn, "no WARN naming the service path was logged; events were " + appender.list);
			assertTrue(warn.getFormattedMessage().contains("Illegal character"),
				"the WARN does not carry the exception message: " + warn.getFormattedMessage());
		} finally {
			logger.detachAppender(appender);
			appender.stop();
			TomcatEmbeddedStartupTest.shutdown(port, tomcat);
		}
	}
}
