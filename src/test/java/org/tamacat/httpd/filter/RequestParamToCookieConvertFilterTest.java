package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;

public class RequestParamToCookieConvertFilterTest {

	ServiceUrl serviceUrl;
	
	@Before
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		serviceUrl = new ServiceUrl(config);
	}
	
	@Test
	public void testSetMethod() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setMethod("GET");
		filter.setRequestParamKey("_test_");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/?_test_=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals("Test=true;Path=/", response.getFirstHeader("Set-Cookie").getValue());
	}

	@Test
	public void testSetMethodPost() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setMethod("GET");
		filter.setRequestParamKey("_test_");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("POST", "/?_test_=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals(null, response.getFirstHeader("Set-Cookie"));
	}
	
	@Test
	public void testSetRequestPath() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setRequestParamKey("_test_");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.setRequestPath("/test/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/aaa/test/bbb?_test_=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals("Test=true;Path=/", response.getFirstHeader("Set-Cookie").getValue());
	}
	
	@Test
	public void testSetRequestPathIgnore() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setRequestParamKey("_test_");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.setRequestPath("/test123/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/aaa/test/bbb?_test_=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals(null, response.getFirstHeader("Set-Cookie"));
	}

	@Test
	public void testSetRequestParamKey() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setRequestParamKey("__test__");
		filter.setCookieKey("Test");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/?__test__=OK");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals("Test=OK", response.getFirstHeader("Set-Cookie").getValue());
	}
	
	@Test
	public void testSetRequestParamKeyNone() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setRequestParamKey("");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/?__test__=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals(null, response.getFirstHeader("Set-Cookie"));
	}
	
	@Test
	public void testSetRequestParamKeyFalse() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		filter.setRequestParamKey("_test_");
		filter.setCookieKey("Test");
		filter.setCookieAttributes("Path=/");
		filter.init(serviceUrl);
		
		HttpRequest request = createHttpRequest("GET", "/?__test__=true");
		HttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		filter.doFilter(request, response, context);
		filter.afterResponse(request, response, context);
		
		assertEquals(null, response.getFirstHeader("Set-Cookie"));
	}
	
	public void testValidateValue() {
		RequestParamToCookieConvertFilter filter = new RequestParamToCookieConvertFilter();
		
		assertEquals(true, filter.validateValue("true"));
		assertEquals(true, filter.validateValue("123"));
		assertEquals(true, filter.validateValue("123OK"));
		
		assertEquals(false, filter.validateValue(""));
		assertEquals(false, filter.validateValue("_NG"));
		assertEquals(false, filter.validateValue("¥r"));
		assertEquals(false, filter.validateValue("¥n"));
		assertEquals(false, filter.validateValue("¥s"));
		assertEquals(false, filter.validateValue("¥t"));
	}
	
	public static HttpRequest createHttpRequest(String method, String uri) {
		if ("POST".equalsIgnoreCase(method)) {
			return new BasicHttpEntityEnclosingRequest(method, uri);
		} else {
			return new BasicHttpRequest(method, uri);
		}
	}

	public static HttpResponse createHttpResponse(int status, String reason) {
		return new BasicHttpResponse(new ProtocolVersion("HTTP",1,1), status, reason);
	}

	public static HttpResponse createHttpResponse(ProtocolVersion ver, int status, String reason) {
		return new BasicHttpResponse(ver, status, reason);
	}

	public static HttpContext createHttpContext() {
		return new BasicHttpContext();
	}

}
