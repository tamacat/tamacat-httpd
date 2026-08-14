/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di;

import java.util.List;

/**
 * Interface of DI Container. (Inversion of Control Containers and the
 * Dependency Injection pattern)
 */
public interface DIContainer {

	/**
	 * Return an Object of the specified bean.
	 * 
	 * @param id
	 * @return return an instance
	 */
	public Object getBean(String id);

	/**
	 * Return an instance of the specified bean.
	 * 
	 * @param id the name of the bean to retrieve
	 * @param type class or interface
	 * @return return an instance
	 */
	public <T> T getBean(String id, Class<T> type);

	/**
	 * Return the instances in java.util.List.
	 * 
	 * @param type class or interface
	 * @return instances in java.util.List
	 */
	public <T> List<T> getInstanceOfType(Class<T> type);

	/**
	 * Remove an instance of the specified bean.
	 * 
	 * @param id
	 */
	void removeBean(String id);

	/**
	 * Remove the instances of the specified bean.
	 * 
	 * @param <T>
	 * @param type
	 */
	<T> void removeBeans(Class<T> type);
}
