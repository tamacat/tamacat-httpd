package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class HostRequestHandlerMapperTest {

	HostRequestHandlerMapper mapper;

	@BeforeEach
	public void setUp() throws Exception {
		mapper = new HostRequestHandlerMapper();

		ServerConfig config = new ServerConfig();
		String componentsXML = "components.xml";
		mapper.create(config, componentsXML);

	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testLookup() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		HttpContext context = HttpObjectFactory.createHttpContext();
		assertEquals(ThymeleafHttpHandler.class, mapper.lookup(request, context).getClass());
	}

	@Test
	public void testLookupException() {
		HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "");
		HttpContext context = HttpObjectFactory.createHttpContext();
		assertNull(mapper.lookup(request, context));
	}
}
