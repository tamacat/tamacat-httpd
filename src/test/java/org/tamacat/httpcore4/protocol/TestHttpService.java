/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.tamacat.httpcore4.protocol;


import org.tamacat.httpcore4.HttpEntityEnclosingRequest;
import org.tamacat.httpcore4.HttpException;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpStatus;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.MethodNotSupportedException;
import org.tamacat.httpcore4.ProtocolException;
import org.tamacat.httpcore4.UnsupportedHttpVersionException;
import org.tamacat.httpcore4.entity.InputStreamEntity;
import org.tamacat.httpcore4.fakes.CallLog;
import org.tamacat.httpcore4.fakes.FakeConnectionReuseStrategy;
import org.tamacat.httpcore4.fakes.FakeHttpExpectationVerifier;
import org.tamacat.httpcore4.fakes.FakeHttpProcessor;
import org.tamacat.httpcore4.fakes.FakeHttpRequestHandler;
import org.tamacat.httpcore4.fakes.FakeHttpRequestHandlerMapper;
import org.tamacat.httpcore4.fakes.FakeHttpResponseFactory;
import org.tamacat.httpcore4.fakes.FakeHttpServerConnection;
import org.tamacat.httpcore4.fakes.RecordingInputStream;
import org.tamacat.httpcore4.message.BasicHttpEntityEnclosingRequest;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestHttpService {

    @Test
    public void testInvalidInitialization() throws Exception {
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        try {
            new HttpService(
                    null,
                    connReuseStrategy,
                    responseFactory,
                    handlerResolver);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBasicExecution() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        httprocessor.calls.assertCalledOnce("process", request, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testBasicExecutionHTTP10() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        responseFactory.calls.assertCalledOnce("newHttpResponse", HttpVersion.HTTP_1_1, 200, context);
    }

    @Test
    public void testBasicProtocolDowngrade() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("GET", "/", new HttpVersion(20, 45));
        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        responseFactory.calls.assertCalledOnce("newHttpResponse", HttpVersion.HTTP_1_1, 200, context);
    }

    @Test
    public void testExecutionEntityEnclosingRequest() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final RecordingInputStream inStream = new RecordingInputStream();
        final InputStreamEntity entity = new InputStreamEntity(inStream, -1);
        request.setEntity(entity);

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        conn.calls.assertCalledOnce("receiveRequestEntity", request);
        httprocessor.calls.assertCalledOnce("process", request, context);
        inStream.calls.assertCalledOnce("close");
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinue() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final RecordingInputStream inStream = new RecordingInputStream();
        final InputStreamEntity entity = new InputStreamEntity(inStream, -1);
        request.setEntity(entity);

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse resp100 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 100, context).thenReturn(resp100);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        conn.calls.assertCalledOnce("sendResponseHeader", resp100);
        conn.calls.assertCalledOnce("receiveRequestEntity", request);
        httprocessor.calls.assertCalledOnce("process", request, context);
        inStream.calls.assertCalledOnce("close");
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalled(2, "flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testExecutionEntityEnclosingRequestCustomExpectationVerifier() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            @Override
            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            }

        };

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final RecordingInputStream inStream = new RecordingInputStream();
        final InputStreamEntity entity = new InputStreamEntity(inStream, -1);
        request.setEntity(entity);

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 100, context).thenReturn(response);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());

        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertNeverCalled("receiveRequestEntity", request);
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testExecutionExceptionInCustomExpectationVerifier() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpExpectationVerifier expectationVerifier = new FakeHttpExpectationVerifier();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final RecordingInputStream inStream = new RecordingInputStream();
        final InputStreamEntity entity = new InputStreamEntity(inStream, -1);
        request.setEntity(entity);

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse resp100 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 100, context).thenReturn(resp100);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_0, 500, context).thenReturn(response);
        expectationVerifier.calls.when("verify", request, resp100, context).thenThrow(new HttpException("Oopsie"));
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());

        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertNeverCalled("receiveRequestEntity", request);
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testMethodNotSupported() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_0, 500, context).thenReturn(error);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        requestHandler.calls.when("handle", request, response, context)
                .thenThrow(new MethodNotSupportedException("whatever"));
        connReuseStrategy.calls.when("keepAlive", error, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(error, context.getResponse());

        Assertions.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, error.getStatusLine().getStatusCode());

        conn.calls.assertCalledOnce("sendResponseHeader", error);
        httprocessor.calls.assertCalledOnce("process", error, context);
        conn.calls.assertCalledOnce("sendResponseHeader", error);
        conn.calls.assertCalledOnce("sendResponseEntity", error);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testUnsupportedHttpVersionException() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_0, 500, context).thenReturn(error);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        requestHandler.calls.when("handle", request, response, context)
                .thenThrow(new UnsupportedHttpVersionException());
        connReuseStrategy.calls.when("keepAlive", error, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(error, context.getResponse());

        Assertions.assertEquals(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, error.getStatusLine().getStatusCode());

        conn.calls.assertCalledOnce("sendResponseHeader", error);
        httprocessor.calls.assertCalledOnce("process", error, context);
        conn.calls.assertCalledOnce("sendResponseHeader", error);
        conn.calls.assertCalledOnce("sendResponseEntity", error);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testProtocolException() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_0, 500, context).thenReturn(error);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        requestHandler.calls.when("handle", request, response, context)
                .thenThrow(new ProtocolException("oh, this world is wrong"));
        connReuseStrategy.calls.when("keepAlive", error, context).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(error, context.getResponse());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, error.getStatusLine().getStatusCode());

        conn.calls.assertCalledOnce("sendResponseHeader", error);
        httprocessor.calls.assertCalledOnce("process", error, context);
        conn.calls.assertCalledOnce("sendResponseHeader", error);
        conn.calls.assertCalledOnce("sendResponseEntity", error);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("close");
    }

    @Test
    public void testConnectionKeepAlive() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());

        httprocessor.calls.assertCalledOnce("process", request, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertCalledOnce("sendResponseEntity", response);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertNeverCalled("close");
    }

    @Test
    public void testNoContentResponse() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());

        httprocessor.calls.assertCalledOnce("process", response, context);
        requestHandler.calls.assertCalledOnce("handle", request, response, context);

        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertNeverCalled("sendResponseEntity", CallLog.ANY);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertNeverCalled("close");
    }

    @Test
    public void testResponseToHead() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeConnectionReuseStrategy connReuseStrategy = new FakeConnectionReuseStrategy();
        final FakeHttpResponseFactory responseFactory = new FakeHttpResponseFactory();
        final FakeHttpRequestHandlerMapper handlerResolver = new FakeHttpRequestHandlerMapper();
        final FakeHttpRequestHandler requestHandler = new FakeHttpRequestHandler();

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final FakeHttpServerConnection conn = new FakeHttpServerConnection();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");

        conn.calls.when("receiveRequestHeader").thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        responseFactory.calls.when("newHttpResponse", HttpVersion.HTTP_1_1, 200, context).thenReturn(response);
        handlerResolver.calls.when("lookup", request).thenReturn(requestHandler);
        connReuseStrategy.calls.when("keepAlive", response, context).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());

        httprocessor.calls.assertCalledOnce("process", response, context);
        requestHandler.calls.assertCalledOnce("handle", request, response, context);

        conn.calls.assertCalledOnce("sendResponseHeader", response);
        conn.calls.assertNeverCalled("sendResponseEntity", CallLog.ANY);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertNeverCalled("close");
    }

}
