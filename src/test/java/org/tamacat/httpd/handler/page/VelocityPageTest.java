package org.tamacat.httpd.handler.page;

import static org.junit.Assert.*;

import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.Test;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.util.PropertyUtils;

public class VelocityPageTest {

	@Test
	public void testGetTemplate() throws Exception {
		Properties props = PropertyUtils.getProperties("velocity.properties");
		VelocityPage page = new VelocityPage(props);
		page.init("./src/test/resources/htdocs/root");
		//assertNotNull(page.getTemplate("/index.vm"));
	}

	@Test
	public void testGetPage() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test.html");
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");

		Properties props = PropertyUtils.getProperties("velocity.properties");
		VelocityPage page = new VelocityPage(props);
		page.init("./src/test/resources/htdocs/root");
		//String html = page.getPage(request, response, "/index");
		//assertNotNull(html);

		try {
			page.getPage(request, response, "/xxxxxxxxxx");
			fail();
		} catch (NotFoundException e) {
			//OK
		}
	}
}
