package org.tamacat.httpd;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.tamacat.httpd.config.HttpProxyConfig;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.util.ReverseUtils;
import org.tamacat.httpd.util.SSLLayeredSocketFactory;

public class HttpsClient_test2 {

	// Manual smoke test for ReverseUtils.createSSLSocketFactory(). Previously
	// used HttpProxyConfig.setProxy(HttpClientBuilder) and a full httpclient
	// CloseableHttpClient to execute a real GET request through an (optional)
	// forward proxy; both setProxy(HttpClientBuilder) and httpclient itself
	// have been removed in 1.6.0 [BR-6/BR-8], and httpcore4 (the vendored
	// httpcore) has no equivalent full HTTP client, so this now exercises the
	// SSLLayeredSocketFactory directly against a local HTTPS listener.
	public static void main(String[] args) throws Exception {
		HttpProxyConfig proxy = new HttpProxyConfig();
		//proxy.setProxyHost("localhost");
		//proxy.setProxyPort(3128);

		ServerConfig config = new ServerConfig();
		SSLLayeredSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, false);

		InetSocketAddress address = new InetSocketAddress("localhost", 443);
		Socket plain = new Socket(address.getHostName(), address.getPort());
		Socket ssl = factory.createLayeredSocket(plain, address.getHostName(), address.getPort(), null);
		System.out.println(ssl);
		ssl.close();
	}
}
