package org.tamacat.httpd.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.mock.HttpObjectFactory;

public class HostRequestHandlerMapperTest {

	HostRequestHandlerMapper mapper;

	@BeforeEach
	public void setUp() throws Exception {
		mapper = new HostRequestHandlerMapper();

		ServerConfig config = new ServerConfig();
		String componentsXML = "components.xml";
		mapper.create(config, componentsXML);

	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testLookup() throws Exception {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/");
		HttpContext context = HttpObjectFactory.createHttpContext();
		assertEquals(ThymeleafHttpHandler.class, mapper.lookup(request, context).getClass());
	}

	/**
	 * <p>An empty request target used to resolve to no handler, because 4.4 kept the
	 * request line verbatim and {@code UriPatternMatcher} required the path to start
	 * with {@code "/"} to match {@code "/*"}.
	 *
	 * <p>core5's {@code BasicHttpRequest(String, String)} normalises the target through
	 * {@code setUri(new URI(path))}, and {@code setUri} substitutes {@code "/"} for a
	 * blank raw path. An empty target therefore becomes {@code "/"} and matches
	 * {@code "/*"}. This is a deliberate consequence of ADR-001 (the request-line
	 * normalisation that {@code StandardHttpRequestFactory} used to perform is now
	 * core5's), not a routing regression: an empty request target cannot arrive over
	 * the wire at all, because {@code BasicLineParser#parseRequestLine} rejects it with
	 * a {@code ParseException} before a message object is ever built.
	 *
	 * <p>The "unresolved path yields no handler" case that this test used to cover is
	 * covered directly, and against the real routing core, by
	 * {@code TamacatHttpServerRequestHandlerTest} (SEC-2.1 / REL-2.1).
	 */
	@Test
	public void testLookupEmptyRequestTarget() throws Exception {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", "");
		assertEquals("/", request.getPath());

		HttpContext context = HttpObjectFactory.createHttpContext();
		assertEquals(ThymeleafHttpHandler.class, mapper.lookup(request, context).getClass());
	}

	/*
	 * 13.4 - virtual-host dispatch by Host header, against a mapper built by hand so
	 * that the routing decision, and not components.xml, is what is under test.
	 */

	static HttpRequestHandler handler(final String id) {
		return new HttpRequestHandler() {
			@Override
			public void handle(ClassicHttpRequest request, ClassicHttpResponse response,
					HttpContext context) {
				response.setReasonPhrase(id);
			}

			@Override
			public String toString() {
				return id;
			}
		};
	}

	static UriHttpRequestHandlerMapper rootMapper(HttpRequestHandler handler) {
		UriHttpRequestHandlerMapper mapper = new UriHttpRequestHandlerMapper();
		mapper.register("/*", handler);
		return mapper;
	}

	static ClassicHttpRequest get(String path, String host) {
		ClassicHttpRequest request = HttpObjectFactory.createHttpRequest("GET", path);
		if (host != null) {
			request.setHeader(HttpHeaders.HOST, host);
		}
		return request;
	}

	@Test
	public void testVirtualHostDispatch() throws Exception {
		HostRequestHandlerMapper vhost = new HostRequestHandlerMapper();
		HttpRequestHandler def = handler("default");
		HttpRequestHandler a = handler("a");
		HttpRequestHandler b = handler("b");
		vhost.setHostRequestHandlerMapper(null, rootMapper(def));
		vhost.setHostRequestHandlerMapper("a.example.com", rootMapper(a));
		vhost.setHostRequestHandlerMapper("b.example.com", rootMapper(b));

		HttpContext context = HttpObjectFactory.createHttpContext();

		assertSame(a, vhost.lookup(get("/", "a.example.com"), context));
		assertSame(b, vhost.lookup(get("/", "b.example.com"), context));
		//the port is stripped off the Host header before the lookup
		assertSame(a, vhost.lookup(get("/", "a.example.com:8080"), context));
		//an unknown host and a missing Host header both fall back to the default
		assertSame(def, vhost.lookup(get("/", "unknown.example.com"), context));
		assertSame(def, vhost.lookup(get("/", null), context));
	}

	/**
	 * With a single (default) mapper registered, virtual-host resolution stays off and
	 * the Host header is ignored entirely - the pre-migration behaviour.
	 */
	@Test
	public void testSingleHostIgnoresHostHeader() throws Exception {
		HostRequestHandlerMapper single = new HostRequestHandlerMapper();
		HttpRequestHandler def = handler("default");
		single.setHostRequestHandlerMapper(null, rootMapper(def));

		HttpContext context = HttpObjectFactory.createHttpContext();
		assertSame(def, single.lookup(get("/", "anything.example.com"), context));
	}

	/** A host whose own mapper resolves nothing yields null, not the default host's handler. */
	@Test
	public void testVirtualHostWithNoMatchingPathYieldsNull() throws Exception {
		HostRequestHandlerMapper vhost = new HostRequestHandlerMapper();
		vhost.setHostRequestHandlerMapper(null, rootMapper(handler("default")));

		UriHttpRequestHandlerMapper narrow = new UriHttpRequestHandlerMapper();
		narrow.register("/only/*", handler("only"));
		vhost.setHostRequestHandlerMapper("a.example.com", narrow);

		HttpContext context = HttpObjectFactory.createHttpContext();
		assertNull(vhost.lookup(get("/elsewhere", "a.example.com"), context));
	}
}
