/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import java.util.HashMap;
import java.util.Properties;

import org.tamacat.httpd.core.di.DIContainer;
import org.tamacat.httpd.core.util.ClassUtils;
import org.tamacat.httpd.core.util.PropertyUtils;
import org.tamacat.httpd.core.util.ResourceNotFoundException;

public class DIContainerFactory {

	private static final String PROPERTIES_FILE = "org.tamacat.di.DIContainerFactory.properties";
	private static final String CUSTOM_PROPERTIES_FILE = "org.tamacat.di.CustomDIContainerFactory.properties";
	private static HashMap<String, DIContainer> manager = new HashMap<>();

	private Class<?> defaultDIContainerClass;
	private ClassLoader loader;

	// Load default DIContainer Class.
	public DIContainerFactory(final ClassLoader loader) {
		this.loader = loader;
		if (this.loader == null) {
			this.loader = ClassUtils.getDefaultClassLoader();
		}
		String className = getDIContainerClass(CUSTOM_PROPERTIES_FILE);
		if (className == null) {
			className = getDIContainerClass(PROPERTIES_FILE);
		}
		try {
			defaultDIContainerClass = ClassUtils.forName(className, this.loader);
		} catch (Exception e) {
		}
		if (defaultDIContainerClass == null) {
			defaultDIContainerClass = DefaultDIContainer.class;
		}
	}

	protected String getDIContainerClass(String name) {
		try {
			Properties props = PropertyUtils.getProperties(name, this.loader);
			if (props != null) {
				return props.getProperty("DIContainerClass");
			}
		} catch (ResourceNotFoundException e) {
		}
		return null;
	}

	public synchronized DIContainer getInstance(String file) {
		DIContainer di = manager.get(file);
		if (di == null) {
			Object[] args = { file, loader };
			Class<?>[] types = { String.class, ClassLoader.class };
			di = (DIContainer) ClassUtils.newInstance(defaultDIContainerClass, types, args);
			manager.put(file, di);
		}
		return di;
	}
}
