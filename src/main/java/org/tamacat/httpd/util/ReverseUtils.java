/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ReverseUrl;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.core.HttpContextKeys;
import org.tamacat.httpd.core.ssl.KeyStoreType;
import org.tamacat.httpd.exception.ServiceUnavailableException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.IOUtils;
import org.tamacat.util.PropertyUtils;
import org.tamacat.util.ResourceNotFoundException;
import org.tamacat.util.StringUtils;

/**
 * <p>The utility class for reverse proxy.<br>
 *  When customize a request/response header to remove in reverse
 *  to the origin server, create the "reverse-header.properties" in CLASSPATH.
 * <pre>Configuration: reverse-header.properties
 * {@code
 * request.removeHeaders=Content-Length,Transfer-Encoding,Accept-Encoding,...
 * response.removeHeaders=Content-Type,Content-Encoding,Content-Length,...
 * }</pre>
 */
public class ReverseUtils {

	static final Log LOG = LogFactory.getLog(ReverseUtils.class);

	private static Pattern PATTERN = Pattern.compile(
		"<[^<]*\\s+(href|src|action)=('|\")([^('|\")]*)('|\")[^>]*>",
		Pattern.CASE_INSENSITIVE
	);

	private static final String HEADER_PROPERTIES = "reverse-header.properties";
	private static final String DEFAULT_HEADER_PROPERTIES = "org/tamacat/httpd/util/reverse-header.properties";

	private static final Set<String> removeRequestHeaders = new HashSet<>();
	private static final Set<String> removeResponseHeaders = new HashSet<>();

	//Configuration of remove request/response headers.
	static {
		Properties props = null;
		try {
			props = PropertyUtils.getProperties(HEADER_PROPERTIES);
		} catch (ResourceNotFoundException e) {
			props = PropertyUtils.getProperties(DEFAULT_HEADER_PROPERTIES);
		}
		if (props != null) {
			String removeHeaders1 = props.getProperty("request.removeHeaders");
			String[] headers1 = removeHeaders1.split(",");
			for (String h : headers1) {
				removeRequestHeaders.add(deleteCRLF(h.trim()));
			}
			String removeHeaders2 = props.getProperty("response.removeHeaders");
			String[]headers2 = removeHeaders2.split(",");
			for (String h : headers2) {
				removeResponseHeaders.add(deleteCRLF(h.trim()));
			}
		}
	}

	/**
	 * <p>Remove hop-by-hop headers.
	 * @param request
	 */
	public static void removeRequestHeaders(ClassicHttpRequest request) {
		for (String h : removeRequestHeaders) {
			if (LOG.isTraceEnabled()) LOG.trace("remove:"+h);
			request.removeHeaders(h);
		}
	}

	/**
	 * <p>Copy the response headers.
	 * @param targetResponse
	 * @param response
	 */
	public static void copyHttpResponse(ClassicHttpResponse targetResponse, ClassicHttpResponse response) {
		// Remove hop-by-hop headers
		for (String h : removeResponseHeaders) {
			targetResponse.removeHeaders(h);
		}

		//core5 has no StatusLine object on the message: the three fields are set directly.
		response.setVersion(targetResponse.getVersion());
		response.setCode(targetResponse.getCode());
		response.setReasonPhrase(targetResponse.getReasonPhrase());
		Header[] headers = response.getHeaders("Set-Cookie"); //backup Set-Cookie header.
		response.setHeaders(targetResponse.getHeaders()); //clean and reset all headers.
		for (Header h : headers) { //add Set-Cookie headers.
			response.addHeader(h);
		}
	}

	/**
	 * Rewrite a response HTTP version in status line from reuested version.
	 * @param request
	 * @param response
	 * @since 1.0.4
	 */
	public static void rewriteStatusLine(ClassicHttpRequest request, ClassicHttpResponse response) {
		//core5 has no StatusLine object on the message. Only the protocol version is
		//rewritten here; the code and the reason phrase are left exactly as they are,
		//which is what re-setting them from themselves did in 1.5.
		response.setVersion(RequestUtils.getVersion(request));
	}

	/**
	 * <p>Rewrite the Content-Location response headers.
	 * @param response
	 * @param reverseUrl
	 */
	public static void rewriteContentLocationHeader(
			ClassicHttpRequest request, ClassicHttpResponse response, ReverseUrl reverseUrl) {
		Header[] locationHeaders = response.getHeaders("Content-Location");
		response.removeHeaders("Content-Location");
		for (Header location : locationHeaders) {
			String value = deleteCRLF(location.getValue());
			String convertUrl = reverseUrl.getConvertRequestedUrl(value);
			if (convertUrl != null) {
				response.addHeader("Content-Location", convertUrl);
			}
		}
	}

	/**
	 * <p>Rewrite the Location response headers.
	 * @param response
	 * @param reverseUrl
	 */
	public static void rewriteLocationHeader(
			ClassicHttpRequest request, ClassicHttpResponse response, ReverseUrl reverseUrl) {
		Header[] locationHeaders = response.getHeaders("Location");
		response.removeHeaders("Location");
		for (Header location : locationHeaders) {
			String value = deleteCRLF(location.getValue());
			String convertUrl = reverseUrl.getConvertRequestedUrl(value);
			if (convertUrl != null) {
				response.addHeader("Location", convertUrl);
			}
		}
	}

	/**
	 * <p>Rewrite the Set-Cookie response headers.
	 * @param response
	 * @param reverseUrl
	 */
	public static void rewriteSetCookieHeader(
			ClassicHttpRequest request, ClassicHttpResponse response, ReverseUrl reverseUrl) {
		Header[] cookies = response.getHeaders("Set-Cookie");
		ArrayList<String> newValues = new ArrayList<String>();
		for (Header h : cookies) {
			String value = h.getValue();
			String newValue = ReverseUtils.getConvertedSetCookieHeader(
					request, reverseUrl, value);
			if (StringUtils.isNotEmpty(newValue)) {
				newValues.add(newValue);
				response.removeHeader(h);
			}
		}
		for (String newValue : newValues) {
			response.addHeader("Set-Cookie", deleteCRLF(newValue));
		}
	}

	public static void rewriteServerHeader(ClassicHttpResponse response, ReverseUrl reverseUrl) {
		ServiceUrl serviceUrl = reverseUrl.getServiceUrl();
		if (serviceUrl != null) {
			response.setHeader(HttpHeaders.SERVER, deleteCRLF(serviceUrl.getServerConfig().getParam("ServerName")));
		}
	}

	/**
	 * <p>Set the remote IP address to {@code X-Forwarded-For} request header
	 * for origin server.
	 * @param request
	 * @param context
	 * @deprecated 1.3
	 * @see {@code setXForwardedFor(ClassicHttpRequest, HttpContext, boolean, String)}
	 */
	@Deprecated
	public static void setXForwardedFor(ClassicHttpRequest request, HttpContext context) {
		String forward = HeaderUtils.getHeader(request, "X-Forwarded-For"); //for Load balancer
		if (StringUtils.isNotEmpty(forward)) {
			request.setHeader("X-Forwarded-For", deleteCRLF(forward));
		} else {
			request.setHeader("X-Forwarded-For", deleteCRLF(RequestUtils.getRemoteIPAddress(context)));
		}
	}
	
	/**
	 * <p>Set the remote IP address to {@code X-Forwarded-For} request header
	 * for origin server.
	 * @param request
	 * @param context
	 * @param useForwardHeader
	 * @param forwardHeader "X-Forwarded-For"
	 * @since 1.3
	 */
	public static void setXForwardedFor(ClassicHttpRequest request, HttpContext context, boolean useForwardHeader, String forwardHeader) {
		request.setHeader(forwardHeader, deleteCRLF(RequestUtils.getRemoteIPAddress(request, context, useForwardHeader, forwardHeader)));
	}
	
	/**
	 * <p>Set the forwarded Host request header for origin server.
	 * @param request
	 */
	public static void setXForwardedHost(ClassicHttpRequest request) {
		Header hostHeader = request.getFirstHeader(HttpHeaders.HOST);
		if (hostHeader != null) {
			request.setHeader("X-Forwarded-Host", deleteCRLF(hostHeader.getValue()));
		}
	}
	
	/**
	 * <p>Set the forwarded proto request header for origin server.
	 * @param request
	 * @param config
	 * @since 1.4-20190416
	 */
	public static void setXForwardedProto(ClassicHttpRequest request, ServerConfig config) {
		String proto = HeaderUtils.getHeader(request, "X-Forwarded-Proto");
		if (StringUtils.isEmpty(proto)) {
			if (config.useHttps()) {
				request.setHeader("X-Forwarded-Proto", "https");
			} else {
				request.setHeader("X-Forwarded-Proto", "http");
			}
		}
	}

	/**
	 * <p>Set the forwarded port request header for origin server.
	 * @param request
	 * @param config
	 * @since 1.4-20190416
	 */
	public static void setXForwardedPort(ClassicHttpRequest request, ServerConfig config) {
		String port = HeaderUtils.getHeader(request, "X-Forwarded-Port");
		if (StringUtils.isEmpty(port)) {
			int serverPort = config.getPort();
			if (serverPort > 0) {
				request.setHeader("X-Forwarded-Port", String.valueOf(serverPort));
			} else {
				if (config.useHttps()) {
					request.setHeader("X-Forwarded-Port", "443");
				} else {
					request.setHeader("X-Forwarded-Port", "80");
				}
			}
		}
	}

	/**
	 * <p>Set the remote username to request header.
	 * @param request
	 * @param context
	 * @param headerName
	 */
	public static void setReverseProxyAuthorization(ClassicHttpRequest request, HttpContext context, String headerName) {
		if (StringUtils.isNotEmpty(headerName)) {
			Object user = context.getAttribute(HttpContextKeys.REMOTE_USER);
			if (user != null && user instanceof String) {
				request.setHeader(headerName, deleteCRLF((String)user));
			} else {
				request.removeHeaders(headerName);
			}
		}
	}

	/**
	 * <p>Convert backend hostname to original hostname.
	 * @param reverseUrl
	 * @param line cookie header line.
	 * @return converted Set-Cookie response header line.
	 */
	public static String getConvertedSetCookieHeader(
			ClassicHttpRequest request, ReverseUrl reverseUrl, String line) {
		if (line == null) return "";
		String dist = deleteCRLF(reverseUrl.getReverse().getHost());
		URL url = RequestUtils.getRequestURL(request, null);
		if (url == null) return "";
		String src = deleteCRLF(url.getHost());
		return getConvertedSetCookieHeader(
				reverseUrl.getReverse().getPath(),
				reverseUrl.getServiceUrl().getPath(),
				Pattern.compile("domain=" + Pattern.quote(dist), Pattern.CASE_INSENSITIVE)
					.matcher(line).replaceAll("domain=" + src)
		);
	}

	/**
	 * <p>Convert cookie path.
	 * <pre>
	 *   BEFORE: JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; Path=/dist
	 *   AFTER : JSESSIONID=1234567890ABCDEFGHIJKLMNOPQRSTUV; Path=/src
	 * </pre>
	 */
	static String getConvertedSetCookieHeader(String dist, String src, String line) {
		if (line != null) {
			String d = stripEnd(dist, "/");
			String s = stripEnd(src, "/");
			return Pattern.compile(";\\s*Path=" + Pattern.quote(d), Pattern.CASE_INSENSITIVE)
					.matcher(line).replaceAll("; Path=" + s);
		} else {
			return line;
		}
	}

	static String stripEnd(String str, String stripChars) {
		int end;
		if (str == null || (end = str.length()) == 0) {
			return str;
		}
		if (stripChars == null) {
			while ((end != 0) && Character.isWhitespace(str.charAt(end - 1))) {
				end--;
			}
		} else if (stripChars.length() == 0) {
			return str;
		} else {
			while ((end != 0)
					&& (stripChars.indexOf(str.charAt(end - 1)) != -1)) {
				end--;
			}
		}
		return str.substring(0, end);
	}

	private static Charset charset = Charset.forName("UTF-8");
	private static CharsetDecoder decoder = charset.newDecoder();
	private static CharsetEncoder encoder = charset.newEncoder();

	//TODO bug?
	public static ByteBuffer parse(ReverseUrl reverseUrl, ByteBuffer buffer) {
		if (reverseUrl == null) return buffer;
		String src = reverseUrl.getServiceUrl().getPath();
		String dist = reverseUrl.getReverse().getPath();
		ByteBuffer result = null;
		try {
			CharBuffer cb = decoder.decode(buffer);
			Matcher matcher = PATTERN.matcher(cb);
			StringBuffer tmp = new StringBuffer();
			while (matcher.find()) {
				String url = matcher.group(3);
				if (url.startsWith("http"))	continue;
				LOG.trace("URL:" + url);
				// LOG.trace(dist +"->" + src);
				String rev = matcher.group().replaceFirst(dist, src);
				LOG.trace("->URL:" + rev);
				matcher.appendReplacement(tmp, rev.replace("$", "\\$"));
			}
			matcher.appendTail(tmp);
			LOG.trace("URLConvert: " + dist + " -> " + src);
			cb = CharBuffer.wrap(tmp.toString());
			result = encoder.encode(cb);
		} catch (CharacterCodingException e) {
			result = buffer;
		}
		return result;
	}

	/**
	 * delete CRLF
	 * @param str
	 * @since 1.1
	 */
	static String deleteCRLF(String str) {
		if (str != null && str.length() > 0 ) {
			return str.replace("\r", "").replace("\n","");
		} else {
			return str;
		}
	}
	
	/**
	 * Create SSL Socket for connect to backend server.
	 * @param reverseUrl
	 * @param strictHttps true: verify the server certificate chain and the
	 *   hostname. false: verify neither.
	 */
	public static Socket createSSLSocket(ReverseUrl reverseUrl, boolean strictHttps) {
		try {
			InetSocketAddress address = reverseUrl.getTargetAddress();
			SSLSocketFactory factory = createSSLSocketFactory(
				reverseUrl.getServiceUrl().getServerConfig(), strictHttps);
			return createLayeredSocket(factory,
				new Socket(address.getHostName(), address.getPort()),
				address.getHostName(), address.getPort(), strictHttps
			);
		} catch (Exception e) {
			e.printStackTrace();
			LOG.warn(e.getMessage());
			return null;
		}
	}

	/**
	 * <p>Layer TLS over an already connected plain socket.
	 * <p>The handshake is completed before returning, so a certificate or
	 * hostname failure surfaces here rather than on the first read or write.
	 * This matches the behaviour of the replaced httpclient
	 * {@code SSLConnectionSocketFactory.createLayeredSocket(...)}.
	 * @param factory
	 * @param socket the connected plain socket.
	 * @param host the hostname to verify against when strictHttps is true.
	 * @param port
	 * @param strictHttps true: enable hostname verification.
	 * @return the connected {@code SSLSocket} with the handshake completed.
	 * @since 2.0
	 */
	public static SSLSocket createLayeredSocket(SSLSocketFactory factory, Socket socket,
			String host, int port, boolean strictHttps) throws IOException {
		SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
		//The SSLParameters MUST be applied before the handshake starts. The JDK reads
		//endpointIdentificationAlgorithm when the handshake begins, so a setSSLParameters()
		//call made afterwards is silently ignored.
		setEndpointIdentification(sslSocket, strictHttps);
		sslSocket.startHandshake();
		return sslSocket;
	}

	/**
	 * <p>Enable or disable JSSE hostname verification on a socket whose
	 * handshake has not started yet.
	 * @param sslSocket
	 * @param strictHttps true: {@code "HTTPS"} endpoint identification.
	 *   false: no endpoint identification, replacing httpclient's
	 *   {@code NoopHostnameVerifier}.
	 */
	static void setEndpointIdentification(SSLSocket sslSocket, boolean strictHttps) {
		SSLParameters params = sslSocket.getSSLParameters();
		if (strictHttps) {
			params.setEndpointIdentificationAlgorithm("HTTPS");
		} else {
			params.setEndpointIdentificationAlgorithm(null);
		}
		sslSocket.setSSLParameters(params);
	}

	/**
	 * <p>Create the socket factory used for backend TLS connections.
	 * <p>Hostname verification is NOT carried by the factory; it is applied
	 * per socket by {@link #createLayeredSocket}.
	 * @param config
	 * @param isStrict
	 * @return {@code SSLSocketFactory} of the configured {@code SSLContext}.
	 */
	public static SSLSocketFactory createSSLSocketFactory(ServerConfig config, boolean isStrict) {
		return getSSLContext(config, isStrict).getSocketFactory();
	}

	protected static SSLContext getSSLContext(ServerConfig config, boolean strictHttps) {
		String protocol = config.getParam("BackEnd.https.protocol", "TLSv1.2");
		try {
			SSLContextBuilder builder = SSLContextBuilder.create().setProtocol(protocol);

			String useClientAuth = config.getParam("BackEnd.https.clientAuth");
			if (StringUtils.isNotEmpty(useClientAuth) && "true".equalsIgnoreCase(useClientAuth)) {
				String keyStoreFile = config.getParam("BackEnd.https.keyStoreFile");
				String keyStoreType = config.getParam("BackEnd.https.keyStoreType", "PKCS12");
				String keyStorePass = config.getParam("BackEnd.https.keyPassword", "");
				InputStream in = IOUtils.getInputStream(keyStoreFile);

				KeyStore clientKeyStore = KeyStore.getInstance(KeyStoreType.valueOf(keyStoreType).name());
				final char[] pwdChars = keyStorePass.toCharArray();
				clientKeyStore.load(in, pwdChars);

				//DO NOT call loadTrustMaterial() here. Leaving the trust managers empty
				//makes SSLContextBuilder pass null to SSLContext.init(), which selects the
				//JDK default trust managers, so the server certificate chain is validated
				//for clientAuth=true regardless of strictHttps. Adding the permissive
				//TrustStrategy below to this branch would silently disable that validation.
				return builder
					.setKeyManagerFactoryAlgorithm("SunX509")
					.loadKeyMaterial(clientKeyStore, pwdChars, null)
					.build();
			}
			builder.setSecureRandom(new SecureRandom());
			if (strictHttps == false) {
				//Accept any server certificate chain, replacing the hand-written
				//all-permissive X509TrustManager.
				builder.loadTrustMaterial(new TrustStrategy() {
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) {
						return true;
					}
				});
			}
			return builder.build();
		} catch (Exception e) {
			throw new ServiceUnavailableException(e.getMessage(), e);
		}
	}
}
