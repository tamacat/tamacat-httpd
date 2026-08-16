package org.tamacat.httpd;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpProcessorBuilder;
import org.tamacat.httpd.handler.ReverseHttpRequest;
import org.tamacat.httpd.handler.ReverseHttpRequestFactory;
import org.tamacat.httpd.util.ReverseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.IOUtils;

/**
 * Manual harness (no @Test, not run by surefire).
 *
 * <p>Drives one reverse-proxy style exchange against {@code https://localhost/ex/}
 * using the HttpComponents Core 5.x classic client API
 * ({@link ClientHttpConnection} - a {@code DefaultBHttpClientConnection} - plus
 * {@link HttpRequestExecutor}), with the client certificate loaded from
 * {@code https/client-cert/test01@example.com.p12}.
 *
 * <p>Migration notes (2.0): the TLS layer used to be built by httpclient's
 * {@code SSLConnectionSocketFactory}; it is now core5's
 * {@link org.apache.hc.core5.ssl.SSLContextBuilder} plus
 * {@link ReverseUtils#createLayeredSocket}. The exchange itself used
 * {@code org.apache.http} (httpcore 4.4) types; those are now the core5
 * {@code ClassicHttpRequest} / {@code ClassicHttpResponse} equivalents.
 */
public class HttpsClient_test {

	private static final Logger LOG = LoggerFactory.getLogger(HttpsClient_test.class);

	ServerConfig serverConfig = new ServerConfig();
	
	public static void main(String[] args) throws Exception {
		new HttpsClient_test().test();
	}
	
	public void test() throws Exception {		
		ServiceUrl serviceUrl = new ServiceUrl(serverConfig);
		serviceUrl.setPath("/examples/");
		serviceUrl.setType(ServiceType.REVERSE);
		serviceUrl.setHost(new URL("https://localhost/examples/"));

		ReverseUrl reverseUrl = new DefaultReverseUrl(serviceUrl);
		reverseUrl.setReverse(new URL("https://localhost/ex/"));

		//LOG.debug(reverseUrl.getTargetAddress().getHostName());
		//LOG.debug(reverseUrl.getTargetAddress().getPort());

		HttpContext context = new HttpCoreContext();
		ClientHttpConnection conn = getClientHttpConnection(context, reverseUrl);
		LOG.debug(conn.toString());
		
		HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
		HttpProcessorBuilder procBuilder = new HttpProcessorBuilder();
		HttpProcessor httpproc = procBuilder.build();

		ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/examples/");
		ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
		
		ReverseHttpRequest targetRequest = ReverseHttpRequestFactory.getInstance(
				request, response, context, reverseUrl, HttpVersion.HTTP_1_1);
		
		try {
			ClassicHttpResponse targetResponse = httpexecutor.execute(targetRequest, conn, context);
			try {
				httpexecutor.postProcess(targetResponse, httpproc, context);
				LOG.debug(targetResponse.getVersion() + " " + targetResponse.getCode()
						+ " " + targetResponse.getReasonPhrase());
				LOG.debug(EntityUtils.toString(targetResponse.getEntity()));
			} finally {
				targetResponse.close();
			}
		} finally {
			conn.close();
		}
	}
	
	protected ClientHttpConnection getClientHttpConnection(HttpContext context, ReverseUrl reverseUrl) throws IOException {
		ClientHttpConnection conn = new ClientHttpConnection(serverConfig);
		Socket outsocket = createSSLSocket(reverseUrl, "TLSv1.2");
		if (outsocket == null) throw new SocketException("Can not create socket.");
		conn.bind(outsocket);
		LOG.debug("Outgoing connection to "	+ outsocket.getInetAddress());
		return conn;
	}
		
	public Socket createSSLSocket(ReverseUrl reverseUrl, String protocol) {
		try {
			InetSocketAddress address = reverseUrl.getTargetAddress();

			//strictHttps=false: no hostname verification, matching the
			//NoopHostnameVerifier this harness used before the migration.
			return ReverseUtils.createLayeredSocket(
				createSSLSocketFactory(protocol),
				new Socket(address.getHostName(), address.getPort()),
				address.getHostName(), address.getPort(), false
			);
		} catch (Exception e) {
			e.printStackTrace();
			LOG.warn(e.getMessage());
			return null;
		}
	}
	
	public KeyStore loadClientKeyStore() throws Exception {
		InputStream in = IOUtils.getInputStream("https/client-cert/test01@example.com.p12");
		
		KeyStore clientKeyStore = KeyStore.getInstance("pkcs12");
		final char[] pwdChars = "changeit".toCharArray();
		clientKeyStore.load(in, pwdChars);
		
		//LOG.debug(clientKeyStore.getCertificate("test01@example.com").getPublicKey());
		//LOG.debug(clientKeyStore.getKey("test01@example.com", pwdChars));
		return clientKeyStore;
	}
	
	public SSLSocketFactory createSSLSocketFactory(String protocol) {
		try {
			KeyStore clientKeyStore = loadClientKeyStore();
			//No trust material is loaded, so SSLContextBuilder passes null trust
			//managers to SSLContext.init() and the JDK defaults apply: the server
			//certificate chain is still validated.
			SSLContext sslContext = SSLContextBuilder.create()
				.setProtocol(protocol)
				.setKeyManagerFactoryAlgorithm("SunX509")
				.loadKeyMaterial(clientKeyStore, "changeit".toCharArray(), null)
				.build();
			return sslContext.getSocketFactory();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
