/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.config;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({System.class, ServiceConfigParser.class})
public class ServiceConfigParserTest {

	ServiceConfigParser parser;

	@Before
	public void setUp() throws Exception {
		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setParam("url-config.file", "url-config.xml");
		parser = new ServiceConfigParser(serverConfig);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetServiceConfig() {
		HostServiceConfig config = parser.getConfig();
		ServiceConfig serviceConfig = config.getDefaultServiceConfig();
		List<ServiceUrl> list = serviceConfig.getServiceUrlList();
		Assert.assertTrue(list.size() > 0);
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
			Assert.fail("type=\"lb\" was removed in 1.6.0 and must no longer parse.");
		} catch (RuntimeException e) {
			Assert.assertTrue(
				"expected the wrapped cause to be IllegalArgumentException but was " + e.getCause(),
				e.getCause() instanceof IllegalArgumentException);
		}
	}
	
	@Test
	public void testreplaceEnvironmentVariable() {
		PowerMockito.mockStatic(System.class);
		PowerMockito.when(System.getenv("LOCAL_SERVER")).thenReturn("localhost");
		PowerMockito.when(System.getenv("LOCAL_PORT")).thenReturn("8080");
		assertEquals(
			"http://localhost:8080/examples/", 
			ServiceConfigParser.replaceEnvironmentVariable("http://${LOCAL_SERVER}:${LOCAL_PORT}/examples/")
		);
	}
}
