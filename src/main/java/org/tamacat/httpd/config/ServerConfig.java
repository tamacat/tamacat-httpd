/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import org.tamacat.util.PropertyUtils;
import org.tamacat.util.StringUtils;

/**
 * <p>Server configurations.<br>
 * Default setting file is {@code server.properties} in CLASSPATH.
 */
public class ServerConfig {
	
	/** 
	 * Default keys of ServerConfig
	 * @since 1.5
	 */
	private String[] serverConfigKeys = {
		"ServerName", "Port",
		"https", "https.keyStoreFile", "https.keyStoreType", "https.protocol", 
		"https.support-protocols", "https.defaultAlias", "https.support-cipherSuites",
		"MaxServerThreads", "ServerSocketTimeout", "ConnectionTimeout", "ServerSocketBufferSize",
		"KeepAliveTimeout", "KeepAliveRequests",
		"WorkerThreadName",
		"BackEndSocketTimeout", "BackEndConnectionTimeout", "BackEndSocketBufferSize",
		"url-config.file", "components.file"		
	};
	
	private Properties props;

	/**
	 * <p>Use the "server.properties" file in CLASSPATH.
	 */
	public ServerConfig() {
		this(PropertyUtils.getProperties("server.properties"));
	}

	/**
	 * <p>Use the setting values from {@link Properties} object.
	 * @param props
	 */
	public ServerConfig(Properties props) {
		this.props = props;
		
		//Override Properties from System Environment Variables.
		if (useSystemEnvironmentVariables()) {
			loadServerConfigFromSystemEnvironment(serverConfigKeys);
		}
	}

	/**
	 * <p>Override the ServerConfig values from System environment variables.
	 * @param serverConfigKeys (comma separated values)
	 * @since 1.5
	 */
	public void setServerConfigKeys(String serverConfigKeys) {
		if (StringUtils.isEmpty(serverConfigKeys)) {
			loadServerConfigFromSystemEnvironment(StringUtils.split(serverConfigKeys, ","));
		}
	}
	
	/**
	 * <p>Override the ServerConfig values from System environment variables.
	 * @since 1.5
	 */
	protected void loadServerConfigFromSystemEnvironment(String[] serverConfigKeys) {
		Arrays.asList(serverConfigKeys).forEach(key-> {
			Optional<String> val = Optional.ofNullable(System.getenv(key));
			val.ifPresent(value -> setParam(key, value));
		});
	}

	/**
	 * <p>Returns the Listen port.
	 * @return Get the parameter value key of "Port",
	 * if value is null then returns {@code 8080}.
	 */
	public int getPort() {
		return getParam("Port", 8080);
	}

	/**
	 * <p>If using the System Environment Variables, default returns true.
	 * @since 1.5
	 */
	public boolean useSystemEnvironmentVariables() {
		return getParam("UseSystemEnvironmentVariables", "true").equalsIgnoreCase("true");
	}
	
	/**
	 * <p>Returns the maximum server threads.
	 * @return Get the parameter value key of "MaxServerThreads",
	 * if value is null then returns {@code 0}.
	 */
	public int getMaxThreads() {
		return getParam("MaxServerThreads", 0);
	}

	/**
	 * <p>Returns the Socket timeout (ms).
	 * @return Get the parameter value key of "ServerSocketTimeout",
	 * if value is null then returns {@code 15000}.
	 */
	public int getSocketTimeout() {
		return getParam("ServerSocketTimeout", 15000);
	}

	/**
	 * <p>Returns the Connection timeout (ms).
	 * @return Get the parameter value key of "ConnectionTimeout",
	 * if value is null then returns {@code 15000}.
	 */
	public int getConnectionTimeout() {
		return getParam("ConnectionTimeout", 15000);
	}

	/**
	 * <p>Returns the Socket buffer size.
	 * @return Get the parameter value key of "ServerSocketBufferSize",
	 * if value is null then returns {@code 8192}.
	 */
	public int getSocketBufferSize() {
		return getParam("ServerSocketBufferSize", 8192);
	}

	/**
	 * <p>If using the https, returns true.
	 * @return Get the parameter value key of "https",
	 * if value is null then returns {@code false}.
	 */
	public boolean useHttps() {
		return getParam("https", "false").equalsIgnoreCase("true");
	}

	/**
	 * <p>If using the https client authentication, returns true.
	 * @return Get the parameter value key of "https.clientAuth",
	 * if value is null then returns {@code false}.
	 */
	public boolean useClientAuth() {
		return getParam("https.clientAuth", "false").equalsIgnoreCase("true");
	}

	/**
	 * <p>Returns the HTTPS support SSL/TLS protocols.
	 * (Comma separate values to String[])
	 * ex) https.support-protocols=TLSv1,TLSv1.1,TLSv1.2
	 * @return ex) ["TLSv1","TLSv1.1","TLSv1.2"]
	 * @since 1.2
	 */
	public String[] getHttpsSupportProtocols() {
		return StringUtils.split(getParam("https.support-protocols"), ",");
	}
	
	/**
	 * <p>Retuens the SSL/TLS cipher suites.
	 * (Comma separate values to String[])
	 * ex) https.support-cipherSuites=TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
	 * @since 1.4
	 */
	public String[] getSupportCipherSuites() {
		return StringUtils.split(getParam("https.support-cipherSuites"), ",");
	}
	
	/**
	 * <p>Returns the value of parameter
	 * @param name parameter name.
	 * @return parameter value.
	 */
	public String getParam(String name) {
		return props.getProperty(name);
	}

	/**
	 * <p>Set the parameter.
	 * @param name
	 * @param value
	 */
	public void setParam(String name, String value) {
		props.setProperty(name, value);
	}

	/**
	 * Returns the parameter, if value is null then returns {@code defaultValue}.
	 * @param <T>
	 * @param name
	 * @param defaultValue
	 * @return if value is null then returns {@code defaultValue}.
	 */
	@SuppressWarnings("unchecked")
	public <T> T getParam(String name, T defaultValue) {
		String value = props.getProperty(name);
		if (defaultValue == null) return (T) value;
		return StringUtils.parse(value, defaultValue);
	}
}
