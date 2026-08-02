package org.tamacat.httpd.util;

import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.util.PropertyUtils;

/**
 * Manual harness (no @Test, not run by surefire).
 *
 * TODO Step 19.3: replace the raw socket exchange below with the core5 classic
 * client API (DefaultBHttpClientConnection + HttpRequestExecutor). Until that
 * step runs there is no HTTP client on the classpath - httpclient has been
 * removed - so this harness only exercises the TLS layer.
 */
public class ReverseUtils_test {

	public static void main(String[] args) throws Exception {
		ServerConfig config = new ServerConfig(PropertyUtils.getProperties("server.properties"));

		boolean strictHttps = false;
		SSLSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, strictHttps);

		int port = 443;
		Socket plain = new Socket(InetAddress.getByName("localhost"), port);
		SSLSocket socket = ReverseUtils.createLayeredSocket(factory, plain, "localhost", port, strictHttps);
		try {
			System.out.println("protocol=" + socket.getSession().getProtocol()
					+ " cipherSuite=" + socket.getSession().getCipherSuite());
		} finally {
			socket.close();
		}
	}
}
