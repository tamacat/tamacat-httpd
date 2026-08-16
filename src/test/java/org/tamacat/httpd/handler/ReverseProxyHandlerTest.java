package org.tamacat.httpd.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
	 * FR-1/BR-1 (revised): an open, non-stale backend connection already carried
	 * in the context - as this target host's entry in the
	 * {@code Map<HttpHost, ClientHttpConnection>} that {@code DefaultWorker}
	 * shares across requests of the same inbound connection - is reused as-is,
	 * no new socket is created.
	 */
	@Test
	public void testGetClientHttpConnectionReusesOpenNonStaleConnection() throws Exception {
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();
		TrackingClientHttpConnection existing =
			new TrackingClientHttpConnection(serverConfig, true, false);
		Map<HttpHost, ClientHttpConnection> conns = new HashMap<>();
		conns.put(reverseUrl.getTargetHost(), existing);
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertSame("an open, non-stale connection must be reused as-is", existing, result);
		assertFalse("a reused connection must not be closed", existing.closeCalled);
		assertSame("the map entry for this target host must still point at the reused connection",
			result, conns.get(reverseUrl.getTargetHost()));
	}

	/**
	 * FR-1/BR-1 Exception: a closed backend connection carried in the context's
	 * map entry for this target host is replaced by a freshly bound one, and -
	 * per the iteration-2 review fix - the old connection is closed first so it
	 * does not leak.
	 */
	@Test
	public void testGetClientHttpConnectionClosesAndReplacesClosedConnection() throws Exception {
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();
		TrackingClientHttpConnection existing =
			new TrackingClientHttpConnection(serverConfig, false, false);
		Map<HttpHost, ClientHttpConnection> conns = new HashMap<>();
		conns.put(reverseUrl.getTargetHost(), existing);
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory(); //avoid a real network connection.

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertNotSame("a closed connection must not be reused", existing, result);
		assertTrue("the replaced connection must be closed", existing.closeCalled);
		assertTrue("the newly bound connection must be open", result.isOpen());
		assertSame("the map entry for this target host must be updated to the new connection",
			result, conns.get(reverseUrl.getTargetHost()));
	}

	/**
	 * FR-1/BR-1 Exception: a stale backend connection (still {@code isOpen()},
	 * but data unexpectedly available - e.g. the peer half-closed) is likewise
	 * closed and replaced, not merely abandoned.
	 */
	@Test
	public void testGetClientHttpConnectionClosesAndReplacesStaleConnection() throws Exception {
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();
		TrackingClientHttpConnection existing =
			new TrackingClientHttpConnection(serverConfig, true, true);
		Map<HttpHost, ClientHttpConnection> conns = new HashMap<>();
		conns.put(reverseUrl.getTargetHost(), existing);
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory();

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertNotSame("a stale connection must not be reused", existing, result);
		assertTrue("the replaced stale connection must be closed", existing.closeCalled);
	}

	/**
	 * FR-1/B-1: when there is no entry for this target host in the map yet
	 * (first request on this inbound connection), a new connection is created,
	 * bound, and - the fix for B-1 itself - stored back into the map under this
	 * target host's key so it can be found and reused on the next request.
	 */
	@Test
	public void testGetClientHttpConnectionCreatesAndStoresWhenAbsent() throws Exception {
		Map<HttpHost, ClientHttpConnection> conns = new HashMap<>();
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory();
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertNotNull(result);
		assertSame("the newly created connection must be stored under its target host for reuse",
			result, conns.get(reverseUrl.getTargetHost()));
		assertSame("the context's HTTP_OUT_CONN map instance itself must not be replaced",
			conns, context.getAttribute(HttpContextKeys.HTTP_OUT_CONN));
	}

	/**
	 * FR-1/BR-1: a caller outside {@code DefaultWorker}'s normal request loop
	 * (which always shares its own map into the context) may pass a context with
	 * no {@code HTTP_OUT_CONN} map at all. {@code getClientHttpConnection} must
	 * not throw in that case - it creates and stores a map so the call is still
	 * internally consistent.
	 */
	@Test
	public void testGetClientHttpConnectionCreatesMapWhenContextHasNone() throws Exception {
		HttpContext context = createContext();
		handler.socketFactory = new DummySocketFactory();
		ReverseUrl reverseUrl = handler.serviceUrl.getReverseUrl();

		ClientHttpConnection result = handler.getClientHttpConnection(context, reverseUrl);

		assertNotNull(result);
		Object attr = context.getAttribute(HttpContextKeys.HTTP_OUT_CONN);
		assertTrue("a map must be created and stored when the context had none",
			attr instanceof Map);
		@SuppressWarnings("unchecked")
		Map<HttpHost, ClientHttpConnection> conns = (Map<HttpHost, ClientHttpConnection>) attr;
		assertSame(result, conns.get(reverseUrl.getTargetHost()));
	}

	/**
	 * FR-1/BR-1 (revised, §12a code-generation review iteration 1): reuse is
	 * scoped per backend target host. A connection opened for one reverse
	 * target must never be handed back for a request to a different target on
	 * the same inbound (keep-alive) connection - this is the direct regression
	 * test for the defect the map-based redesign fixes. The original
	 * single-field design returned whatever connection happened to be "the"
	 * backend connection, regardless of which target the new request was
	 * actually for, so the client could receive a response from the wrong
	 * backend. {@code src/test/resources/url-config.xml} shows this is a real
	 * configuration: one listen port serves several {@code type="reverse"}
	 * {@code <url>} entries with different {@code reverse} targets.
	 */
	@Test
	public void testGetClientHttpConnectionDoesNotCrossReuseOrDisturbOtherTargetHosts() throws Exception {
		DefaultReverseUrl reverseUrlA = new DefaultReverseUrl(handler.serviceUrl);
		reverseUrlA.setReverse(new URL("http://localhost:8080/examples/"));
		DefaultReverseUrl reverseUrlB = new DefaultReverseUrl(handler.serviceUrl);
		reverseUrlB.setReverse(new URL("http://localhost:9090/other/"));
		HttpHost hostA = reverseUrlA.getTargetHost();
		HttpHost hostB = reverseUrlB.getTargetHost();
		assertNotEquals("the fixture must exercise two genuinely different target hosts",
			hostA, hostB);

		//connA starts closed, so the request for target A must open a fresh
		//connection; connB starts open/non-stale, so the request for target B
		//must reuse it as-is. Both entries pre-populate the same shared map, the
		//way DefaultWorker would carry them across requests of one inbound
		//connection.
		TrackingClientHttpConnection connA =
			new TrackingClientHttpConnection(serverConfig, false, false);
		TrackingClientHttpConnection connB =
			new TrackingClientHttpConnection(serverConfig, true, false);
		Map<HttpHost, ClientHttpConnection> conns = new HashMap<>();
		conns.put(hostA, connA);
		conns.put(hostB, connB);
		HttpContext context = createContext();
		context.setAttribute(HttpContextKeys.HTTP_OUT_CONN, conns);
		handler.socketFactory = new DummySocketFactory(); //target A must reconnect.

		ClientHttpConnection resultA = handler.getClientHttpConnection(context, reverseUrlA);
		ClientHttpConnection resultB = handler.getClientHttpConnection(context, reverseUrlB);

		assertNotSame("target A's closed connection must be replaced, not reused",
			connA, resultA);
		assertTrue("the replaced target A connection must be closed", connA.closeCalled);
		assertSame("target B's open, non-stale connection must be reused as-is",
			connB, resultB);
		assertFalse("replacing target A's connection must not close target B's connection",
			connB.closeCalled);
		assertNotSame("target A and target B must never end up sharing one connection",
			resultA, resultB);
		assertSame("target A's map entry must hold its own new connection",
			resultA, conns.get(hostA));
		assertSame("target B's map entry must be untouched by target A's replacement",
			connB, conns.get(hostB));
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
