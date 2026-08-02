package org.tamacat.httpd.filter;

import static org.junit.Assert.*;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Test;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.util.HeaderUtils;

public class SecureResponseHeaderFilterTest {

	@Test
	public void testAfterResponse() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		//for (Header h : response.getHeaders()) {
		//	System.out.println(h);
		//}
		assertEquals("DENY", HeaderUtils.getHeader(response, "X-Frame-Options"));
		assertEquals("nosniff", HeaderUtils.getHeader(response, "X-Content-Type-Options"));
		assertEquals("1; mode=block", HeaderUtils.getHeader(response, "X-XSS-Protection"));
		assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", HeaderUtils.getHeader(response, HttpHeaders.EXPIRES));
		assertEquals("no-store, no-cache, must-revalidate, post-check=0, pre-check=0", HeaderUtils.getHeader(response, HttpHeaders.CACHE_CONTROL));
		assertEquals("no-cache", HeaderUtils.getHeader(response, HttpHeaders.PRAGMA));
	}
	
	@Test
	public void testAfterResponse2() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.addHeader("X-Frame-Options","SAMEORIGIN");
		response.addHeader(HttpHeaders.CACHE_CONTROL, "private, no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
		response.addHeader(HttpHeaders.EXPIRES, "Thu, 19 Nov 1981 08:52:00 GMT");
		response.addHeader("X-XSS-Protection", "0");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		//for (Header h : response.getHeaders()) {
		//	System.out.println(h);
		//}
		assertEquals("SAMEORIGIN", HeaderUtils.getHeader(response, "X-Frame-Options"));
		assertEquals("nosniff", HeaderUtils.getHeader(response, "X-Content-Type-Options"));
		assertEquals("0", HeaderUtils.getHeader(response, "X-XSS-Protection"));
		assertEquals("Thu, 19 Nov 1981 08:52:00 GMT", HeaderUtils.getHeader(response, HttpHeaders.EXPIRES));
		assertEquals("private, no-store, no-cache, must-revalidate, post-check=0, pre-check=0", HeaderUtils.getHeader(response, HttpHeaders.CACHE_CONTROL));
		assertEquals("no-cache", HeaderUtils.getHeader(response, HttpHeaders.PRAGMA));
	}

	@Test
	public void testSetFramesOptions() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setFrameOptions("SAMEORIGIN");
		filter.afterResponse(request, response, context);
		assertEquals("SAMEORIGIN", HeaderUtils.getHeader(response, "X-Frame-Options"));
	}

	@Test
	public void testSetContentTypeOptions() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setContentTypeOptions("");
		filter.afterResponse(request, response, context);
		assertEquals(null, HeaderUtils.getHeader(response, "X-Content-Type-Options"));
	}

	@Test
	public void testSetXssProtection() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setXssProtection("0");
		filter.afterResponse(request, response, context);
		assertEquals("0", HeaderUtils.getHeader(response, "X-XSS-Protection"));
	}

	@Test
	public void testSetExpires() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setExpires("Thu, 19 Nov 1981 08:52:00 GMT");
		filter.afterResponse(request, response, context);
		assertEquals("Thu, 19 Nov 1981 08:52:00 GMT", HeaderUtils.getHeader(response, HttpHeaders.EXPIRES));
	}

	@Test
	public void testSetCacheControl() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setCacheControl("no-store");
		filter.afterResponse(request, response, context);
		assertEquals("no-store", HeaderUtils.getHeader(response, HttpHeaders.CACHE_CONTROL));
	}

	@Test
	public void testSetPragma() {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setPragma("no-cache");
		filter.afterResponse(request, response, context);
		assertEquals("no-cache", HeaderUtils.getHeader(response, HttpHeaders.PRAGMA));
		
		filter.setPragma("");
		response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		filter.afterResponse(request, response, context);
		assertEquals(null, HeaderUtils.getHeader(response, HttpHeaders.PRAGMA));
	}
	
	@Test
	public void testContentType() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setEntity(new ByteArrayEntity("TEST".getBytes(), null));
		HttpContext context = createHttpContext();
		//System.out.println(response.getEntity().getContentType());
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		assertEquals("text/html; charset=UTF-8", HeaderUtils.getHeader(response, HttpHeaders.CONTENT_TYPE));
	}

	@Test
	public void testContentTypeJSON() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/test");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));
		HttpContext context = createHttpContext();
		//System.out.println(response.getEntity().getContentType());
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		assertEquals(null, HeaderUtils.getHeader(response, HttpHeaders.CONTENT_TYPE));
		assertEquals("application/json; charset=UTF-8", response.getEntity().getContentType());
	}
	
	@Test
	public void testContentType200() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/font.woff2");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setEntity(new ByteArrayEntity("TEST".getBytes(), null));
		//System.out.println(response.getEntity().getContentType());
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		assertEquals("font/woff2", HeaderUtils.getHeader(response, HttpHeaders.CONTENT_TYPE));
	}

	@Test
	public void testContentType302() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/font.woff2");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 302, "Found");

		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.afterResponse(request, response, context);
		assertEquals(null, HeaderUtils.getHeader(response, HttpHeaders.CONTENT_TYPE));
	}
	
	@Test
	public void testIsAddCacheControlHeadersFalse() throws Exception {
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setHeader(HttpHeaders.CONTENT_TYPE, "font/woff2");
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		assertEquals(false, filter.isAddCacheControlHeaders(response));
	}
	
	@Test
	public void testIsAddCacheControlHeadersTrue() throws Exception {
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setHeader(HttpHeaders.CONTENT_TYPE, "text/html");
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		assertEquals(true, filter.isAddCacheControlHeaders(response));
	}
	
	@Test
	public void testSetForceReplaceErrorPage_Default() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 400, "Bad Request");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		try {	
			filter.afterResponse(request, response, context);
		} catch (HttpException e) {
			fail();
		}
	}
	
	@Test
	public void testSetForceReplaceErrorPage_503() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 503, "Service Unavailable");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setForceReplaceErrorPage("400,503");
		try {
			filter.afterResponse(request, response, context);
			fail();
		} catch (HttpException e) {
			assertEquals(503, e.getHttpStatus().getStatusCode());
		}
	}
	
	@Test
	public void testSetForceReplaceErrorPage_None() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 503, "Service Unavailable");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setForceReplaceErrorPage("");
		filter.afterResponse(request, response, context);
	}
	
	@Test
	public void testSetForceReplaceErrorPage_Disabled() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 400, "Bad Request");
		response.setHeader("X-Override-Error", "disabled");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setForceReplaceErrorPage("400");
		assertFalse(filter.isForceReplaceErrorPage(response));

		filter.afterResponse(request, response, context);
		assertTrue(response.getFirstHeader("X-Override-Error") == null);
	}
	
	@Test
	public void testSetAppendResponseHeader() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setAppendResponseHeader("Strict-Transport-Security: max-age=63072000; includeSubDomains; preload");
		
		filter.afterResponse(request, response, context);
		assertEquals("max-age=63072000; includeSubDomains; preload", response.getFirstHeader("Strict-Transport-Security").getValue());
	}
	
	@Test
	public void testSetAppendResponseHeader2() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setAppendResponseHeader("Content-Security-Policy: script-src 'self' https://example.com");
		
		filter.afterResponse(request, response, context);
		assertEquals("script-src 'self' https://example.com", response.getFirstHeader("Content-Security-Policy").getValue());
	}
	
	@Test
	public void testSetAppendResponseHeader_DO_NOT_OVERRIDE() throws Exception {
		ClassicHttpRequest request = createHttpRequest("GET", "/");
		ClassicHttpResponse response = createHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
		response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
		HttpContext context = createHttpContext();
		
		SecureResponseHeaderFilter filter = new SecureResponseHeaderFilter();
		filter.setAppendResponseHeader("Strict-Transport-Security: max-age=63072000; includeSubDomains; preload");
		
		filter.afterResponse(request, response, context);
		assertEquals("max-age=31536000; includeSubDomains", response.getFirstHeader("Strict-Transport-Security").getValue());
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
		return new BasicHttpContext();
	}
}
