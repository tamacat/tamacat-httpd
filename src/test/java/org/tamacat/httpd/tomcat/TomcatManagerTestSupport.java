package org.tamacat.httpd.tomcat;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;

/**
 * <p>Test-only access to {@link TomcatManager}'s static map, for tests in other
 * packages that start a server whose {@code TomcatEmbeded} middleware registers
 * and starts Tomcat instances (see {@code HttpdTest}). Not a test itself: the
 * name does not match the Surefire includes.</p>
 */
public final class TomcatManagerTestSupport {

	private TomcatManagerTestSupport() {}

	/** The ports currently registered in {@link TomcatManager#MANAGER}. */
	public static Set<Integer> ports() {
		synchronized (TomcatManager.class) {
			return new HashSet<>(TomcatManager.MANAGER.keySet());
		}
	}

	/**
	 * Waits until every instance on the given ports has left the starting
	 * states (it is STARTED, FAILED or later), so that stopping it cannot race
	 * with {@code TomcatManager.TomcatThread} still running {@code Tomcat.start()}.
	 * An instance nobody started stays NEW; it is waited for until the timeout.
	 * @return true if all instances finished starting within the timeout
	 */
	public static boolean awaitStartFinished(Collection<Integer> ports, long timeoutMillis)
			throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		for (Integer port : ports) {
			Tomcat tomcat = TomcatManager.MANAGER.get(port);
			if (tomcat == null) continue;
			while (isStarting(tomcat.getServer().getState())) {
				if (System.currentTimeMillis() > deadline) return false;
				Thread.sleep(50);
			}
		}
		return true;
	}

	static boolean isStarting(LifecycleState state) {
		return state == LifecycleState.NEW || state == LifecycleState.INITIALIZING
			|| state == LifecycleState.INITIALIZED || state == LifecycleState.STARTING_PREP
			|| state == LifecycleState.STARTING;
	}

	/**
	 * Stops and destroys the instances on the given ports and removes them from
	 * {@link TomcatManager#MANAGER} (the same clean-up as
	 * {@link TomcatEmbeddedStartupTest}), so a later {@code TomcatManager.start()}
	 * in this JVM does not start them again and their MBeans are unregistered.
	 */
	public static void shutdown(Collection<Integer> ports) {
		for (Integer port : ports) {
			TomcatEmbeddedStartupTest.shutdown(port, TomcatManager.MANAGER.get(port));
		}
	}
}
