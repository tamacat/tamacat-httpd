package org.tamacat.httpd.core;

import java.net.ServerSocket;
import java.util.Properties;

import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.handler.TamacatHttpServerRequestHandler;
import org.tamacat.httpd.handler.UriHttpRequestHandlerMapper;
import org.tamacat.httpd.core.util.IOUtils;
import org.tamacat.httpd.core.util.PropertyUtils;

public class WorkerThread_test {

	DefaultWorker thread;

	public void testWorkerThread() throws Exception {
		Properties props = PropertyUtils.getProperties("server.properties");
		ServerConfig serverConfig = new ServerConfig(props);
//		HttpParamsBuilder paramsBuilder = new HttpParamsBuilder();
//		paramsBuilder.socketTimeout(serverConfig.getSocketTimeout())
//			  .socketBufferSize(serverConfig.getSocketBufferSize())
//			  .originServer(serverConfig.getParam("ServerName"));

		HttpProcessorBuilder procBuilder = new HttpProcessorBuilder();

		//default interceptors
		procBuilder.addInterceptor(new ResponseDate());
		procBuilder.addInterceptor(new ResponseServer());
		procBuilder.addInterceptor(new ResponseContent());
		procBuilder.addInterceptor(new ResponseConnControl());

		//core5 has no doService() extension point: DefaultHttpService is replaced by
		//impl.io.HttpService with a TamacatHttpServerRequestHandler injected into it.
		HttpService service = new HttpService(
				procBuilder.build(),
				new TamacatHttpServerRequestHandler(new UriHttpRequestHandlerMapper()),
				new DefaultConnectionReuseStrategy(), null
		);

		ServerSocket serversocket = new ServerSocket(8080);
		thread = new DefaultWorker();
		thread.setHttpService(service);
		thread.setServerConfig(serverConfig);
		thread.setSocket(serversocket.accept());
		new Thread(thread).start();
		thread.isClosed();
		IOUtils.close(serversocket);
	}

}
