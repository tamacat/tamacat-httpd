/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import java.util.HashMap;

import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineMap;
import org.tamacat.httpd.core.di.impl.DIContainerFactory;
import org.tamacat.httpd.core.di.impl.DefaultDIContainer;
import org.tamacat.httpd.core.util.ClassUtils;

/**
 * DI is creates {@link DIContainer}s from {@link BeanDefine}s 
 * or configuration file(XML).
 */
public final class DI {

	private final HashMap<ClassLoader, DIContainerFactory> manager;

	/**
	 * Creates an {@link DIContainer} for the given set of configuration file(XML).
	 * @param defines Configuration file(XML) in CLASSPATH.
	 * @return {@link DIContainer}
	 */
	public static synchronized DIContainer configure(String xml) {
		return configure(xml, Thread.currentThread().getContextClassLoader());
	}
	
	/**
	 * Creates an {@link DIContainer} for the given set of configuration file(XML).
	 * @param defines Configuration file(XML) in CLASSPATH.
	 * @param loader instance of ClassLoader
	 * @return {@link DIContainer}
	 */
	public static synchronized DIContainer configure(String xml, ClassLoader loader) {
		return DI.getFactory(loader).getInstance(xml);
	}
	
	/**
	 * Creates an {@link DIContainer} for the given set of defines.
	 * @param defines Array of {@link BeanDefine}.
	 * @return {@link DIContainer}
	 */
	public static DIContainer configure(BeanDefine... defines) {
		BeanDefineMap defineMap = new BeanDefineMap();
		for (BeanDefine def : defines) {
			defineMap.add(def);
		}
		return new DefaultDIContainer(defineMap, ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Creates an {@link DIContainer} for the given set of defines.
	 * @param defines BeanDefineMap, such as Map of {@link BeanDefine}.
	 * @param loader instance of ClassLoader
	 * @return {@link DIContainer}
	 */
	public static DIContainer configure(BeanDefineMap defines, ClassLoader loader) {
		return new DefaultDIContainer(defines, loader);
	}
	 
	/**
	 * Creates an {@link DIContainer} for the given set of defines.
	 * @param defines BeanDefineMap, such as Map of {@link BeanDefine}.
	 * @return {@link DIContainer}
	 */
	public static DIContainer configure(BeanDefineMap defines) {
		return configure(defines, ClassUtils.getDefaultClassLoader());
	}
	
	static final DI DI = new DI();
	
	private DI() {
		manager = new HashMap<ClassLoader, DIContainerFactory>();
		ClassLoader loader = getClass().getClassLoader();
		manager.put(loader, new DIContainerFactory(loader));
	}
	
	private DIContainerFactory getFactory(ClassLoader loader) {
		if (loader == null) {
			loader = ClassUtils.getDefaultClassLoader();
		}
		DIContainerFactory factory = manager.get(loader);
		if (factory == null) {
			factory = new DIContainerFactory(loader);
			manager.put(loader, factory);
		}
		return factory;
	}
}
