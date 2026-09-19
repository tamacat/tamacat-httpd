package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import java.io.IOException;

import org.tamacat.httpcore4.HttpException;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.message.BasicHttpResponse;
import org.tamacat.httpcore4.message.BasicStatusLine;
import org.tamacat.httpcore4.protocol.BasicHttpContext;
import org.tamacat.httpcore4.protocol.HttpContext;

import org.junit.Test;

public class LocationRedirectResponseInterceptorTest {

	@Test
	public void testProcess() throws HttpException, IOException {
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
		response.addHeader("Location", "http://www.example.com/ridirect");
		HttpContext context = new BasicHttpContext();
		LocationRedirectResponseInterceptor interceptor = new LocationRedirectResponseInterceptor();
		interceptor.process(response, context);
		assertEquals("http://www.example.com/ridirect", context.getAttribute(LocationRedirectResponseInterceptor.LAST_REDIRECT_URL));
	}

	@Test
	public void testCheckRedirect() {
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
		HttpContext context = new BasicHttpContext();
		context.setAttribute(LocationRedirectResponseInterceptor.LAST_REDIRECT_URL, "http://www.example.com/ridirect");
		LocationRedirectResponseInterceptor.checkRedirect(response, context);
		assertEquals("http://www.example.com/ridirect", response.getFirstHeader("Location").getValue());
	}
}
