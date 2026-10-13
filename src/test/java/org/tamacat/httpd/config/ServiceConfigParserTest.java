/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServiceConfigParserTest {

	/**
	 * Environment variable names that {@code ServiceConfigParser.REPLACE_HOLDER_PATTERN}
	 * is able to recognise inside a {@code ${...}} placeholder.
	 */
	static final Pattern USABLE_ENV_NAME = Pattern.compile("[a-zA-Z0-9_\\-]+");

	ServiceConfigParser parser;

	@BeforeEach
	public void setUp() throws Exception {
		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setParam("url-config.file", "url-config.xml");
		parser = new ServiceConfigParser(serverConfig);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetServiceConfig() {
		HostServiceConfig config = parser.getConfig();
		ServiceConfig serviceConfig = config.getDefaultServiceConfig();
		List<ServiceUrl> list = serviceConfig.getServiceUrlList();
		Assertions.assertTrue(list.size() > 0);
	}

	/**
	 * New in 1.6.0 (Step 6b.3): a {@code url-config.xml} entry using the
	 * removed {@code type="lb"} (load balancing, config.lb package, [BR-9])
	 * must fail clearly during parsing — not with an unhelpful
	 * {@code ClassNotFoundException}/{@code NoClassDefFoundError} from a
	 * silently-broken reference to a deleted class. {@code getConfig()}'s
	 * existing convention wraps any parse failure in a {@code RuntimeException}
	 * (see catch block above); the direct cause must be the same
	 * {@code IllegalArgumentException} {@code ServiceType.find()} already
	 * throws for any other unrecognized type string (Step 6b.2).
	 */
	@Test
	public void testGetServiceConfigLbTypeFailsClearly() {
		ServerConfig lbConfig = new ServerConfig();
		lbConfig.setParam("url-config.file", "url-config-lb.xml");
		ServiceConfigParser lbParser = new ServiceConfigParser(lbConfig);
		try {
			lbParser.getConfig();
			Assertions.fail("type=\"lb\" was removed in 1.6.0 and must no longer parse.");
		} catch (RuntimeException e) {
			Assertions.assertTrue(
				e.getCause() instanceof IllegalArgumentException,
				"expected the wrapped cause to be IllegalArgumentException but was " + e.getCause());
		}
	}
	
	/**
	 * Placeholders naming a defined environment variable are replaced by its value.
	 * <p>
	 * This test used to replace {@code System.getenv} with a static mock, which does
	 * not work on a modular JDK. Java offers no supported way to inject an environment
	 * variable into the running process, so the test drives the same code path with
	 * two variables that the process actually has, and is skipped where the process
	 * has fewer than two usable ones.
	 */
	@Test
	public void testreplaceEnvironmentVariable() {
		List<Map.Entry<String, String>> env = new ArrayList<>(usableEnvironment().entrySet());
		Assumptions.assumeTrue(env.size() >= 2, "needs at least two usable environment variables");
		Map.Entry<String, String> server = env.get(0);
		Map.Entry<String, String> port = env.get(1);

		assertEquals(
			"http://" + server.getValue() + ":" + port.getValue() + "/examples/",
			ServiceConfigParser.replaceEnvironmentVariable(
				"http://${" + server.getKey() + "}:${" + port.getKey() + "}/examples/")
		);
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
