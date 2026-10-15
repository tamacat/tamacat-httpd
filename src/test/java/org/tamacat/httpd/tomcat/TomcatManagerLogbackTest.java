/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.tomcat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.read.ListAppender;

/**
 * <p>logback-classic registers a ServletContainerInitializer whose listener stops
 * the JVM-wide LoggerContext when a web application stops. Before 1.7.0 this meant
 * that stopping any embedded Tomcat webapp ended all logging of the server.
 * {@link TomcatManager#getInstance(int)} now sets
 * {@code logbackDisableServletContainerInitializer=true} unless the user set it.
 *
 * <p>Every test saves and restores the system property, removes the Tomcat it
 * registered from {@link TomcatManager}'s static map, and, if the LoggerContext was
 * stopped, re-runs logback's own auto-configuration so later tests in the same JVM
 * log exactly as before.
 */
public class TomcatManagerLogbackTest {

	static final String KEY = TomcatManager.LOGBACK_DISABLE_SCI_KEY;

	/** The logger the assertions observe: an ordinary application logger. */
	static final Logger APP_LOG = (Logger) LoggerFactory.getLogger(TomcatManagerLogbackTest.class);

	String savedProperty;
	int port;
	Tomcat tomcat;
	ListAppender<ILoggingEvent> appender;

	@BeforeEach
	public void setUp() throws Exception {
		savedProperty = System.getProperty(KEY);
		//An environment variable would take part in the decision; these tests
		//control the system property only.
		assumeTrue(System.getenv(KEY) == null, "the environment variable " + KEY + " is set");
		port = TomcatEmbeddedStartupTest.findFreePort();
	}

	@AfterEach
	public void tearDown() throws Exception {
		try {
			TomcatEmbeddedStartupTest.shutdown(port, tomcat);
		} finally {
			if (appender != null) {
				APP_LOG.detachAppender(appender);
				appender.stop();
			}
			restoreLoggingIfStopped();
			if (savedProperty == null) {
				System.clearProperty(KEY);
			} else {
				System.setProperty(KEY, savedProperty);
			}
		}
	}

	/**
	 * The key must be the one logback reads, or the default does nothing.
	 */
	@Test
	public void testGetInstanceDisablesTheInitializerWhenUnset() {
		assertEquals(CoreConstants.DISABLE_SERVLET_CONTAINER_INITIALIZER_KEY, KEY);
		System.clearProperty(KEY);

		tomcat = TomcatManager.getInstance(port);

		assertEquals("true", System.getProperty(KEY),
			"getInstance must disable logback's ServletContainerInitializer when nobody set it");
	}

	/**
	 * An explicit user value, including "false", is not overwritten.
	 */
	@Test
	public void testGetInstanceKeepsAnExplicitValue() {
		System.setProperty(KEY, "false");

		tomcat = TomcatManager.getInstance(port);

		assertEquals("false", System.getProperty(KEY),
			"an explicit value given by the user must be respected");
	}

	/**
	 * The fix: a webapp starts and stops, and the application keeps logging.
	 */
	@Test
	public void testApplicationLoggingContinuesAfterAWebappStops() throws Exception {
		System.clearProperty(KEY);

		startAndStopTheExamplesWebapp();

		APP_LOG.info("logged after the webapp stopped");
		assertTrue(loggerContext().isStarted(), "the LoggerContext was stopped when the webapp stopped");
		assertTrue(containsMessage("logged after the webapp stopped"),
			"application logging stopped after the embedded webapp stopped");
	}

	/**
	 * Positive control for the test above: with the initializer kept enabled by the
	 * user, the same steps stop the LoggerContext. This shows that the webapp really
	 * started the initializer, so the test above is not passing vacuously. Logging
	 * is restored in {@link #tearDown()} and checked at the end of this test.
	 */
	@Test
	public void testLoggingStopsWhenTheUserKeepsTheInitializerEnabled() throws Exception {
		System.setProperty(KEY, "false");

		startAndStopTheExamplesWebapp();

		APP_LOG.info("logged after the webapp stopped");
		boolean stopped = !loggerContext().isStarted();
		boolean received = containsMessage("logged after the webapp stopped");

		restoreLoggingIfStopped();
		ListAppender<ILoggingEvent> after = new ListAppender<>();
		after.start();
		APP_LOG.addAppender(after);
		try {
			APP_LOG.info("logged after logging was restored");
			assertEquals(1, after.list.size(), "logging was not restored for later tests");
		} finally {
			APP_LOG.detachAppender(after);
			after.stop();
		}
		assertNotNull(loggerContext().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("STDOUT"),
			"the configured STDOUT appender was not restored for later tests");

		assertTrue(stopped, "with the user's value \"false\", stopping the webapp should stop the "
			+ "LoggerContext. It did not: either the user's value was overwritten or the "
			+ "initializer never ran, and then the test above proves nothing");
		assertFalse(received, "a message logged after the LoggerContext stopped was still delivered");
	}

	// ------------------------------------------------------------------ helpers

	void startAndStopTheExamplesWebapp() throws Exception {
		appender = new ListAppender<>();
		appender.start();
		APP_LOG.addAppender(appender);

		TomcatHandler handler = new TomcatEmbeddedStartupTest().newHandler(port);
		handler.setServiceUrl(new TomcatEmbeddedStartupTest().newServiceUrl("/examples/"));
		tomcat = handler.tomcat;
		assertNotNull(tomcat, "deploy() should have obtained a Tomcat instance");
		TomcatEmbeddedStartupTest.start(tomcat, port);
		Context ctx = TomcatEmbeddedStartupTest.findContext(tomcat, "/examples");
		assertNotNull(ctx, "the /examples webapp was not deployed");
		assertEquals(LifecycleState.STARTED, ctx.getState(), "the /examples webapp did not start");

		tomcat.stop();
	}

	boolean containsMessage(String message) {
		for (ILoggingEvent event : appender.list) {
			if (message.equals(event.getFormattedMessage())) {
				return true;
			}
		}
		return false;
	}

	static LoggerContext loggerContext() {
		return (LoggerContext) LoggerFactory.getILoggerFactory();
	}

	/**
	 * Re-runs logback's own start-up sequence (reset, auto-configuration, start) if
	 * the LoggerContext was stopped, so later tests log with the same configuration.
	 */
	static void restoreLoggingIfStopped() throws Exception {
		LoggerContext context = loggerContext();
		if (!context.isStarted()) {
			context.reset();
			new ContextInitializer(context).autoConfig();
			context.start();
		}
	}
}
