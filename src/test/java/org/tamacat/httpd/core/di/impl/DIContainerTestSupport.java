package org.tamacat.httpd.core.di.impl;

import org.tamacat.httpd.core.di.DIContainer;
import org.tamacat.httpd.core.di.define.BeanDefine;

/**
 * <p>Test-only access to the bean definitions of a {@link DefaultDIContainer}.
 * Not a test itself: the name does not match the Surefire includes.</p>
 */
public final class DIContainerTestSupport {

	private DIContainerTestSupport() {}

	/**
	 * Sets whether the bean {@code id} is a singleton. While it is, the
	 * container returns the instance it created first, so a test can reach the
	 * bean that other code obtained from the same (cached) container.
	 * @return the previous setting
	 */
	public static boolean setSingleton(DIContainer di, String id, boolean singleton) {
		BeanDefine def = ((DefaultDIContainer) di).creator.getDefines().get(id);
		if (def == null) {
			throw new IllegalArgumentException(id + " is not defined.");
		}
		boolean previous = def.isSingleton();
		def.setSingleton(singleton);
		return previous;
	}
}
