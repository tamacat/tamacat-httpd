package org.tamacat.httpd.core;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.util.Timeout;
import org.tamacat.httpd.config.ServerConfig;

/**
 * Get the backend server configuration parameters from the
 *
 * server.properties.
 *  default value is:
 *  - BackEndSocketBufferSize=8192
 *  - BackEndSocketTimeout=5000
 *
 * <p>Migration notes (2.0):
 * <ul>
 *  <li>httpcore 4.4's {@code org.apache.http.config.MessageConstraints} and the bare
 *      {@code int buffersize} constructors are gone. HttpComponents Core 5.x carries both
 *      the buffer size and the message constraints in a single
 *      {@link Http1Config} (15.9). Only the settings this class actually configured are
 *      carried over - the buffer size; every other {@code Http1Config} value stays at
 *      core5's default.</li>
 *  <li>{@code setSocketTimeout} now takes a {@link Timeout} rather than an {@code int}.</li>
 * </ul>
 *
 * <p><strong>{@code BackEndConnectionTimeout} is still not implemented.</strong> It is
 * listed among {@code ServerConfig}'s known keys and appears in the sample
 * {@code server.properties}, but no code reads it: the backend socket is created with
 * {@code SocketFactory#createSocket(host, port)}, which uses the operating system's
 * default connect timeout. This is a separate defect from the
 * {@code BackEndSocketTimeout} one fixed above, and fixing it means changing socket
 * creation in {@code ReverseProxyHandler#createSocket} and {@code ReverseUtils}
 * (including the TLS path), which is out of scope for the 2.0 type migration.
 */
public class ClientHttpConnection extends DefaultBHttpClientConnection {

	long connStartTime = System.currentTimeMillis();
	long lastAccessTime = System.currentTimeMillis();

	/**
	 * The {@code BackEndSocketTimeout} to apply once the socket is bound.
	 * {@code null} when this connection was not built from a {@link ServerConfig}.
	 * @since 2.0
	 */
	private final Timeout backEndSocketTimeout;

	public ClientHttpConnection(ServerConfig serverConfig) {
		super(http1Config(serverConfig.getParam("BackEndSocketBufferSize", 8192)));
		//DEFECT FIX (2.0): BackEndSocketTimeout was never applied.
		//1.5 called setSocketTimeout(...) here in the constructor, but
		//BHttpConnectionBase#setSocketTimeout is a no-op while the connection is
		//unbound - and bind(Socket) happens later, in ReverseProxyHandler. The value is
		//therefore remembered here and applied in bind(Socket) below.
		this.backEndSocketTimeout = Timeout.ofMilliseconds(
			serverConfig.getParam("BackEndSocketTimeout", 5000));
	}

	public ClientHttpConnection(int buffersize) {
		super(http1Config(buffersize));
		this.backEndSocketTimeout = null;
	}

	public ClientHttpConnection(Http1Config http1Config, CharsetDecoder chardecoder, CharsetEncoder charencoder) {
		super(http1Config, chardecoder, charencoder);
		this.backEndSocketTimeout = null;
	}

	public ClientHttpConnection(Http1Config http1Config, CharsetDecoder chardecoder, CharsetEncoder charencoder,
			ContentLengthStrategy incomingContentStrategy, ContentLengthStrategy outgoingContentStrategy,
			HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
			HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
		super(http1Config, chardecoder, charencoder, incomingContentStrategy, outgoingContentStrategy,
				requestWriterFactory, responseParserFactory);
		this.backEndSocketTimeout = null;
	}

	static Http1Config http1Config(int buffersize) {
		return Http1Config.custom().setBufferSize(buffersize).build();
	}

	@Override
	public void bind(final Socket socket) throws IOException {
		connStartTime = System.currentTimeMillis();
		lastAccessTime = connStartTime;
		super.bind(socket);
		//Must come after super.bind(socket): setSocketTimeout does nothing while unbound.
		if (backEndSocketTimeout != null) {
			setSocketTimeout(backEndSocketTimeout);
		}
	}

	public long getConnectionStartTime() {
		return connStartTime;
	}

	public long getLastAccessTime() {
		long last = lastAccessTime;
		lastAccessTime = System.currentTimeMillis();
		return last;
	}
}
