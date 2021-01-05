package org.tamacat.httpd;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.tamacat.httpd.config.DefaultReverseUrl;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceType;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.core.HttpProcessorBuilder;
import org.tamacat.httpd.handler.ReverseHttpRequest;
import org.tamacat.httpd.handler.ReverseHttpRequestFactory;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.IOUtils;

public class HttpsClient_test {

	static final Log LOG = LogFactory.getLog(HttpsClient_test.class);

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

		HttpContext context = new HttpClientContext();
		ClientHttpConnection conn = getClientHttpConnection(context, reverseUrl);
		LOG.debug(conn);
		
		HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
		HttpProcessorBuilder procBuilder = new HttpProcessorBuilder();
		HttpProcessor httpproc = procBuilder.build();

		HttpRequest request = new BasicHttpRequest("GET", "/examples/");
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("HTTP",1,1), 200, "OK"));
		
		ReverseHttpRequest targetRequest = ReverseHttpRequestFactory.getInstance(request, response, context, reverseUrl);
		
		HttpResponse targetResponse = httpexecutor.execute(targetRequest, conn, context);
		httpexecutor.postProcess(targetResponse, httpproc, context);
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
			
			return createSSLSocketFactory(protocol).createLayeredSocket(
				new Socket(address.getHostName(), address.getPort()), 
				address.getHostName(), address.getPort(),
				new BasicHttpContext()
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
	
	public SSLConnectionSocketFactory createSSLSocketFactory(String protocol) {
		SSLContext sslContext;
		try {
			sslContext = SSLContext.getInstance(protocol);			
			KeyStore clientKeyStore = loadClientKeyStore();

			KeyManagerFactory keyMgrFactory = KeyManagerFactory.getInstance("SunX509");
			keyMgrFactory.init(clientKeyStore, "changeit".toCharArray());

			sslContext.init(keyMgrFactory.getKeyManagers(), null, null);			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
	}
}
