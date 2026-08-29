package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.core.ssl.DefaultSSLContextCreator;
import org.tamacat.httpd.core.util.PropertyUtils;

public class HttpEngineTest {

	HttpEngine engine;

	@BeforeEach
	public void setUp() throws Exception {
		engine = new HttpEngine();
		engine.setWorkerExecutor(new DefaultWorkerExecutor());
	}

	@AfterEach
	public void tearDown() throws Exception {
		engine.stopHttpd();
	}

	@Test
	public void testInit() {
		engine.init();
	}

	@Test
	public void testStartHttpd() {

	}

	@Test
	public void testStopHttpd() {

	}

	@Test
	public void testRestartHttpd() {

	}

	@Test
	public void testCreateSecureServerSocket() throws IOException {
		ServerConfig serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		engine.setServerConfig(serverConfig);
		//ServerSocket socket = engine.createSecureServerSocket(8080);
		//socket.close();

		DefaultSSLContextCreator sslContextCreator = new DefaultSSLContextCreator(serverConfig);
		engine.setSslContextCreator(sslContextCreator);

		//socket = engine.createSecureServerSocket(8080);
		//socket.close();
	}

	@Test
	public void testSetHttpResponseInterceptor() {
		HttpResponseInterceptor interceptor = new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, EntityDetails entity, HttpContext context)
					throws HttpException, IOException {
			}
		};
		engine.setHttpInterceptor(interceptor);
	}

	@Test
	public void testRegisterMXServer() {
		//engine.registerMXServer();
		//engine.unregisterMXServer();
	}

	@Test
	public void testReload() {
		//engine.reload();
	}

	@Test
	public void testGetMaxServerThreads() {
		ServerConfig serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		engine.setServerConfig(serverConfig);
		engine.setMaxServerThreads(3);
		assertEquals(3, engine.getMaxServerThreads());
	}

	@Test
	public void testGetPropertiesName() {
		engine.setPropertiesName("server.properties");
		assertEquals("server.properties", engine.getPropertiesName());
	}

	@Test
	public void testGetServerConfig() {
		ServerConfig serverConfig = new ServerConfig(PropertyUtils.getProperties("server.properties"));
		engine.setServerConfig(serverConfig);
		assertSame(serverConfig, engine.getServerConfig());
	}

	@Test
	public void testGetClassLoader() {
		assertNotNull(engine.getClassLoader());

		ClassLoader loader = getClass().getClassLoader();
		engine.setClassLoader(loader);
		assertSame(loader, engine.getClassLoader());
	}

}
