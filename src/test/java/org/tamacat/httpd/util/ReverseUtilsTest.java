/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.util.IOUtils;
import org.tamacat.util.PropertyUtils;

public class ReverseUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testRemoveRequestHeaders() {
		HttpRequest request = new BasicHttpRequest("GET", "/");
		
		request.setHeader("Transfer-Encoding", "gzip");
		request.setHeader("Content-Length", "123456");
		request.setHeader("Content-Type", "text/html");

		ReverseUtils.removeRequestHeaders(request);
		
		assertNull(request.getFirstHeader("Transfer-Encoding"));
		assertNull(request.getFirstHeader("Content-Length"));
		assertEquals("text/html", request.getFirstHeader("Content-Type").getValue());
	}

	@Test
	public void testCopyHttpResponse() {
		HttpResponse targetResponse = new BasicHttpResponse(
			new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		
		targetResponse.setHeader("Transfer-Encoding", "gzip");
		targetResponse.setHeader("Content-Length", "123456");
		targetResponse.setHeader("Content-Type", "text/html");
		targetResponse.setHeader("Host", "tamacat.org");

		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		response.setHeader("Set-Cookie", "key1=value1; domain=192.168.1.1");

		ReverseUtils.copyHttpResponse(targetResponse, response);
		
		assertNull(response.getFirstHeader("Transfer-Encoding"));
		assertNull(response.getFirstHeader("Content-Length"));
		assertNull(response.getFirstHeader("Content-Type"));
		assertEquals("tamacat.org", response.getFirstHeader("Host").getValue());
		
		assertEquals("key1=value1; domain=192.168.1.1", response.getFirstHeader("Set-Cookie").getValue());
	}
	
	@Test
	public void testRewriteLocationHeader() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/examples/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://localhost/examples/servlets"));
		ReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/examples/"));
		
		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 302, "Moved Temporarily"));	
		response.setHeader("Location", "http://localhost:8080/examples/servlets/");
		ReverseUtils.rewriteLocationHeader(null, response, reverseUrl);
		assertEquals("http://localhost/examples/servlets/",
			response.getFirstHeader("Location").getValue()
		);
	}

	@Test
	public void testRewriteContentLocationHeader() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/examples/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://localhost/examples/servlets"));
		ReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://localhost:8080/examples/"));
		
		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 302, "Moved Temporarily"));	
		response.setHeader("Content-Location", "http://localhost/examples/servlets/");
		ReverseUtils.rewriteContentLocationHeader(null, response, reverseUrl);
		assertEquals("http://localhost/examples/servlets/",
			response.getFirstHeader("Content-Location").getValue()
		);
	}
	
	@Test
	public void testRewriteSetCookieHeader() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/examples/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://www.example.com/examples/servlets"));
		ReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://192.168.1.1:8080/examples/"));
		
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		request.setHeader("Host", "www.example.com");
		
		HttpResponse response = new BasicHttpResponse(
				new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		//Case-01
		response.setHeader("Set-Cookie", "key1=value1; domain=192.168.1.1");
		ReverseUtils.rewriteSetCookieHeader(request, response, reverseUrl);
		assertEquals("www.example.com",
			HeaderUtils.getCookieValue(
					response.getFirstHeader("Set-Cookie").getValue(), "domain")
		);
		
		//Case-02
		response.setHeader("Set-Cookie", "key2=value2; DOMAIN=192.168.1.1");
		ReverseUtils.rewriteSetCookieHeader(request, response, reverseUrl);
		assertEquals("www.example.com",
				HeaderUtils.getCookieValue(
						response.getFirstHeader("Set-Cookie").getValue(), "domain")
		);
	}
	
	@Test
	@Deprecated
	public void testSetXForwardedFor_OLD() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		HttpContext context = new BasicHttpContext();
		InetAddress address = InetAddress.getByName("192.168.1.1"); 
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context);
		assertEquals("192.168.1.1", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	@Deprecated
	public void testSetXForwardedFor_OLD2() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		request.setHeader("X-Forwarded-For", "192.168.100.100");
		HttpContext context = new BasicHttpContext();
		//InetAddress address = InetAddress.getByName("192.168.1.1"); 
		//context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context);
		assertEquals("192.168.100.100", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedFor() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		HttpContext context = new BasicHttpContext();
		InetAddress address = InetAddress.getByName("192.168.1.1"); 
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context, false, "X-Forwarded-For");
		assertEquals("192.168.1.1", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedFor_USE_FORWARD() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		request.setHeader("X-Forwarded-For", "192.168.100.100");
		HttpContext context = new BasicHttpContext();
		//InetAddress address = InetAddress.getByName("192.168.1.1"); 
		//context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context, true, "X-Forwarded-For");
		assertEquals("192.168.100.100", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedProto() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		ServerConfig config = new ServerConfig();
		config.setParam("https", "false");
		ReverseUtils.setXForwardedProto(request, config);
		assertEquals("http", request.getFirstHeader("X-Forwarded-Proto").getValue());
	}
	
	@Test
	public void testSetXForwardedPort() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		ServerConfig config = new ServerConfig();
		config.setParam("Port", "4443");
		config.setParam("https", "false");
		ReverseUtils.setXForwardedPort(request, config);
		assertEquals("4443", request.getFirstHeader("X-Forwarded-Port").getValue());
	}
	
	@Test
	public void testSetXForwarded_From_LB() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		//LoadBalancer add X-Forwarded request headers.
		request.setHeader("X-Forwarded-Port", "443");
		request.setHeader("X-Forwarded-Proto", "https");
		
		ServerConfig config = new ServerConfig();
		config.setParam("Port", "80");
		config.setParam("https", "false");
		ReverseUtils.setXForwardedPort(request, config);
		ReverseUtils.setXForwardedProto(request, config);
		
		assertEquals("443", request.getFirstHeader("X-Forwarded-Port").getValue());
		assertEquals("https", request.getFirstHeader("X-Forwarded-Proto").getValue());
	}
	
	@Test
	public void testSetXForwarded_HTTPS() throws Exception {
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		
		ServerConfig config = new ServerConfig();
		config.setParam("Port", "443");
		config.setParam("https", "true");
		ReverseUtils.setXForwardedPort(request, config);
		ReverseUtils.setXForwardedProto(request, config);
		
		assertEquals("443", request.getFirstHeader("X-Forwarded-Port").getValue());
		assertEquals("https", request.getFirstHeader("X-Forwarded-Proto").getValue());
	}
	
	@Test
	public void testGetConvertedSetCookieHeaderDomain() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);
		serviceUrl.setPath("/examples/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("http://www.example.com/"));
		ReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("http://192.168.1.1:8080/examples/"));
		
		HttpRequest request = new BasicHttpRequest("GET", "/examples/servlets");
		request.setHeader("Host", "www.example.com");
		String line = "name=aaa, domain=xxx; Domain=192.168.1.1";
		assertEquals("name=aaa, domain=xxx; domain=www.example.com", ReverseUtils.getConvertedSetCookieHeader(request, reverseUrl, line));
		
		String line2 = "name=aaa, domain=xxx; Domain=192.168.1x1";
		assertEquals("name=aaa, domain=xxx; Domain=192.168.1x1", ReverseUtils.getConvertedSetCookieHeader(request, reverseUrl, line2));
	}
	
	@Test
	public void testGetConvertedSetCookieHeader() throws Exception {
		String before = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; Path=/dist";
		String dist = "/dist";
		String src = "/src";
		String after = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; Path=/src";
		assertEquals(after, ReverseUtils.getConvertedSetCookieHeader(dist, src, before));
		
		before = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; path=/dist";
		assertEquals(after, ReverseUtils.getConvertedSetCookieHeader(dist, src, before));
		
		before = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; PATH=/dist";
		assertEquals(after, ReverseUtils.getConvertedSetCookieHeader(dist, src, before));
		
		before = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV;Path=/dist";
		assertEquals(after, ReverseUtils.getConvertedSetCookieHeader(dist, src, before));
		
		before = "JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV;path=/dist";
		assertEquals(after, ReverseUtils.getConvertedSetCookieHeader(dist, src, before));
		
		assertEquals(null, ReverseUtils.getConvertedSetCookieHeader(dist, src, null));
	}
	
	
	@Test
	public void testStripEnd() {
		assertEquals("/testabc", ReverseUtils.stripEnd("/testabc", "/test"));
		assertEquals("/test", ReverseUtils.stripEnd("/test", null));
		assertEquals("/test", ReverseUtils.stripEnd("/test ", null));
		assertEquals("/test", ReverseUtils.stripEnd("/test", ""));
		assertEquals("", ReverseUtils.stripEnd("", ""));
		assertEquals(null, ReverseUtils.stripEnd(null, null));
	}
	
	@Test
	public void testCreateSSLSocketFactory() throws Exception {
		ServerConfig config = new ServerConfig(PropertyUtils.getProperties("server.properties"));

		//SSLConnectionSocketFactory factory = ReverseUtils.createSSLSocketFactory("TLSv1.2", NoopHostnameVerifier.INSTANCE);
		SSLConnectionSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, true);
		assertEquals("Socket[unconnected]", factory.createSocket(new BasicHttpContext()).toString());
	}

	// ---- TLS behaviour (FR-1/NFR-2, 260827-codeql-followup) ----------------
	//
	// strictHttps now defaults to true (ReverseProxyHandler). These two tests
	// exercise ReverseUtils.createSSLSocketFactory's isStrict parameter end to
	// end against a local TLS server presenting the CN=localhost certificate
	// (https/localhost.p12) signed by "ca.localhost", a CA that is NOT in the
	// JDK default trust store: a handshake that fails proves the new default
	// rejects an untrusted certificate; a handshake that succeeds proves the
	// isStrict=false opt-out (SR-2) still trusts any certificate, unchanged.

	static final String KEYSTORE = "https/localhost.p12";
	static final String KEYSTORE_PASS = "changeit";

	/**
	 * Start a TLS server presenting a certificate signed by an untrusted CA.
	 * The caller closes the returned socket.
	 */
	static SSLServerSocket startTestTlsServer() throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		InputStream in = IOUtils.getInputStream(KEYSTORE);
		try {
			ks.load(in, KEYSTORE_PASS.toCharArray());
		} finally {
			in.close();
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, KEYSTORE_PASS.toCharArray());
		SSLContext ctx = SSLContext.getInstance("TLSv1.2");
		ctx.init(kmf.getKeyManagers(), null, null);

		final SSLServerSocket server = (SSLServerSocket) ctx.getServerSocketFactory()
				.createServerSocket(0, 1, InetAddress.getLoopbackAddress());
		server.setSoTimeout(10000);
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Socket accepted = server.accept();
					accepted.setSoTimeout(10000);
					((SSLSocket) accepted).startHandshake();
					accepted.close();
				} catch (Exception e) {
					//expected when the client rejects the certificate.
				}
			}
		});
		t.setDaemon(true);
		t.start();
		return server;
	}

	/**
	 * Run a full handshake against the test server.
	 * @return null when the handshake completed, otherwise the failure.
	 */
	static Exception handshakeAgainstUntrustedServer(boolean isStrict) throws Exception {
		SSLServerSocket server = startTestTlsServer();
		try {
			int port = server.getLocalPort();
			ServerConfig config = new ServerConfig();
			SSLConnectionSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, isStrict);
			Socket plain = new Socket(InetAddress.getLoopbackAddress(), port);
			plain.setSoTimeout(10000);
			try {
				Socket ssl = factory.createLayeredSocket(plain, "localhost", port, new BasicHttpContext());
				ssl.close();
				return null;
			} catch (Exception e) {
				return e;
			}
		} finally {
			server.close();
		}
	}

	/**
	 * FR-1 (new default): strictHttps=true must reject a certificate signed
	 * by a CA the JVM does not trust.
	 */
	@Test
	public void testCreateSSLSocketFactoryDefaultRejectsUntrustedCertificate() throws Exception {
		Exception e = handshakeAgainstUntrustedServer(true);
		assertNotNull("handshake must fail for an untrusted certificate", e);
		assertTrue("expected an SSL/certificate failure but got " + e, e instanceof SSLException);
	}

	/**
	 * SR-2 (opt-out): strictHttps=false must keep accepting any certificate,
	 * exactly as before this fix (createGenerousTrustManager()).
	 */
	@Test
	public void testCreateSSLSocketFactoryOptOutStillTrustsAnyCertificate() throws Exception {
		Exception e = handshakeAgainstUntrustedServer(false);
		assertNull(e != null ? e.toString() : null, e);
	}
}
