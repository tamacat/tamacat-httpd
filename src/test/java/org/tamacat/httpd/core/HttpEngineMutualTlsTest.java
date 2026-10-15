package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.protocol.BasicHttpContext;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.util.IOUtils;
import org.tamacat.httpd.core.util.PropertyUtils;
import org.tamacat.httpd.filter.TlsClientAuthControlInterceptor;
import org.tamacat.httpd.util.RequestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * <p>T-MTLS: mutual TLS over a real TLS connection on JDK 25.</p>
 *
 * <p>The server socket is created by {@link HttpEngine#createServerSocket()}
 * from {@code https/client-cert/server.properties} (client authentication
 * required, CA trust store and CRL), on an ephemeral port. The client presents
 * {@code test-clients/test01@example.com.p12}. The accepted socket is bound to a
 * {@link ServerHttpConnection} and stored in the context the way
 * {@link DefaultWorker} does, so {@link RequestUtils#setTlsClientAuthPrincipal}
 * reads the peer certificates from the real {@code SSLSession}.</p>
 *
 * <p>Store passwords are read from the same properties file; none are written
 * here.</p>
 */
public class HttpEngineMutualTlsTest {

	static final String SERVER_PROPERTIES = "https/client-cert/server.properties";
	static final String CLIENT_KEYSTORE = "https/client-cert/test-clients/test01@example.com.p12";
	static final String CLIENT_ALIAS = "test01@example.com";
	static final String CERTS_KEY = "javax.security.cert.X509Certificate[]";
	static final String PRINCIPAL_KEY = "javax.net.ssl.cert.SSLSession#getPeerPrincipal";
	/** RequestUtils.TLS_CLIENT_AUTH_PRINCIPAL_CONTEXT_KEY (package-private; the string is part of the contract). */
	static final String PRINCIPAL_NAME_KEY = "SSLSession.getPeerPrincipal";
	static final String HEADER = "X-ARR-ClientCert";

	/** (a) and (b) for both protocols the server enables. */
	@Test
	public void testClientCertificateChainAndPrincipal() throws Exception {
		for (String protocol : new String[] {"TLSv1.2", "TLSv1.3"}) {
			Handshake hs = handshake(true, protocol);
			try {
				assertNull(hs.serverError, protocol + ": server handshake failed: " + hs.serverError);
				assertEquals(protocol, hs.accepted.getSession().getProtocol());

				HttpContext context = new BasicHttpContext();
				RequestUtils.setTlsClientAuthPrincipal(hs.connection, context);

				// (a) the historical "javax." key holds a java.security.cert.X509Certificate[]
				Object value = context.getAttribute(CERTS_KEY);
				assertNotNull(value, protocol);
				assertSame(X509Certificate[].class, value.getClass(), protocol);
				X509Certificate[] chain = (X509Certificate[]) value;
				Certificate[] expected = clientChain();
				assertEquals(expected.length, chain.length, protocol);
				assertTrue(chain.length >= 1, protocol);
				for (int i = 0; i < expected.length; i++) {
					assertEquals(expected[i], chain[i], protocol + " chain[" + i + "]");
				}

				// (b) the principal name of the client certificate
				String subject = ((X509Certificate) expected[0]).getSubjectX500Principal().getName();
				assertEquals(subject, context.getAttribute(PRINCIPAL_NAME_KEY), protocol);
				assertEquals(subject, RequestUtils.getTlsClientAuthPrincipal(context), protocol);
				assertTrue(subject.contains("O=tamacat.org"), subject);
			} finally {
				hs.close();
			}
		}
	}

	/** (c) with the logging configuration of the test class path (no TRACE). */
	@Test
	public void testInterceptorSetsClientCertHeader() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(TlsClientAuthControlInterceptor.class);
		assertFalse(logger.isTraceEnabled());
		assertInterceptorSetsHeader(null);
	}

	/**
	 * (c) with TRACE enabled: the interceptor casts the attribute to
	 * X509Certificate[] and logs every certificate. A ClassCastException would
	 * propagate out of process(); the captured TRACE lines prove the cast and
	 * the loop actually ran.
	 */
	@Test
	public void testInterceptorSetsClientCertHeaderWithTraceLogging() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(TlsClientAuthControlInterceptor.class);
		Level previous = logger.getLevel();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.TRACE);
		try {
			assertInterceptorSetsHeader(appender);
		} finally {
			logger.setLevel(previous);
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	/**
	 * Negative control: a client without a certificate fails the handshake
	 * (https.clientAuth=true sets need-client-auth), and nothing is stored in
	 * the context; a header sent by the client is removed, not passed on.
	 */
	@Test
	public void testNoClientCertificateIsRejectedAndSetsNothing() throws Exception {
		for (String protocol : new String[] {"TLSv1.2", "TLSv1.3"}) {
			Handshake hs = handshake(false, protocol);
			try {
				assertNotNull(hs.serverError, protocol);
				assertInstanceOf(SSLHandshakeException.class, hs.serverError, protocol);
				assertTrue(hs.accepted.getNeedClientAuth(), protocol);

				HttpContext context = new BasicHttpContext();
				context.setAttribute(DefaultWorker.HTTP_IN_CONN, hs.connection);
				HttpRequest request = new BasicHttpRequest("GET", "/");
				request.setHeader(HEADER, "spoofed");
				new TlsClientAuthControlInterceptor().process(request, context);

				assertNull(context.getAttribute(CERTS_KEY), protocol);
				assertNull(context.getAttribute(PRINCIPAL_KEY), protocol);
				assertNull(RequestUtils.getTlsClientAuthPrincipal(context), protocol);
				assertNull(request.getFirstHeader(HEADER), protocol);
			} finally {
				hs.close();
			}
		}
	}

	/**
	 * Runs the interceptor on a real mutual-TLS connection for each protocol.
	 * @param traceLog the appender attached at TRACE level, or null when TRACE is off
	 */
	void assertInterceptorSetsHeader(ListAppender<ILoggingEvent> traceLog) throws Exception {
		boolean trace = traceLog != null;
		for (String protocol : new String[] {"TLSv1.2", "TLSv1.3"}) {
			Handshake hs = handshake(true, protocol);
			try {
				if (trace) traceLog.list.clear();
				assertNull(hs.serverError, protocol + ": server handshake failed: " + hs.serverError);
				HttpContext context = new BasicHttpContext();
				context.setAttribute(DefaultWorker.HTTP_IN_CONN, hs.connection);
				HttpRequest request = new BasicHttpRequest("GET", "/");
				request.setHeader(HEADER, "spoofed");

				new TlsClientAuthControlInterceptor().process(request, context);

				String subject = ((X509Certificate) clientChain()[0]).getSubjectX500Principal().getName();
				Header[] headers = request.getHeaders(HEADER);
				assertEquals(1, headers.length, protocol + " trace=" + trace);
				assertEquals(subject, headers[0].getValue(), protocol + " trace=" + trace);
				assertInstanceOf(X509Certificate[].class, context.getAttribute(CERTS_KEY));

				if (trace) {
					// one "x509 SubjectDN" line per certificate, in chain order
					Certificate[] expected = clientChain();
					List<String> subjects = traceLog.list.stream()
						.map(ILoggingEvent::getFormattedMessage)
						.filter(m -> m.startsWith("x509 SubjectDN: ")).toList();
					assertEquals(expected.length, subjects.size(), protocol + " " + subjects);
					for (int i = 0; i < expected.length; i++) {
						assertEquals("x509 SubjectDN: " + ((X509Certificate) expected[i]).getSubjectX500Principal(),
							subjects.get(i), protocol);
					}
				}
			} finally {
				hs.close();
			}
		}
	}

	// ---- connection set-up ------------------------------------------------

	static final class Handshake {
		ServerSocket server;
		SSLSocket client;
		SSLSocket accepted;
		ServerHttpConnection connection;
		Exception serverError;
		ExecutorService executor;

		void close() throws Exception {
			if (client != null) client.close();
			if (connection != null) connection.shutdown();
			if (accepted != null) accepted.close();
			if (server != null) server.close();
			if (executor != null) executor.shutdownNow();
		}
	}

	static Properties serverProperties() {
		Properties props = PropertyUtils.getProperties(SERVER_PROPERTIES);
		props.setProperty("Port", "0");
		props.setProperty("UseSystemEnvironmentVariables", "false");
		return props;
	}

	static Certificate[] clientChain() throws Exception {
		return clientKeyStore().getCertificateChain(CLIENT_ALIAS);
	}

	static KeyStore clientKeyStore() throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (InputStream in = IOUtils.getInputStream(CLIENT_KEYSTORE)) {
			ks.load(in, serverProperties().getProperty("BackEnd.https.keyPassword").toCharArray());
		}
		return ks;
	}

	static SSLContext clientContext(boolean withCertificate) throws Exception {
		Properties props = serverProperties();
		KeyStore trust = KeyStore.getInstance(props.getProperty("https.CA.keyStoreType"));
		try (InputStream in = IOUtils.getInputStream(props.getProperty("https.CA.keyStoreFile"))) {
			trust.load(in, props.getProperty("https.CA.keyPassword").toCharArray());
		}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(trust);

		SSLContext ctx = SSLContext.getInstance("TLS");
		if (withCertificate) {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(clientKeyStore(), props.getProperty("BackEnd.https.keyPassword").toCharArray());
			ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} else {
			ctx.init(null, tmf.getTrustManagers(), null);
		}
		return ctx;
	}

	static Handshake handshake(boolean withCertificate, String protocol) throws Exception {
		Handshake hs = new Handshake();
		try {
			HttpEngine engine = new HttpEngine();
			engine.setServerConfig(new ServerConfig(serverProperties()));
			hs.server = engine.createServerSocket();
			assertInstanceOf(SSLServerSocket.class, hs.server);
			assertTrue(((SSLServerSocket) hs.server).getNeedClientAuth());

			hs.executor = Executors.newSingleThreadExecutor();
			Future<SSLSocket> accept = hs.executor.submit(() -> {
				SSLSocket s = (SSLSocket) hs.server.accept();
				s.setSoTimeout(10000);
				hs.accepted = s;
				try {
					s.startHandshake();
				} catch (Exception e) {
					hs.serverError = e;
				}
				return s;
			});

			hs.client = (SSLSocket) clientContext(withCertificate).getSocketFactory()
				.createSocket("127.0.0.1", hs.server.getLocalPort());
			hs.client.setSoTimeout(10000);
			SSLParameters params = hs.client.getSSLParameters();
			params.setProtocols(new String[] {protocol});
			params.setEndpointIdentificationAlgorithm("HTTPS"); // checks SAN IP:127.0.0.1
			hs.client.setSSLParameters(params);
			try {
				hs.client.startHandshake();
			} catch (Exception e) {
				if (withCertificate) throw e;
				// without a certificate, TLSv1.2 fails here; TLSv1.3 fails on the server only
			}
			accept.get(20, TimeUnit.SECONDS);

			hs.connection = new ServerHttpConnection(8192);
			hs.connection.bind(hs.accepted);
			return hs;
		} catch (Exception | Error e) {
			hs.close();
			throw e;
		}
	}
}
