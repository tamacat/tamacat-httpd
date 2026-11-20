/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.mock.LogCapture;

/**
 * <p>Stopping an embedded Tomcat webapp must not end the logging of the server.
 *
 * <p>The logging back end is tamacat-logging, which registers no
 * ServletContainerInitializer and has no stoppable context, so nothing in a webapp
 * can stop it. With logback-classic, which earlier 1.7 builds used, its initializer
 * did stop the JVM-wide LoggerContext when a webapp stopped, and
 * {@code TomcatManager} had to switch that off. This test keeps the behaviour
 * pinned for whichever SLF4J provider is on the class path.
 *
 * <p>Every test removes the Tomcat it registered from {@link TomcatManager}'s static
 * map, because it lives as long as the JVM.
 */
public class TomcatManagerLoggingTest {

	/** The logger the assertions observe: an ordinary application logger. */
	static final Logger APP_LOG = LoggerFactory.getLogger(TomcatManagerLoggingTest.class);

	int port;
	Tomcat tomcat;

	@BeforeEach
	public void setUp() throws Exception {
		port = TomcatEmbeddedStartupTest.findFreePort();
	}

	@AfterEach
	public void tearDown() throws Exception {
		TomcatEmbeddedStartupTest.shutdown(port, tomcat);
	}

	/**
	 * A webapp starts and stops, and the application keeps logging.
	 */
	@Test
	public void testApplicationLoggingContinuesAfterAWebappStops() throws Exception {
		try (LogCapture log = LogCapture.start()) {
			TomcatHandler handler = new TomcatEmbeddedStartupTest().newHandler(port);
			handler.setServiceUrl(new TomcatEmbeddedStartupTest().newServiceUrl("/examples/"));
			tomcat = handler.tomcat;
			assertNotNull(tomcat, "deploy() should have obtained a Tomcat instance");
			TomcatEmbeddedStartupTest.start(tomcat, port);
			Context ctx = TomcatEmbeddedStartupTest.findContext(tomcat, "/examples");
			assertNotNull(ctx, "the /examples webapp was not deployed");
			assertEquals(LifecycleState.STARTED, ctx.getState(), "the /examples webapp did not start");

			tomcat.stop();
			APP_LOG.info("logged after the webapp stopped");

			assertTrue(log.entries(TomcatManagerLoggingTest.class).stream()
					.anyMatch(e -> "INFO".equals(e.level())
							&& "logged after the webapp stopped".equals(e.message())),
				"application logging stopped after the embedded webapp stopped; events were "
					+ log.entries());
		}
	}
}
