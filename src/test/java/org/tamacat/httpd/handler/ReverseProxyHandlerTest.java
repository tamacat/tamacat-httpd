package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.filter.RequestFilter;
import org.tamacat.httpd.filter.ResponseFilter;
import org.tamacat.httpd.mock.DummySocketFactory;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.mock.TrackingClientHttpConnection;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class ReverseProxyHandlerTest {

	ServerConfig serverConfig;
	ReverseProxyHandler handler;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new ReverseProxyHandler();
		serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		ServiceUrl serviceUrl = new ServiceUrl(serverConfig);

		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URI("http://localhost/test/").toURL());
		DefaultReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URI("http://localhost:8080/examples/").toURL());

		serviceUrl.setReverseUrl(reverseUrl);
		handler.setServiceUrl(serviceUrl);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	HttpContext createContext() {
		HttpContext context = HttpObjectFactory.createHttpContext();
		try {
			InetAddress address = InetAddress.getByName("127.0.0.1");
			context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return context;
	}

	@Test
	public void testHandle() {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test/test.html");
		request.setVersion(HttpVersion.HTTP_1_0);
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = createContext();

		handler.setHttpFilter(new RequestFilter() {
			@Override
			public void init(ServiceUrl serviceUrl) {
			}
			@Override
			public void doFilter(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext context) {
			}
		});
		handler.handle(request, response, context);

		handler.setHttpFilter(new ResponseFilter() {
			@Override
			public void init(ServiceUrl serviceUrl) {
			}
			@Override
			public void afterResponse(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext context) {
			}
		});
	}

	//@Test
	public void testDoRequest() throws HttpException, IOException {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test/test.html");
		request.setVersion(HttpVersion.HTTP_1_0);
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = createContext();

		handler.doRequest(request, response, context);
	}

	@Test
	public void testGetEntity() {
		assertNotNull(handler.getEntity("<html>TEST</html>"));

		handler.setEncoding("none");
		assertNull(handler.getEntity("<html>TEST</html>"));
	}

	@Test
	public void testGetFileEntity() {
		assertNotNull(handler.getFileEntity(new File("./src/test/resources/htdocs/index.html")));
	}

	@Test
	public void testForwardRequest() {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test/test.html");
		request.setVersion(HttpVersion.HTTP_1_0);
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = createContext();
		ServiceUrl serviceUrl = new ServiceUrl(serverConfig);

		handler.setServiceUrl(serviceUrl);
		try {
			handler.forwardRequest(request, response, context, handler.serviceUrl.getReverseUrl());
			fail();
		} catch (ServiceUnavailableException e) {
			assertEquals("reverseUrl is null.", e.getMessage());
		}
	}

	/**
	 * Backend keep-alive is off: even when the context already holds an open
	 * connection to the same backend, a request gets a new connection of its own.
	 * The existing one is left alone - DefaultWorker closes it.
	 */
	@Test
	public void testGetClientHttpConnectionAlwaysOpensANewConnection() throws Exception {
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();
		TrackingClientHttpConnection existing = new TrackingClientHttpConnection(serverConfig);
		List<ClientHttpConnection> conns = new ArrayList<>();
		conns.add(existing);
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory(); //avoid a real network connection.

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertNotSame(existing, result, "an open connection to the same backend must not be reused");
		assertTrue(result.isOpen(), "the new connection is bound");
		assertEquals(0, existing.closeCount, "closing is DefaultWorker's job, not the handler's");
	}

	/**
	 * The new connection is added to the list DefaultWorker shares under
	 * HTTP_OUT_CONN - the list instance itself, not a copy - so the worker can
	 * close it once the response has been sent. It is not closed here: the
	 * response body is streamed from it after the handler returns.
	 */
	@Test
	public void testGetClientHttpConnectionRegistersTheConnectionForTheWorkerToClose() throws Exception {
		List<ClientHttpConnection> conns = new ArrayList<>();
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory();

		ClientHttpConnection first = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());
		ClientHttpConnection second = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());

		assertEquals(List.of(first, second), conns);
		assertSame(conns, context.getAttribute(HttpContextKeys.HTTP_OUT_CONN), "the shared list must not be replaced");
		assertTrue(first.isOpen() && second.isOpen());
	}

	/**
	 * A caller outside DefaultWorker's request loop may pass a context without the
	 * list. getClientHttpConnection must not throw; it stores a new list so the
	 * caller can still reach - and close - the connection.
	 */
	@Test
	public void testGetClientHttpConnectionCreatesTheListWhenContextHasNone() throws Exception {
		HttpContext context = createContext();
		handler.socketFactory = new DummySocketFactory();

		ClientHttpConnection result = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());

		Object attr = context.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
		assertTrue(attr instanceof List, "a list must be stored when the context had none");
		assertEquals(List.of(result), attr);
	}

	@Test
	public void testAddHttpRequestInterceptor() {
		handler.addHttpRequestInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(HttpRequest request, EntityDetails entity, HttpContext context)
					throws org.apache.hc.core5.http.HttpException, IOException {
			}
		});
	}

	@Test
	public void testAddHttpResponseInterceptor() {
		handler.addHttpResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, EntityDetails entity, HttpContext context)
					throws org.apache.hc.core5.http.HttpException, IOException {
			}
		});
	}

	@Test
	public void testSetProxyAuthorizationHeader() {
		//default
		assertEquals("X-ReverseProxy-Authorization", handler.proxyAuthorizationHeader);

		handler.setProxyAuthorizationHeader("Custom-ReverseProxy-Authorization");
		assertEquals("Custom-ReverseProxy-Authorization", handler.proxyAuthorizationHeader);
	}

	@Test
	public void testSetProxyOrignPathHeader() {
		//default
		assertEquals("X-ReverseProxy-Origin-Path", handler.proxyOrignPathHeader);

		handler.setProxyOrignPathHeader("Custom-ProxyOrignPathHeader");
		assertEquals("Custom-ProxyOrignPathHeader", handler.proxyOrignPathHeader);
	}

	//@Test
	public void testProxyAutorizationUser() {
		HttpContext context = createContext();
		context.setAttribute("REMOTE_USER", "admin");
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test/test.html");
		request.setVersion(HttpVersion.HTTP_1_0);
		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.forwardRequest(request, response, context, handler.serviceUrl.getReverseUrl());

		//DummyHttpRequestExecutor executor = (DummyHttpRequestExecutor)handler.httpexecutor;
		//assertEquals("admin", executor.getHttpRequest().getFirstHeader("X-ReverseProxy-Authorization").getValue());
	}

	//@Test
	public void testProxyAutorizationUserOverride() {
		HttpContext context = createContext();
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/test/test.html");
		request.setVersion(HttpVersion.HTTP_1_0);
		request.setHeader("X-ReverseProxy-Authorization", "admin"); //Do not use (remove header)

		ClassicHttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.forwardRequest(request, response, context, handler.serviceUrl.getReverseUrl());

		//DummyHttpRequestExecutor executor = (DummyHttpRequestExecutor)handler.httpexecutor;
		//assertEquals(null, executor.getHttpRequest().getFirstHeader("X-ReverseProxy-Authorization"));
	}
	
	@Test
	public void testSetOverrideHostHeaderWithReverseUrl() {
		assertFalse(handler.overrideHostHeaderWithReverseUrl);
		
		handler.setOverrideHostHeaderWithReverseUrl(true);
		assertTrue(handler.overrideHostHeaderWithReverseUrl);
	}
	
	@Test
	public void testSetOverrideHostHeader() {
		assertNull(handler.overrideHostHeader);
		
		handler.setOverrideHostHeader("example.com");
		assertEquals("example.com", handler.overrideHostHeader);
	}

}
