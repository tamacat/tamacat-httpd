/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.impl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tamacat.httpd.core.di.DIContainerException;
import org.tamacat.httpd.core.di.define.BeanConstructorParam;
import org.tamacat.httpd.core.di.define.BeanDefine;
import org.tamacat.httpd.core.di.define.BeanDefineMap;
import org.tamacat.httpd.core.di.define.BeanDefineParam;
import org.tamacat.httpd.core.util.ClassUtils;
import org.tamacat.httpd.core.util.ClassUtilsException;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * Create beans for DIContainer.
 */
public class BeanCreator {

	static final Map<String, BeanAdapter<Object>> beans = new HashMap<>();

	protected final BeanDefineMap defines;

	protected final ClassLoader loader;

	public BeanCreator(BeanDefineMap defines) {
		this.defines = defines;
		this.loader = getClass().getClassLoader();
		initDefines();
	}

	public BeanCreator(BeanDefineMap defines, ClassLoader loader) {
		this.defines = defines;
		this.loader = loader;
		initDefines();
	}

	public BeanDefineMap getDefines() {
		return defines;
	}

	public ClassLoader getClassLoader() {
		return loader;
	}

	/**
	 * Get bean for DIContainer
	 * 
	 * @param id
	 * @return Object
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public synchronized <T> T getBean(String id, Class<T> type) {
		BeanDefine def = defines.get(id);
		if (def == null) {
			throw new DIContainerException("Error: [" + id + "] is not found in BeanDefines.");
		}

		// singleton instance
		if (def.isSingleton()) {
			// instance is null then create new instance.
			BeanAdapter<Object> adapter = beans.get(id);
			if (adapter == null) {
				Object instance = newInstance(def);
				if (instance == null) {
					throw new ClassUtilsException("id=" + id + " instance is null. class=" + def.getType());
				}
				beans.put(id, new BeanAdapter(id, def.getType(), instance));
				return (T) instance;
			} else {
				return (T) initializeInstance(def, adapter.getInstance());
			}
		} else { // Prototype, always new instance.
			Object instance = newInstance(def);
			if (instance == null) {
				throw new ClassUtilsException("id=" + id + " instance is null. class=" + def.getType());
			}
			return (T) instance;
		}
	}

	public synchronized void removeBean(String id) {
		defines.remove(id);
		beans.remove(id);
	}

	public synchronized <T> void removeBeans(Class<T> type) {
		String[] keys = StringUtils.toStringArray(defines.keySet());
		for (String key : keys) {
			BeanDefine def = defines.get(key);
			if (def.getType().equals(type)) {
				defines.remove(key);
				beans.remove(key);
			}
		}
	}

	/* === private methods. === */

	/**
	 * Merge aliases and id.
	 */
	protected void initDefines() {
		String[] keys = StringUtils.toStringArray(defines.keySet());
		for (String key : keys) {
			String[] aliases = defines.get(key).getAliases();
			if (aliases != null && aliases.length > 0) {
				for (String alias : aliases) {
					if (defines.containsKey(alias) == false) {
						defines.put(alias, defines.get(key));
					}
				}
			}
		}
	}

	/**
	 * If BeanDefine#useInitMethod() returns true, then execute initialize
	 * method.
	 * 
	 * @param def
	 * @param instance
	 * @return instance
	 */
	protected <T> T initializeInstance(BeanDefine def, T instance) {
		if (def.useInitMethod()) {
			ClassUtils.invoke(def.getInitMethod(), instance, (Object[]) null);
		}
		return instance;
	}

	/**
	 * New instance by BeanDefine. ( Constructor Injection / FactoryMethod /
	 * Setter Injection )
	 * 
	 * @param def
	 *            BeanDefine
	 * @return new instance
	 */
	@SuppressWarnings("unchecked")
	protected <T extends Object> T newInstance(BeanDefine def) {
		T instance = null;
		// for Factory Method.
		if (def.getFactoryMethod() != null) {
			instance = (T) ClassUtils.invoke(def.getFactoryMethod(), (Object) null, (Object[]) null);
			if (instance != null && (instance.getClass() != def.getType())) {
				// Convert the Factory Class -> Instance Class.
				def.setType(instance.getClass());
			}
		}
		// for Constructor Injection.
		if (def.getConstructorArgs().size() > 0) {
			// use constructor injection.
			instance = (T) newInstanceForConstructorInjection(def);
		} else {
			// use default constructor.
			instance = (T) initializeInstance(def, ClassUtils.newInstance(def.getType()));
		}
		// use setter injection.
		for (BeanDefineParam prop : def.getPropertyList()) {
			if (prop == null)
				continue;
			if (prop.isRef()) {
				Object param = getBean(prop.getRefId(), prop.getParamType());
				if (param == null) {
					throw new ClassUtilsException("ref=" + prop.getRefId() + " param is null. [" + prop.getRefId()
							+ ":" + prop.getParamType() + "] in " + instance);
				}
				instance = setterInjection(instance, prop, def, param);
			} else {
				instance = setterInjectionByBasicType(instance, prop, def, prop.getValue());
			}
		}
		return instance;
	}

	/**
	 * New instance by BeanDefine for Constructor Injection.
	 * 
	 * @param def
	 * @return new instance
	 */
	protected Object newInstanceForConstructorInjection(BeanDefine def) {
		List<BeanConstructorParam> argsDef = def.getConstructorArgs();
		Class<?>[] argsTypes = new Class<?>[argsDef.size()];
		Object[] args = new Object[argsDef.size()];
		boolean isAutoTypes = false;
		for (int i = 0; i < argsDef.size(); i++) {
			if (argsDef.get(i).isRef()) {
				argsTypes[i] = defines.get(argsDef.get(i).getRefId()).getType();
				args[i] = getBean(argsDef.get(i).getRefId(), argsTypes[i]);
			} else {
				args[i] = argsDef.get(i).getValue();
				// args type is not defined.
				if (argsDef.get(i).getType() == null) {
					isAutoTypes = true;
				} else {
					argsTypes[i] = loadClass(argsDef.get(i).getType());
				}
			}
		}
		// execute constructor injection.
		if (isAutoTypes) {
			return initializeInstance(def, ClassUtils.newInstance(def.getType(), args));
		} else {
			return initializeInstance(def, ClassUtils.newInstance(def.getType(), argsTypes, args));
		}
	}

	protected Class<?> loadClass(String name) {
		return ClassUtils.forName(name, getClassLoader());
	}

	/**
	 * Setter injection method for parameter type equals Object (ref)
	 * 
	 * @param instance
	 * @param prop
	 * @param def
	 * @param param
	 * @return instance
	 */
	protected <T> T setterInjection(T instance, BeanDefineParam prop, BeanDefine def, Object param) {
		Method method = prop.getMethod();
		if (method == null) {
			Class<?> p = param != null ? param.getClass() : null;
			method = ClassUtils.searchMethod(def.getType(), prop.getName(), p);
			if (method == null) {
				throw new ClassUtilsException("id=" + def.getId() + " method or param is null. [" + prop.getName()
						+ ":" + param + "] in " + instance);
			}
		}
		ClassUtils.invoke(method, instance, param);
		// Regist method cache.
		prop.setMethod(method);
		return instance;
	}

	/**
	 * Setter injection method for parameter type is primitive or String. TODO:
	 * overload, multiple parameters.
	 * 
	 * @param instance
	 * @param prop
	 * @param def
	 * @param param
	 * @return instance
	 */
	@SuppressWarnings("unchecked")
	protected <T extends Object> T setterInjectionByBasicType(T instance, BeanDefineParam prop, BeanDefine def,
			String param) {
		Method method = prop.getMethod();
		if (method != null) {
			StringValueConverter<?> converter = prop.getStringValueConverter();
			if (converter != null) {
				ClassUtils.invoke(method, instance, converter.convert(param));
			} else {
				ClassUtils.invoke(method, instance, param);
			}
			return instance;
		} else {
			PropertyValueHandler handler = new PropertyValueHandler(def.getType(), prop);
			return (T) handler.resolvParameterValue(instance);
		}
	}
}
