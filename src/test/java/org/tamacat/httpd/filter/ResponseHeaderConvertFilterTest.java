package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;

public class ResponseHeaderConvertFilterTest {

	ResponseHeaderConvertFilter filter;
	
	@BeforeEach
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		filter = new ResponseHeaderConvertFilter();
		filter.init(serviceUrl);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testConvertHeaderValue() {
		filter.setHeaderNames("Location");
		filter.setConvertValues("http://localhost/=https://example.com/");
		
		assertEquals("https://example.com/test/index.html", filter.convertHeaderValue("http://localhost/test/index.html"));
	}

}
