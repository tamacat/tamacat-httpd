/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class ServiceConfigParserTest {

	/**
	 * Environment variable names that {@code ServiceConfigParser.REPLACE_HOLDER_PATTERN}
	 * is able to recognise inside a {@code ${...}} placeholder.
	 */
	static final Pattern USABLE_ENV_NAME = Pattern.compile("[a-zA-Z0-9_\\-]+");

	ServiceConfigParser parser;

	@Before
	public void setUp() throws Exception {
		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setParam("url-config.file", "url-config.xml");
		parser = new ServiceConfigParser(serverConfig);
	}

	@Test
	public void testGetServiceConfig() {
		HostServiceConfig config = parser.getConfig();
		ServiceConfig serviceConfig = config.getDefaultServiceConfig();
		List<ServiceUrl> list = serviceConfig.getServiceUrlList();
		Assert.assertTrue(list.size() > 0);
	}

	/**
	 * Placeholders naming a defined environment variable are replaced by its value.
	 * <p>
	 * The original test mocked {@code System.getenv} with PowerMock, which no longer
	 * works on a modular JDK. Java offers no supported way to inject an environment
	 * variable into the running process, so the test drives the same code path with
	 * variables that the process actually has.
	 */
	@Test
	public void testReplaceEnvironmentVariable() {
		List<Map.Entry<String, String>> env = new ArrayList<>(usableEnvironment().entrySet());
		Assume.assumeTrue("needs at least two usable environment variables", env.size() >= 2);
		Map.Entry<String, String> server = env.get(0);
		Map.Entry<String, String> port = env.get(1);

		String actual = ServiceConfigParser.replaceEnvironmentVariable(
			"http://${" + server.getKey() + "}:${" + port.getKey() + "}/examples/");

		assertEquals(
			"http://" + server.getValue() + ":" + port.getValue() + "/examples/",
			actual);
	}

	/** A placeholder naming an undefined variable is left untouched. */
	@Test
	public void testReplaceEnvironmentVariableUndefined() {
		String value = "http://${TAMACAT_TEST_UNDEFINED_VARIABLE}:8080/examples/";
		Assume.assumeTrue(System.getenv("TAMACAT_TEST_UNDEFINED_VARIABLE") == null);
		assertEquals(value, ServiceConfigParser.replaceEnvironmentVariable(value));
	}

	/** A value without any placeholder is returned unchanged. */
	@Test
	public void testReplaceEnvironmentVariableWithoutPlaceholder() {
		assertEquals(
			"http://localhost:8080/examples/",
			ServiceConfigParser.replaceEnvironmentVariable("http://localhost:8080/examples/"));
	}

	/** Null and empty input are returned as is. */
	@Test
	public void testReplaceEnvironmentVariableNullOrEmpty() {
		assertEquals(null, ServiceConfigParser.replaceEnvironmentVariable(null));
		assertEquals("", ServiceConfigParser.replaceEnvironmentVariable(""));
	}

	/**
	 * Environment entries whose name the placeholder pattern can express and whose
	 * value cannot itself be mistaken for a placeholder.
	 */
	static Map<String, String> usableEnvironment() {
		Map<String, String> usable = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
			String name = entry.getKey();
			String value = entry.getValue();
			if (name == null || value == null || value.length() == 0) {
				continue;
			}
			if (!USABLE_ENV_NAME.matcher(name).matches() || value.contains("${")) {
				continue;
			}
			usable.put(name, value);
		}
		return usable;
	}
}
