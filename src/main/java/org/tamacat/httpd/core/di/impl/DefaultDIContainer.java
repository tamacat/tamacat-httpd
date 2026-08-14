/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import java.io.PrintStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map.Entry;

import org.tamacat.httpd.core.di.DIContainer;
import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineMap;
import org.tamacat.httpd.core.di.xml.SpringBeanDefineHandler;
import org.tamacat.httpd.core.util.ClassUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class DefaultDIContainer implements DIContainer {

	static final String PROPERTIES_FILE = "org.tamacat.di.DIContainer.properties";
	static final String BEAN_DEFINE_HANDLER_KEY = "DIContainerBeanDefineHandler";

	protected ClassLoader loader;
	protected BeanDefineHandler beanDefineHandler;
	protected BeanCreator creator;
	protected Properties props;

	public DefaultDIContainer(String xml) {
		init(xml, getClassLoader());
	}

	public DefaultDIContainer(String xml, ClassLoader parent) {
		init(xml, getClassLoader(parent));
	}

	public DefaultDIContainer(BeanDefineMap defines, ClassLoader parent) {
		init(defines, getClassLoader(parent));
	}

	@Override
	public Object getBean(String id) {
		return creator.getBean(id, null);
	}

	@Override
	public <T> T getBean(String id, Class<T> type) {
		return creator.getBean(id, type);
	}

	@Override
	public void removeBean(String id) {
		creator.removeBean(id);
	}

	@Override
	public <T> void removeBeans(Class<T> type) {
		creator.removeBeans(type);
	}

	@Override
	public <T> List<T> getInstanceOfType(Class<T> type) {
		List<T> list = new ArrayList<T>();
		if (type == null)
			return list;
		for (Entry<String, BeanDefine> entry : creator.getDefines().entrySet()) {
			if (ClassUtils.isTypeOf(entry.getValue().getType(), type)) {
				list.add(getBean(entry.getValue().getId(), type));
			}
		}
		return list;
	}

	public void trace(PrintStream out) {
		out.println("-------------------------------------------");
		out.println("ClassLoader: " + getClassLoader().toString());
		for (Entry<String, BeanDefine> entry : creator.getDefines().entrySet()) {
			out.println(entry.getKey() + "=" + entry.getValue());
		}
		out.println("-------------------------------------------");
	}

	void loadProperties() {
		props = PropertyUtils.getProperties(PROPERTIES_FILE, getClassLoader());
	}

	protected ClassLoader getClassLoader() {
		if (loader == null) {
			loader = ClassUtils.getDefaultClassLoader();
		}
		return loader;
	}

	protected ClassLoader getClassLoader(ClassLoader parent) {
		if (loader == null) {
			loader = parent;
		}
		return loader;
	}

	BeanDefineHandler loadBeanDefineHandler() {
		String className = props.getProperty(BEAN_DEFINE_HANDLER_KEY);
		Class<?> handlerClass = null;
		try {
			handlerClass = getClassLoader().loadClass(className);
		} catch (ClassNotFoundException e) {
			handlerClass = SpringBeanDefineHandler.class;
		}
		beanDefineHandler = (BeanDefineHandler) ClassUtils.newInstance(handlerClass);
		return beanDefineHandler;
	}

	private void init(String xml, ClassLoader loader) {
		loadProperties();
		this.creator = new BeanCreator(load(xml), getClassLoader());
	}

	private void init(BeanDefineMap defines, ClassLoader loader) {
		loadProperties();
		this.creator = new BeanCreator(defines, getClassLoader());
	}

	private BeanDefineMap load(String xml) {
		BeanDefineHandler handler = loadBeanDefineHandler();
		handler.setClassLoader(getClassLoader());
		handler.setConfigurationFile(xml);
		return handler.getBeanDefines();
	}
}
