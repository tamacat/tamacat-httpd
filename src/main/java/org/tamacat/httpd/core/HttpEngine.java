/*
 * Copyright (c) 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.management.MXBean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.jmx.BasicCounter;
import org.tamacat.httpd.core.jmx.JMXReloadableHttpd;
import org.tamacat.httpd.core.jmx.JMXServer;
import org.tamacat.httpd.core.ssl.SSLContextCreator;
import org.tamacat.httpd.core.ssl.SSLSNIContextCreator;
import org.tamacat.httpd.filter.HttpResponseConnControl;
import org.tamacat.httpd.handler.DefaultHttpService;
import org.tamacat.httpd.handler.HostRequestHandlerMapper;
import org.tamacat.io.RuntimeIOException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.CollectionUtils;
import org.tamacat.util.PropertyUtils;

/**
 * <p>It is implements of the multi-thread server.
 */
@MXBean
public class HttpEngine implements JMXReloadableHttpd, Runnable {

	static final Log LOG = LogFactory.getLog(HttpEngine.class);

	protected String propertiesName = "server.properties";

	protected ServerConfig serverConfig;

	protected SSLContextCreator sslContextCreator;
	protected ServerSocket serverSocket;
	protected String[] httpsSupportProtocols;
	protected WorkerExecutor workerExecutor = new DefaultWorkerExecutor();

	protected BasicCounter counter;

	protected List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
	protected List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();

	protected JMXServer jmx = new JMXServer(this);
	protected ClassLoader loader;

	/**
	 * <p>Start the http server.
	 */
	@Override
	public void startHttpd() {
		//Initalize engine.
		init();

		LOG.info("Listen: " + serverConfig.getPort());
		serverSocket = createServerSocket();
		while (!Thread.interrupted()) {
			try {
				workerExecutor.execute(serverSocket.accept());
			} catch (InterruptedIOException e) {
				counter.error();
				LOG.error(e.getMessage());
				break;
			} catch (IOException e) {
				counter.error();
				LOG.warn(e.getMessage());
				if (serverSocket.isClosed()) { //for stop()
					break;
				}
			} catch (Exception e) {
				counter.error();
				LOG.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public void stopHttpd() {
		try {
			if (serverSocket != null) serverSocket.close();
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		} finally {
			if (workerExecutor != null) workerExecutor.shutdown();
		}
	}

	@Override
	public void restartHttpd() {
		for (;;) {
			if (counter.getActiveConnections() == 0) {
				stopHttpd();
				startHttpd();
				break;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOG.error(e.getMessage());
			}
		}
	}

	/**
	 * <p>Set the {@link SSLContextCreator},
	 * when customize the configration of https (SSL/TSL).
	 * When I did not set it, it is generated by a {@code createSecureServerSocket}.
	 * @param sslContextCreator
	 */
	public void setSslContextCreator(SSLContextCreator sslContextCreator) {
		this.sslContextCreator = sslContextCreator;
	}

	/**
	 * Set the WorkerExecutor.
	 * @param WorkerExecutor
	 * @sinse 1.1
	 */
	public void setWorkerExecutor(WorkerExecutor workerExecutor) {
		this.workerExecutor = workerExecutor;
	}

	public void setHttpInterceptor(HttpRequestInterceptor interceptor) {
		requestInterceptors.add(interceptor);
	}

	public void setHttpInterceptor(HttpResponseInterceptor interceptor) {
		responseInterceptors.add(interceptor);
	}

	/**
	 * <p>This method called by {@link #start}.
	 */
	protected void init() {
		if (serverConfig == null) {
			Properties props = PropertyUtils.getProperties(propertiesName);
			serverConfig = new ServerConfig(props);
			if (this.sslContextCreator != null) {
				this.sslContextCreator.setServerConfig(serverConfig);
			}
		}
		counter = new BasicCounter(serverConfig);
		jmx.setServerConfig(serverConfig);
		workerExecutor.setServerConfig(serverConfig);

		HttpProcessorBuilder procBuilder = new HttpProcessorBuilder()
			.addInterceptor(new ResponseDate()).addInterceptor(new ResponseServer())
			.addInterceptor(new ResponseContent())
			.addInterceptor(new HttpResponseConnControl());

		//add interceptors
		for (HttpRequestInterceptor interceptor : requestInterceptors) {
			procBuilder.addInterceptor(interceptor);
		}
		for (HttpResponseInterceptor interceptor : responseInterceptors) {
			procBuilder.addInterceptor(interceptor);
		}
		DefaultHttpService service = new DefaultHttpService(
			procBuilder, new KeepAliveConnReuseStrategy(serverConfig),
			new DefaultHttpResponseFactory(), null, null);

		if (jmx.isMXServerStarted() == false) {
			registerMXServer();
		}

		String componentsXML = serverConfig.getParam("components.file", "components.xml");
		HostRequestHandlerMapper hostResolver = new HostRequestHandlerMapper().create(serverConfig, componentsXML);
		service.setHostHandlerResolver(hostResolver);
		workerExecutor.setHttpService(service);
	}

	protected ServerSocket createServerSocket() {
		ServerSocket serverSocket = null;
		try {
			//setup the server port.
			int port = serverConfig.getPort();
			if (serverConfig.useHttps()) {
				serverSocket = createSecureServerSocket(port);
				if (serverConfig.useClientAuth() && serverSocket instanceof SSLServerSocket) {
					((SSLServerSocket)serverSocket).setNeedClientAuth(true);
				}
			} else {
				serverSocket = new ServerSocket(port);
			}
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
		return serverSocket;
	}

	public void registerMXServer() {
		if (jmx.isMXServerStarted() == false) {
			counter.register();
			jmx.registerMXServer();
		}
	}

	public void unregisterMXServer() {
		counter.unregister();
		jmx.unregisterMXServer();
	}

	/**
	 * <p>Create the secure {@link ServerSocket}.
	 * @param port HTTPS listen port.
	 * @return created the {@link ServerSocket}
	 * @throws IOException
	 */
	protected ServerSocket createSecureServerSocket(int port) throws IOException {
		if (sslContextCreator == null) {
			sslContextCreator = new SSLSNIContextCreator(serverConfig);
		}
		SSLContext ctx = sslContextCreator.getSSLContext();
		SSLServerSocket socket = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);
		setHttpsSupportProtocols(socket);
		setHttpsSupportCipherSuites(socket);
		return socket;
	}

	/**
	 * <p>Set the Https Support Protocols
	 * @param socket
	 * @since 1.2
	 */
	protected void setHttpsSupportProtocols(SSLServerSocket socket) {
		if (httpsSupportProtocols == null) {
			httpsSupportProtocols = getServerConfig().getHttpsSupportProtocols();
		}
		if (httpsSupportProtocols.length > 0) {
			LOG.debug("httpsSupportProtocols="+String.join(",", httpsSupportProtocols));
			socket.setEnabledProtocols(httpsSupportProtocols);
		}
	}
	
	/**
	 * <p>Set the Https Support CiperSuites
	 * @param socket
	 * @since 1.4
	 */
	protected void setHttpsSupportCipherSuites(SSLServerSocket socket) {
		List<String> cipherSuitesList = CollectionUtils.newArrayList();
		String[] cipherSuites = getServerConfig().getSupportCipherSuites();
		if (cipherSuites.length == 0) {
			//System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");
			
			//Get the default cipher suites.
			//Delete: *_DES_*, *_3DES_*, *_RC4_*,TLS_RSA_WITH_*,*_CBC_SHA*
			cipherSuites = socket.getSSLParameters().getCipherSuites();
			for (String cipher : Arrays.asList(cipherSuites)) {
				if (cipher.startsWith("TLS_RSA_WITH_")) continue; //Forward Secrecy
				if (cipher.indexOf("_3DES_")>=0) continue; //CVE-2016-2183(Sweet32)
				if (cipher.indexOf("_DES_")>=0) continue;
				if (cipher.indexOf("_RC4_")>=0) continue;
				if (cipher.indexOf("_CBC_SHA")>=0) continue; //WEAK
				cipherSuitesList.add(cipher);
			}
		} else {
			cipherSuitesList.addAll(Arrays.asList(cipherSuites));
		}
		SSLParameters params = new SSLParameters();
		params.setUseCipherSuitesOrder(true); //Has server cipher order: Yes
		params.setCipherSuites(cipherSuitesList.toArray(new String[cipherSuitesList.size()]));
		socket.setSSLParameters(params);
	}
	
	public void reload() {
		init();
		LOG.info("reloaded.");
	}

	public int getMaxServerThreads() {
		return serverConfig.getMaxThreads();
	}

	public void setMaxServerThreads(int max) {
		serverConfig.setParam("MaxServerThreads",String.valueOf(max));
	}

	@Override
	public void run() {
		startHttpd();
	}

	public String getPropertiesName() {
		return propertiesName;
	}

	public void setPropertiesName(String propertiesName) {
		this.propertiesName = propertiesName;
	}

	public ServerConfig getServerConfig() {
		return serverConfig;
	}

	public void setServerConfig(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
	}

	public void setClassLoader(ClassLoader loader) {
		this.loader = loader;
	}

	public ClassLoader getClassLoader() {
		if (loader == null) return Thread.currentThread().getContextClassLoader();
		else return loader;
	}
}