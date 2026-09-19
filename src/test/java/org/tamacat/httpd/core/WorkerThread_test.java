package org.tamacat.httpd.core;

import java.net.ServerSocket;
import java.util.Properties;

import org.tamacat.httpcore4.impl.DefaultConnectionReuseStrategy;
import org.tamacat.httpcore4.impl.DefaultHttpResponseFactory;
import org.tamacat.httpcore4.protocol.ResponseConnControl;
import org.tamacat.httpcore4.protocol.ResponseContent;
import org.tamacat.httpcore4.protocol.ResponseDate;
import org.tamacat.httpcore4.protocol.ResponseServer;
import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.handler.DefaultHttpService;
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

		DefaultHttpService service = new DefaultHttpService(
				procBuilder, new DefaultConnectionReuseStrategy(),
				new DefaultHttpResponseFactory(), null, null
		);
		//DefaultHttpService service = new DefaultHttpService(
		//		procBuilder, new DefaultConnectionReuseStrategy(),
		//   	new DefaultHttpResponseFactory(), null, null,
		//    	paramsBuilder.buildParams());

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
