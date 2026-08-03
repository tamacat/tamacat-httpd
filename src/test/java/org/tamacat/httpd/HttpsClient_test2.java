package org.tamacat.httpd;

import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.ClientHttpConnection;
import org.tamacat.httpd.util.ReverseUtils;

/**
 * Manual harness (no @Test, not run by surefire).
 *
 * <p>Sends {@code GET /ex/} over TLS to {@code localhost:443} and prints the
 * status line and the response body.
 *
 * <p>Migration notes (2.0): before the migration this harness drove httpclient's
 * {@code HttpClients.custom()...execute(HttpHost, HttpRequest)} through
 * {@code HttpProxyConfig.setProxy(...)}. httpclient is no longer a dependency and
 * {@code HttpProxyConfig} was deleted (ADR-002), so the exchange is now driven by the
 * HttpComponents Core 5.x classic client API
 * ({@link ClientHttpConnection} - a {@code DefaultBHttpClientConnection} - plus
 * {@link HttpRequestExecutor}). The upstream-proxy path has no replacement; it was
 * removed as a feature, not ported.
 *
 * <p>{@code strictHttps=false}, so neither the server certificate chain nor the
 * hostname is verified - the same posture the pre-migration harness had via
 * {@code createSSLSocketFactory(config, false)}.
 */
public class HttpsClient_test2 {

	public static void main(String[] args) throws Exception {
		ServerConfig config = new ServerConfig();

		boolean strictHttps = false;
		String host = "localhost";
		int port = 443;

		SSLSocketFactory factory = ReverseUtils.createSSLSocketFactory(config, strictHttps);
		Socket plain = new Socket(InetAddress.getByName(host), port);
		SSLSocket socket = ReverseUtils.createLayeredSocket(factory, plain, host, port, strictHttps);
		System.out.println("protocol=" + socket.getSession().getProtocol()
				+ " cipherSuite=" + socket.getSession().getCipherSuite());

		ClientHttpConnection conn = new ClientHttpConnection(config);
		try {
			conn.bind(socket);

			HttpContext context = new BasicHttpContext();
			HttpRequestExecutor executor = new HttpRequestExecutor();

			ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/ex/");
			//core5's HttpRequestExecutor does not add Host itself; that is the job of
			//the RequestTargetHost interceptor, which this bare harness does not run.
			request.setHeader(HttpHeaders.HOST, host);

			ClassicHttpResponse response = executor.execute(request, conn, context);
			try {
				System.out.println(response.getVersion() + " " + response.getCode()
						+ " " + response.getReasonPhrase());
				System.out.println(EntityUtils.toString(response.getEntity()));
			} finally {
				response.close();
			}
		} finally {
			conn.close();
		}
	}
}
