package org.tamacat.httpd;

//import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.core.HttpEngine;
import org.tamacat.httpd.core.HttpEngineTestSupport;
import org.tamacat.httpd.core.di.DI;
import org.tamacat.httpd.core.di.DIContainer;
import org.tamacat.httpd.core.di.impl.DIContainerTestSupport;
import org.tamacat.httpd.tomcat.TomcatManagerTestSupport;

/**
 * <p>{@link Httpd#main} starts the default server (httpd.xml) in a background
 * thread and returns. The server's TomcatEmbeded middleware calls
 * TomcatManager.start() over the static TomcatManager map, so a server left
 * running would start Tomcat instances that later tests register. This test
 * therefore waits until the background server has finished starting, then
 * stops it, stops and removes the Tomcat instances it registered, and waits
 * for the threads it started.</p>
 */
public class HttpdTest {

	static final long STARTUP_TIMEOUT_MILLIS = 60000;
	static final long THREAD_JOIN_MILLIS = 10000;

	DIContainer di;
	boolean wasSingleton;
	Set<Thread> threadsBefore;
	Set<Integer> tomcatPortsBefore;

	@BeforeEach
	public void setUp() throws Exception {
		// Httpd.main gets the "server" bean from the same cached container. While
		// it is a singleton, tearDown() gets the very instance main started.
		di = DI.configure(Httpd.XML);
		wasSingleton = DIContainerTestSupport.setSingleton(di, "server", true);
		threadsBefore = new HashSet<>(Thread.getAllStackTraces().keySet());
		tomcatPortsBefore = TomcatManagerTestSupport.ports();
	}

	@AfterEach
	public void tearDown() throws Exception {
		try {
			Set<Thread> started = new HashSet<>(Thread.getAllStackTraces().keySet());
			started.removeAll(threadsBefore);
			HttpEngine server = di.getBean("server", HttpEngine.class);

			// Wait until init() (handlers, middleware start-up) is done and the
			// engine listens, or until its thread has ended (e.g. port in use).
			long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MILLIS;
			while (!HttpEngineTestSupport.isListening(server) && anyAlive(started)
					&& System.currentTimeMillis() < deadline) {
				Thread.sleep(50);
			}
			Set<Integer> tomcatPorts = TomcatManagerTestSupport.ports();
			tomcatPorts.removeAll(tomcatPortsBefore);
			TomcatManagerTestSupport.awaitStartFinished(tomcatPorts, STARTUP_TIMEOUT_MILLIS);

			server.stopHttpd(); // middleware shutdown (TomcatManager.stop()), socket close, executor shutdown
			TomcatManagerTestSupport.shutdown(tomcatPorts);

			Set<Thread> all = new HashSet<>(Thread.getAllStackTraces().keySet());
			all.removeAll(threadsBefore);
			for (Thread t : all) {
				t.join(THREAD_JOIN_MILLIS);
			}
		} finally {
			DIContainerTestSupport.setSingleton(di, "server", wasSingleton);
		}
	}

	static boolean anyAlive(Set<Thread> threads) {
		for (Thread t : threads) {
			if (t.isAlive()) return true;
		}
		return false;
	}

	@Test
	public void testMain() {
		Httpd.main(new String[] {});
	}

}
