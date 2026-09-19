package org.tamacat.httpd.util;

import java.net.Socket;

import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.util.PropertyUtils;

public class ReverseUtils_test {

	// Manual smoke test for ReverseUtils.createSSLSocketFactory(). Previously
	// built a full httpclient CloseableHttpClient (HttpClientBuilder/HttpGet)
	// around the factory and executed a real GET request; httpclient has been
	// removed in 1.6.0 [BR-6/BR-8] and httpcore4 (the vendored httpcore) has
	// no equivalent full HTTP client, so this now exercises the
	// SSLLayeredSocketFactory directly (createSocket/createLayeredSocket),
	// matching what ReverseUtilsTest's testCreateSSLSocketFactory* methods
	// already verify under JUnit.
	public static void main(String[] args) throws Exception {
		ServerConfig config = new ServerConfig(PropertyUtils.getProperties("server.properties"));

		SSLLayeredSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, false);
		Socket socket = factory.createSocket(null);
		System.out.println(socket);
	}
}
