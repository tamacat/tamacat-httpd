/*
 * Copyright (c) 2010, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.util.RequestUtils;

public class VelocityActionFilterTest {

	VelocityActionFilter filter;
	
	@BeforeEach
	public void setUp() throws Exception {
		filter = new VelocityActionFilter();
		filter.setBase("org.tamacat.httpd.action");
		filter.setSuffix("Action");
		
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		filter.init(serviceUrl);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoFilter() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/main?a=Default&p=top");
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = HttpObjectFactory.createHttpContext();
		RequestUtils.parseParameters(request, context, "UTF-8");
		assertEquals("Default", RequestUtils.getParameter(context, "a"));
		filter.doFilter(request, response, context);
		assertNotNull(filter.getServiceUrl());
	}

}
