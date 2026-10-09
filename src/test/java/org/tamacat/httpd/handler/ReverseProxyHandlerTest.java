package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpRequestInterceptor;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpResponseInterceptor;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.httpd.filter.RequestFilter;
import org.tamacat.httpd.filter.ResponseFilter;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.DefaultWorker;
import org.tamacat.httpd.mock.DummySocketFactory;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.mock.TrackingClientHttpConnection;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class ReverseProxyHandlerTest {

	ServerConfig serverConfig;
	ReverseProxyHandler handler;

	@Before
	public void setUp() throws Exception {
		handler = new ReverseProxyHandler();
		serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		ServiceUrl serviceUrl = new ServiceUrl(serverConfig);

		serviceUrl.setPath("/test/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://localhost/test/"));
		DefaultReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/examples/"));

		serviceUrl.setReverseUrl(reverseUrl);
		handler.setServiceUrl(serviceUrl);
	}

	@After
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
		HttpRequest request = new BasicHttpRequest("GET", "/test/test.html", HttpVersion.HTTP_1_0);
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		HttpContext context = createContext();

		handler.setHttpFilter(new RequestFilter() {
			@Override
			public void init(ServiceUrl serviceUrl) {
			}
			@Override
			public void doFilter(HttpRequest request, HttpResponse response,
					HttpContext context) {
			}
		});
		handler.handle(request, response, context);

		handler.setHttpFilter(new ResponseFilter() {
			@Override
			public void init(ServiceUrl serviceUrl) {
			}
			@Override
			public void afterResponse(HttpRequest request, HttpResponse response,
					HttpContext context) {
			}
		});
	}

	//@Test
	public void testDoRequest() throws HttpException, IOException {
		HttpRequest request = new BasicHttpRequest("GET", "/test/test.html", HttpVersion.HTTP_1_0);
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
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
		HttpRequest request = new BasicHttpRequest("GET", "/test/test.html", HttpVersion.HTTP_1_0);
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
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

	@Test
	public void testAddHttpRequestInterceptor() {
		handler.addHttpRequestInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(HttpRequest request, HttpContext context)
					throws org.tamacat.httpcore4.HttpException, IOException {
			}
		});
	}

	@Test
	public void testAddHttpResponseInterceptor() {
		handler.addHttpResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, HttpContext context)
					throws org.tamacat.httpcore4.HttpException, IOException {
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
		HttpRequest request = new BasicHttpRequest("GET", "/test/test.html", HttpVersion.HTTP_1_0);
		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
		handler.forwardRequest(request, response, context, handler.serviceUrl.getReverseUrl());

		//DummyHttpRequestExecutor executor = (DummyHttpRequestExecutor)handler.httpexecutor;
		//assertEquals("admin", executor.getHttpRequest().getFirstHeader("X-ReverseProxy-Authorization").getValue());
	}

	//@Test
	public void testProxyAutorizationUserOverride() {
		HttpContext context = createContext();
		HttpRequest request = new BasicHttpRequest("GET", "/test/test.html", HttpVersion.HTTP_1_0);
		request.setHeader("X-ReverseProxy-Authorization", "admin"); //Do not use (remove header)

		HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");
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


	/**
	 * A backend connection is never reused: even when the context already holds an
	 * open connection, a request gets a new connection of its own. The existing one
	 * is left alone - DefaultWorker closes it.
	 */
	@Test
	public void testGetClientHttpConnectionAlwaysOpensANewConnection() throws Exception {
		TrackingClientHttpConnection existing = new TrackingClientHttpConnection(serverConfig);
		List<ClientHttpConnection> conns = new ArrayList<>();
		conns.add(existing);
		HttpContext context = createContext();
		context.setAttribute(DefaultWorker.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory(); //avoid a real network connection.

		ClientHttpConnection result = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());

		assertNotSame("an open connection must not be reused", existing, result);
		assertTrue("the new connection is bound", result.isOpen());
		assertEquals("closing is DefaultWorker's job, not the handler's", 0, existing.closeCount);
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
		context.setAttribute(DefaultWorker.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory();

		ClientHttpConnection first = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());
		ClientHttpConnection second = handler.getClientHttpConnection(context, handler.serviceUrl.getReverseUrl());

		assertEquals(Arrays.asList(first, second), conns);
		assertSame("the shared list must not be replaced", conns, context.getAttribute(DefaultWorker.HTTP_OUT_CONN));
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

		Object attr = context.getAttribute(DefaultWorker.HTTP_OUT_CONN);
		assertTrue("a list must be stored when the context had none", attr instanceof List);
		assertEquals(Arrays.asList(result), attr);
	}
}
