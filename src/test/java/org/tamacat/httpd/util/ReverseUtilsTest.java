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
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.util.IOUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class ReverseUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testRemoveRequestHeaders() {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
		
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
		ClassicHttpResponse targetResponse = new BasicClassicHttpResponse(
			200, "OK");
		
		targetResponse.setHeader("Transfer-Encoding", "gzip");
		targetResponse.setHeader("Content-Length", "123456");
		targetResponse.setHeader("Content-Type", "text/html");
		targetResponse.setHeader("Host", "tamacat.org");

		ClassicHttpResponse response = new BasicClassicHttpResponse(
				200, "OK");
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
		
		ClassicHttpResponse response = new BasicClassicHttpResponse(
				302, "Moved Temporarily");	
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
		
		ClassicHttpResponse response = new BasicClassicHttpResponse(
				302, "Moved Temporarily");	
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
		
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		request.setHeader("Host", "www.example.com");
		
		ClassicHttpResponse response = new BasicClassicHttpResponse(
				200, "OK");
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
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		HttpContext context = new BasicHttpContext();
		InetAddress address = InetAddress.getByName("192.168.1.1"); 
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context);
		assertEquals("192.168.1.1", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	@Deprecated
	public void testSetXForwardedFor_OLD2() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		request.setHeader("X-Forwarded-For", "192.168.100.100");
		HttpContext context = new BasicHttpContext();
		//InetAddress address = InetAddress.getByName("192.168.1.1"); 
		//context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context);
		assertEquals("192.168.100.100", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedFor() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		HttpContext context = new BasicHttpContext();
		InetAddress address = InetAddress.getByName("192.168.1.1"); 
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context, false, "X-Forwarded-For");
		assertEquals("192.168.1.1", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedFor_USE_FORWARD() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		request.setHeader("X-Forwarded-For", "192.168.100.100");
		HttpContext context = new BasicHttpContext();
		//InetAddress address = InetAddress.getByName("192.168.1.1"); 
		//context.setAttribute(RequestUtils.REMOTE_ADDRESS, address);
		ReverseUtils.setXForwardedFor(request, context, true, "X-Forwarded-For");
		assertEquals("192.168.100.100", request.getFirstHeader("X-Forwarded-For").getValue());
	}
	
	@Test
	public void testSetXForwardedProto() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		ServerConfig config = new ServerConfig();
		config.setParam("https", "false");
		ReverseUtils.setXForwardedProto(request, config);
		assertEquals("http", request.getFirstHeader("X-Forwarded-Proto").getValue());
	}
	
	@Test
	public void testSetXForwardedPort() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		ServerConfig config = new ServerConfig();
		config.setParam("Port", "4443");
		config.setParam("https", "false");
		ReverseUtils.setXForwardedPort(request, config);
		assertEquals("4443", request.getFirstHeader("X-Forwarded-Port").getValue());
	}
	
	@Test
	public void testSetXForwarded_From_LB() throws Exception {
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
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
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
		
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
		
		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/servlets");
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

		SSLSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, true);
		Socket socket = factory.createSocket();
		assertTrue(socket instanceof SSLSocket);
		assertFalse(socket.isConnected());
		socket.close();
	}

	// ---- TLS behaviour (SEC-1) --------------------------------------------
	//
	// The four states that must be preserved across the migration:
	//
	//   clientAuth | strictHttps | chain validation | hostname verification
	//   -----------+-------------+------------------+----------------------
	//   false      | true        | on               | on
	//   false      | false       | off              | off
	//   true       | true        | on               | on
	//   true       | false       | on               | off
	//
	// Chain validation is exercised end to end against a local TLS server that
	// presents the CN=localhost certificate signed by "ca.localhost", a CA that
	// is NOT in the JDK default trust store. A handshake that completes proves
	// chain validation is off; a handshake that fails proves it is on.
	//
	// Hostname verification cannot be isolated end to end, because strictHttps
	// drives both columns and "chain off + hostname on" is unreachable. It is
	// asserted directly on the SSLParameters that are applied to the socket
	// before the handshake starts.

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

	static ServerConfig backEndConfig(boolean clientAuth) {
		Properties props = new Properties();
		if (clientAuth) {
			props.setProperty("BackEnd.https.clientAuth", "true");
			props.setProperty("BackEnd.https.keyStoreFile", KEYSTORE);
			props.setProperty("BackEnd.https.keyStoreType", "PKCS12");
			props.setProperty("BackEnd.https.keyPassword", KEYSTORE_PASS);
		}
		return new ServerConfig(props);
	}

	/**
	 * Run a full handshake against the test server.
	 * @return null when the handshake completed, otherwise the failure.
	 */
	static Exception handshakeAgainstUntrustedServer(boolean clientAuth, boolean strictHttps) throws Exception {
		SSLServerSocket server = startTestTlsServer();
		try {
			int port = server.getLocalPort();
			SSLSocketFactory factory = ReverseUtils.createSSLSocketFactory(backEndConfig(clientAuth), strictHttps);
			Socket plain = new Socket(InetAddress.getLoopbackAddress(), port);
			plain.setSoTimeout(10000);
			try {
				SSLSocket ssl = ReverseUtils.createLayeredSocket(factory, plain, "localhost", port, strictHttps);
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
	 * Assert that the handshake failed because the certificate chain was
	 * rejected, and not for some unrelated reason such as a refused
	 * connection - which would make the three "rejected" rows pass vacuously.
	 */
	static void assertRejectedByChainValidation(Exception e) {
		assertNotNull("handshake must fail", e);
		assertTrue("expected a TLS failure but got " + e, e instanceof SSLException);
		Throwable t = e;
		while (t.getCause() != null) {
			t = t.getCause();
		}
		assertTrue("expected a certificate path failure but got " + t,
				t instanceof CertPathBuilderException || t instanceof CertPathValidatorException
					|| t instanceof CertificateException);
	}

	/** Row 1: clientAuth=false, strictHttps=true - chain validation on. */
	@Test
	public void testChainValidationOnWhenStrict() throws Exception {
		assertRejectedByChainValidation(handshakeAgainstUntrustedServer(false, true));
	}

	/** Row 2: clientAuth=false, strictHttps=false - chain validation off. */
	@Test
	public void testChainValidationOffWhenNotStrict() throws Exception {
		Exception e = handshakeAgainstUntrustedServer(false, false);
		assertNull(e != null ? e.toString() : null, e);
	}

	/** Row 3: clientAuth=true, strictHttps=true - chain validation on. */
	@Test
	public void testChainValidationOnWhenClientAuthAndStrict() throws Exception {
		assertRejectedByChainValidation(handshakeAgainstUntrustedServer(true, true));
	}

	/**
	 * Row 4: clientAuth=true, strictHttps=false - chain validation stays ON.
	 * This is the row that breaks silently if the permissive TrustStrategy is
	 * applied to the clientAuth branch of getSSLContext.
	 */
	@Test
	public void testChainValidationOnWhenClientAuthAndNotStrict() throws Exception {
		assertRejectedByChainValidation(handshakeAgainstUntrustedServer(true, false));
	}

	@Test
	public void testHostnameVerificationOnWhenStrict() throws Exception {
		assertEquals("HTTPS", endpointIdentificationAlgorithm(false, true));
		assertEquals("HTTPS", endpointIdentificationAlgorithm(true, true));
	}

	@Test
	public void testHostnameVerificationOffWhenNotStrict() throws Exception {
		assertNull(endpointIdentificationAlgorithm(false, false));
		assertNull(endpointIdentificationAlgorithm(true, false));
	}

	static String endpointIdentificationAlgorithm(boolean clientAuth, boolean strictHttps) throws Exception {
		SSLSocketFactory factory = ReverseUtils.createSSLSocketFactory(backEndConfig(clientAuth), strictHttps);
		SSLSocket socket = (SSLSocket) factory.createSocket();
		try {
			ReverseUtils.setEndpointIdentification(socket, strictHttps);
			return socket.getSSLParameters().getEndpointIdentificationAlgorithm();
		} finally {
			socket.close();
		}
	}

	@Test
	public void testGetSSLContextProtocolDefault() {
		assertEquals("TLSv1.2", ReverseUtils.getSSLContext(backEndConfig(false), true).getProtocol());
	}

	@Test
	public void testGetSSLContextProtocolFromConfig() {
		Properties props = new Properties();
		props.setProperty("BackEnd.https.protocol", "TLSv1.3");
		assertEquals("TLSv1.3", ReverseUtils.getSSLContext(new ServerConfig(props), true).getProtocol());
	}
}
