package org.tamacat.httpd.filter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.junit.jupiter.api.Test;

public class LocationRedirectResponseInterceptorTest {

	@Test
	public void testProcess() throws HttpException, IOException {
		ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
		response.addHeader("Location", "http://www.example.com/ridirect");
		HttpContext context = new HttpCoreContext();
		LocationRedirectResponseInterceptor interceptor = new LocationRedirectResponseInterceptor();
		interceptor.process(response, response.getEntity(), context);
		assertEquals("http://www.example.com/ridirect", context.getAttribute(LocationRedirectResponseInterceptor.LAST_REDIRECT_URL));
	}

	@Test
	public void testCheckRedirect() {
		ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
		HttpContext context = new HttpCoreContext();
		context.setAttribute(LocationRedirectResponseInterceptor.LAST_REDIRECT_URL, "http://www.example.com/ridirect");
		LocationRedirectResponseInterceptor.checkRedirect(response, context);
		assertEquals("http://www.example.com/ridirect", response.getFirstHeader("Location").getValue());
	}
}
