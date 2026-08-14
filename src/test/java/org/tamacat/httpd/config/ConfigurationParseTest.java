/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.handler.DefaultHttpHandlerFactory;
import org.tamacat.httpd.handler.HttpHandler;
import org.tamacat.httpd.handler.HttpHandlerFactory;
import org.tamacat.httpd.core.util.PropertyUtils;

/**
 * <p>Smoke tests for configuration parsing (FR-7.1, FR-7.2 / REL-3.3).
 *
 * <p>The migration removed {@code HttpProxyConfig} and {@code PerformanceCounterFilter}
 * and, with them, 24 lines of {@code components.xml} (code-generation Step 6.5). A
 * dangling bean or property in that file does not fail the build - it fails at runtime,
 * during config parse, when the DI container cannot resolve it. These tests load the
 * real configuration files and drive the real DI resolution so that such a failure is
 * caught by {@code mvn test}.
 */
public class ConfigurationParseTest {

	/** Setters removed in 2.0 whose configuration entries had to go with them. */
	static final List<String> REMOVED_SETTERS =
		Arrays.asList("httpProxyConfig", "proxyHost", "proxyPort", "nonProxyHosts");

	ServerConfig serverConfig;

	@Before
	public void setUp() throws Exception {
		serverConfig = new ServerConfig();
		serverConfig.setParam("url-config.file", "url-config.xml");
		serverConfig.setParam("components.file", "components.xml");
	}

	static String readResource(String name) throws Exception {
		InputStream in = ConfigurationParseTest.class.getClassLoader().getResourceAsStream(name);
		assertNotNull(name + " must be on the test classpath", in);
		try {
			StringBuilder buf = new StringBuilder();
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
			char[] chunk = new char[4096];
			int len;
			while ((len = reader.read(chunk)) > 0) {
				buf.append(chunk, 0, len);
			}
			return buf.toString();
		} finally {
			in.close();
		}
	}

	/** FR-7.1 - server.properties loads and the keys the server reads are present. */
	@Test
	public void testServerPropertiesLoads() throws Exception {
		Properties props = PropertyUtils.getProperties("server.properties", getClass().getClassLoader());
		assertNotNull(props);
		assertFalse(props.isEmpty());

		ServerConfig config = new ServerConfig(props);
		assertEquals("tamacat-httpd", config.getParam("ServerName"));
		assertEquals("url-config.xml", config.getParam("url-config.file"));
		assertEquals("components.xml", config.getParam("components.file"));
		assertEquals(Integer.valueOf(60000), config.getParam("BackEndSocketTimeout", Integer.valueOf(0)));
	}

	/** FR-7.1 - url-config.xml parses into a non-empty set of services. */
	@Test
	public void testUrlConfigParses() {
		HostServiceConfig hostConfig = new ServiceConfigParser(serverConfig).getConfig();
		assertNotNull(hostConfig);

		List<ServiceUrl> urls = hostConfig.getDefaultServiceConfig().getServiceUrlList();
		assertFalse("url-config.xml must yield at least one service url", urls.isEmpty());

		for (ServiceUrl url : urls) {
			assertNotNull("path is required: " + url, url.getPath());
			assertNotNull("handler is required for " + url.getPath(), url.getHandlerName());
			assertNotNull("type is required for " + url.getPath(), url.getType());
		}
	}

	/**
	 * FR-7.1 - the real DI resolution. Every {@code <url>} in url-config.xml must
	 * resolve, through components.xml, to a live {@link HttpHandler}. This is the
	 * assertion that would fail had Step 6.5's XML surgery left a dangling reference.
	 */
	@Test
	public void testEveryServiceUrlResolvesAHandler() {
		HttpHandlerFactory factory = new DefaultHttpHandlerFactory(
			"components.xml", getClass().getClassLoader());

		HostServiceConfig hostConfig = new ServiceConfigParser(serverConfig).getConfig();
		List<String> resolved = new ArrayList<>();
		for (String host : hostConfig.getHosts()) {
			for (ServiceUrl url : hostConfig.getServiceConfig(host).getServiceUrlList()) {
				HttpHandler handler = factory.getHttpHandler(url);
				assertNotNull("DI could not resolve handler '" + url.getHandlerName()
					+ "' for path " + url.getPath(), handler);
				resolved.add(url.getPath());
			}
		}
		assertFalse("no service url was resolved at all", resolved.isEmpty());
	}

	/**
	 * FR-7.2 - components.xml must not mention any setter removed in 2.0. Parsing does
	 * not fail on an unknown property name, so the file is checked as text.
	 */
	@Test
	public void testComponentsXmlHasNoRemovedSetters() throws Exception {
		String xml = readResource("components.xml");
		for (String setter : REMOVED_SETTERS) {
			assertFalse("components.xml still refers to the removed setter '" + setter + "'",
				xml.contains(setter));
		}
		//the beans those setters belonged to are gone too
		assertFalse(xml.contains("HttpProxyConfig"));
		assertFalse(xml.contains("PerformanceCounterFilter"));
	}

	/** The same check for the other components.xml on the test classpath. */
	@Test
	public void testTomcatComponentsXmlHasNoRemovedSetters() throws Exception {
		String xml = readResource("tomcat/components.xml");
		for (String setter : REMOVED_SETTERS) {
			assertFalse("tomcat/components.xml still refers to '" + setter + "'",
				xml.contains(setter));
		}
	}

	/**
	 * FR-7.2 - and the setters really are gone from the classes, so that the text check
	 * above cannot pass while the API silently survives.
	 */
	@Test
	public void testRemovedSettersAreAbsentFromTheHandlerClasses() throws Exception {
		Class<?>[] classes = {
			org.tamacat.httpd.handler.ReverseProxyHandler.class,
			org.tamacat.httpd.handler.AbstractHttpHandler.class,
			org.tamacat.httpd.config.ServiceUrl.class,
			org.tamacat.httpd.config.ServerConfig.class,
		};
		for (Class<?> c : classes) {
			for (Method m : c.getMethods()) {
				for (String setter : REMOVED_SETTERS) {
					String name = "set" + Character.toUpperCase(setter.charAt(0)) + setter.substring(1);
					assertFalse(c.getName() + " still declares " + name, m.getName().equals(name));
				}
			}
		}
	}
}
