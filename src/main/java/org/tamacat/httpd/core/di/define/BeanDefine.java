/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.define;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.tamacat.httpd.core.util.ClassUtils;

public class BeanDefine implements Cloneable {

	private String id;
	private List<String> aliases = new ArrayList<>();
	private Class<?> type;
	private Method initMethod;
	private Method factoryMethod;

	private boolean isSingleton; // = true; -> v1.1 default false
	private List<BeanDefineParam> properties = new ArrayList<>();
	private List<BeanConstructorParam> constructorArgs = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Class<?> getType() {
		return type;
	}

	public void setType(Class<?> type) {
		this.type = type;
	}

	public void setType(String className, ClassLoader loader) {
		this.type = (loader != null) ? ClassUtils.forName(className, loader) : ClassUtils.forName(className);
	}

	public void setInitMethod(String initMethod) {
		this.initMethod = ClassUtils.getMethod(getType(), initMethod, (Class[]) null);
	}

	public void setInitMethod(Method initMethod) {
		this.initMethod = initMethod;
	}

	public boolean useInitMethod() {
		return initMethod != null;
	}

	public Method getInitMethod() {
		return initMethod;
	}

	public boolean isSingleton() {
		return isSingleton;
	}

	public void setSingleton(boolean isSingleton) {
		this.isSingleton = isSingleton;
	}

	public List<BeanDefineParam> getPropertyList() {
		return properties;
	}

	public void addConstructorArgs(BeanConstructorParam param) {
		constructorArgs.add(param);
	}

	public List<BeanConstructorParam> getConstructorArgs() {
		return constructorArgs;
	}

	/**
	 * Set the alias of id. (Two or more values can also be set by comma
	 * separated values.)
	 */
	public void setAliases(String aliases) {
		if (aliases == null || aliases.trim().length() == 0)
			return;
		if (aliases.indexOf(',') > 0) {
			StringTokenizer token = new StringTokenizer(aliases, ",");
			while (token.hasMoreTokens()) {
				this.aliases.add(token.nextToken().trim());
			}
		} else {
			this.aliases.add(aliases.trim());
		}
	}

	public String[] getAliases() {
		return aliases != null ? aliases.toArray(new String[aliases.size()]) : null;
	}

	public Method getFactoryMethod() {
		return factoryMethod;
	}

	public void setFactoryMethod(Method factoryMethod) {
		this.factoryMethod = factoryMethod;
	}

	public void setFactoryMethod(String factoryMethod) {
		if (factoryMethod != null) {
			this.factoryMethod = ClassUtils.getStaticMethod(type, factoryMethod);
		}
	}

	@Override
	public BeanDefine clone() {
		try {
			BeanDefine clone = (BeanDefine) super.clone();
			clone.constructorArgs = new ArrayList<BeanConstructorParam>();
			for (BeanConstructorParam param : constructorArgs) {
				clone.constructorArgs.add(param.clone());
			}
			clone.properties = new ArrayList<BeanDefineParam>();
			for (BeanDefineParam param : properties) {
				clone.properties.add(param.clone());
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.getMessage());
		}
	}

}