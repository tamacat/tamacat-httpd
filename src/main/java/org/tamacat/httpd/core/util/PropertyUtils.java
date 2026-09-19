/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.util;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Set;

/**
 * <p>
 * The Utility of Properties.<br>
 * wheen file is not found then throws {@link ResourceNotFoundException}.
 */
public abstract class PropertyUtils {

	public static Properties getProperties(String path) {
		return getProperties(path, ClassUtils.getDefaultClassLoader());
	}

	/**
	 * <p>
	 * Get the properties file, when file not found then throws the
	 * {@link ResourceNotFoundException}.
	 * 
	 * @param path
	 *            File path in the CLASSPATH
	 * @return Properties file.
	 * @since 0.7
	 */
	public static Properties getProperties(String path, ClassLoader loader) {
		Properties props = new Properties();
		InputStream in = null;
		try {
			in = IOUtils.getInputStream(path, loader);
			props.load(in);
		} catch (Exception e) {
			throw new ResourceNotFoundException(e);
		} finally {
			IOUtils.close(in);
		}
		if (disableResolveSystemProperty(props)) {
			return props;
		} else {
			return SystemPropertyUtils.resolvePlaceholders(props);
		}
	}

	/**
	 * <p>
	 * Get the properties file, when file not found then throws the
	 * {@link ResourceNotFoundException}.
	 * 
	 * @param path
	 *            URL path
	 * @return Properties file.
	 * @since 1.0
	 */
	public static Properties getProperties(URL path) {
		Properties props = new Properties();
		InputStream in = null;
		try {
			in = path.openStream();
			props.load(in);
		} catch (Exception e) {
			throw new ResourceNotFoundException(e);
		} finally {
			IOUtils.close(in);
		}
		if (disableResolveSystemProperty(props)) {
			return props;
		} else {
			return SystemPropertyUtils.resolvePlaceholders(props);
		}
	}

	public static Properties marge(String defaultFile, String addFile) {
		return marge(defaultFile, addFile, ClassUtils.getDefaultClassLoader());
	}

	/**
	 * <p>
	 * Marge the two property files.<br>
	 * This method is not throws the {@link ResourceNotFoundException}. When
	 * file not found, then always returns the empty properties.
	 * 
	 * @param defaultFile
	 *            default properties.
	 * @param addFile
	 *            add or override the properties.
	 * @return marged properties
	 * @since 0.7
	 */
	public static Properties marge(String defaultFile, String addFile, ClassLoader loader) {
		Properties props = null;
		// Get the default properties.
		try {
			props = getProperties(defaultFile, loader);
		} catch (Exception e) {
			props = new Properties(); // create empty properties.
		}
		// Override or add the properties.
		try {
			Properties add = getProperties(addFile, loader);
			Set<Object> keys = add.keySet();
			for (Object key : keys) {
				props.setProperty((String) key, add.getProperty((String) key));
			}
		} catch (Exception e) {
			// none.
		}
		return props;
	}
	
	/**
	 * Disabled resolve System Property
	 * ex) PropertyUtils.disableResolveSystemProperty=true
	 */
	static boolean disableResolveSystemProperty(Properties props) {
		if (props != null && Boolean.parseBoolean(props.getProperty("PropertyUtils.disableResolveSystemProperty", "false"))) {
			return true;
		} else {
			return false;
		}
	}
}
