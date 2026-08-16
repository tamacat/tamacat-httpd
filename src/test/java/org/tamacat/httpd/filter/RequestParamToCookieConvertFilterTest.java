package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/?_test_=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("POST", "/?_test_=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/aaa/test/bbb?_test_=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/aaa/test/bbb?_test_=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/?__test__=OK");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/?__test__=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
		
		ClassicHttpRequest request = createHttpRequest("GET", "/?__test__=true");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
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
	
	public static ClassicHttpRequest createHttpRequest(String method, String uri) {
		if ("POST".equalsIgnoreCase(method)) {
			return new BasicClassicHttpRequest(method, uri);
		} else {
			return new BasicClassicHttpRequest(method, uri);
		}
	}

	public static ClassicHttpResponse createHttpResponse(int status, String reason) {
		return new BasicClassicHttpResponse(status, reason);
	}

	public static ClassicHttpResponse createHttpResponse(ProtocolVersion ver, int status, String reason) {
		ClassicHttpResponse response = new BasicClassicHttpResponse(status, reason);
		response.setVersion(ver);
		return response;
	}

	public static HttpContext createHttpContext() {
		return new HttpCoreContext();
	}

}
