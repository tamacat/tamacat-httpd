package org.tamacat.httpd.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;

public class DefaultHttpHandlerFactoryTest {

	ServerConfig serverConfig;
	ServiceConfig serviceConfig;
	DefaultHttpHandlerFactory factory;
	ServiceUrl serviceUrl;
	
	@BeforeEach
	public void setUp() throws Exception {
		serverConfig = new ServerConfig();
		serviceConfig = new ServiceConfig();
		serviceUrl = new ServiceUrl(serverConfig);
		serviceUrl.setHandlerName("VelocityHandler");
		serviceUrl.setPath("/");
		serviceUrl.setType(ServiceType.NORMAL);
		serviceConfig.addServiceUrl(serviceUrl);
		
		factory = new DefaultHttpHandlerFactory("components.xml");
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

//	@Test
//	public void testGetHttpHandler() {
//		HttpHandler handler = factory.getHttpHandler(serviceUrl);
//		assertEquals(false, ((VelocityHttpHandler)handler).listings);
//		
//		handler = factory.getHttpHandler(serviceUrl);
//		assertEquals(false, ((VelocityHttpHandler)handler).listings);
//	}
}
