/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.util.RequestUtils;

public class VelocityActionFilterTest {

	VelocityActionFilter filter;
	
	@Before
	public void setUp() throws Exception {
		filter = new VelocityActionFilter();
		filter.setBase("org.tamacat.httpd.action");
		filter.setSuffix("Action");
		
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		filter.init(serviceUrl);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoFilter() {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/main?a=Default&p=top");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();
		RequestUtils.parseParameters(request, context, "UTF-8");
		assertEquals("Default", RequestUtils.getParameter(context, "a"));
		filter.doFilter(request, response, context);
		assertNotNull(filter.getServiceUrl());
	}

}
