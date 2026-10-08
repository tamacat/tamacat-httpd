package org.tamacat.httpd.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.hc.core5.http.io.SessionInputBuffer;
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
 *
 * <p><strong>Response content tracking (2.0).</strong> A backend connection may only
 * be reused once the body of its previous response has been read to the end. Every
 * response body this connection creates is therefore tracked, and
 * {@link #isResponseContentPending()} reports whether the latest one is still
 * unread. An exception on the proxy side can drop a received response before its
 * entity is ever read or closed; the connection is then still open, and
 * {@link #isStale()} is false because the unread body is available data. Reusing it
 * would make the next exchange parse that leftover body as its status line.
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

	/**
	 * The body of the latest response received on this connection, or {@code null}
	 * when no response with a body has been received yet.
	 * @since 2.0
	 */
	private volatile ResponseContentInputStream responseContent;

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

	/**
	 * Returns true while the body of the latest response received on this
	 * connection has not been read to the end, so its remaining bytes may still be
	 * on the wire. Such a connection must not be reused for another request.
	 * <p>Stays true for good once a read of the body failed, and when a
	 * close-delimited body was closed before its end (it ends only when the
	 * backend closes the connection).
	 * @since 2.0
	 */
	public boolean isResponseContentPending() {
		ResponseContentInputStream content = responseContent;
		return content != null && !content.isComplete();
	}

	/**
	 * Tracks every response body this connection creates. {@code BHttpConnectionBase}
	 * calls this only from {@code receiveResponseEntity} on a client connection.
	 * An empty body ({@code len == 0}) leaves nothing on the wire and is not tracked.
	 * @since 2.0
	 */
	@Override
	protected InputStream createContentInputStream(long len, SessionInputBuffer buffer, InputStream inputStream) {
		InputStream content = super.createContentInputStream(len, buffer, inputStream);
		if (len == 0) {
			return content;
		}
		//Content-Length and chunked bodies have a known end; a close-delimited body
		//(neither header) ends only when the backend closes the connection.
		boolean delimited = len > 0 || len == ContentLengthStrategy.CHUNKED;
		ResponseContentInputStream tracked = new ResponseContentInputStream(content, delimited);
		responseContent = tracked;
		return tracked;
	}

	/**
	 * A response body that records whether it was read to the end.
	 * <p>{@link #close()} keeps core5's behaviour of reading a delimited body to the
	 * end (core5's {@code ContentLengthInputStream} and {@code ChunkedInputStream}
	 * do the same on close), so a closed delimited body leaves the connection
	 * reusable. The draining is done through this stream, so the end is observed
	 * here rather than assumed from the delegate's close.
	 * @since 2.0
	 */
	static final class ResponseContentInputStream extends FilterInputStream {

		static final int DRAIN_BUFFER_SIZE = 2048;

		private final boolean delimited;
		private volatile boolean eof;
		private volatile boolean failed;
		private boolean closed;

		ResponseContentInputStream(InputStream in, boolean delimited) {
			super(in);
			this.delimited = delimited;
		}

		/**
		 * True once a read returned end of stream, unless a read failed before.
		 * A failure makes the body pending for good: core5's {@code ChunkedInputStream}
		 * reports end of stream after some failures although the body did not end
		 * there - it sets its eof flag before throwing on a truncated chunk, and
		 * before reading the trailers, which may still fail.
		 */
		boolean isComplete() {
			return eof && !failed;
		}

		@Override
		public int read() throws IOException {
			try {
				int b = in.read();
				if (b == -1) {
					eof = true;
				}
				return b;
			} catch (IOException | RuntimeException e) {
				failed = true;
				throw e;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			try {
				int n = in.read(b, off, len);
				if (n == -1) {
					eof = true;
				}
				return n;
			} catch (IOException | RuntimeException e) {
				failed = true;
				throw e;
			}
		}

		@Override
		public void close() throws IOException {
			if (closed) {
				return;
			}
			closed = true;
			try {
				//A close-delimited body is not drained: its end is the backend closing
				//the connection, so the connection stays pending and is replaced.
				//Neither is a body whose read already failed: it stays pending anyway.
				if (delimited && !eof && !failed) {
					byte[] buffer = new byte[DRAIN_BUFFER_SIZE];
					while (read(buffer, 0, buffer.length) != -1) {
						//discard the rest of the body.
					}
				}
			} finally {
				in.close();
			}
		}
	}
}
