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

import java.io.IOException;

import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpEntityEnclosingRequest;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.ProtocolException;
import org.tamacat.httpcore4.entity.ByteArrayEntity;
import org.tamacat.httpcore4.fakes.CallLog;
import org.tamacat.httpcore4.fakes.FakeHttpClientConnection;
import org.tamacat.httpcore4.fakes.FakeHttpProcessor;
import org.tamacat.httpcore4.message.BasicHttpEntityEnclosingRequest;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.tamacat.httpcore4.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestHttpRequestExecutor {


    @Test
    public void testInvalidInput() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(null, conn, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, null, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, conn, null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doSendRequest(null, conn, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doSendRequest(request, null, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doSendRequest(request, conn, null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doReceiveResponse(null, conn, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doReceiveResponse(request, null, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.doReceiveResponse(request, conn, null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(null, httprocessor, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, null, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, httprocessor, null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(null, httprocessor, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, null, context);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, httprocessor, null);
            Assertions.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBasicExecution() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertCalledOnce("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());
    }

    @Test
    public void testExecutionSkipIntermediateResponses() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader")
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "OK"), new BasicHttpResponse(HttpVersion.HTTP_1_1, 101, "OK"), new BasicHttpResponse(HttpVersion.HTTP_1_1, 102, "OK"), new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalled(4, "receiveResponseHeader");
        conn.calls.assertCalled(1, "receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);

        Assertions.assertSame(conn, context.getConnection());
        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());
    }

    @Test
    public void testExecutionNoResponseBody() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 204, "OK"));

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertNeverCalled("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
    }

    @Test
    public void testExecutionHead() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertNeverCalled("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
    }

    @Test
    public void testExecutionEntityEnclosingRequest() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
//        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final HttpEntity entity = new ByteArrayEntity(new byte[0]);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("sendRequestEntity", request);
        conn.calls.assertCalledOnce("flush");
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertCalledOnce("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueSuccess() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final HttpEntity entity = new ByteArrayEntity(new byte[0]);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader")
                .thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue"), new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        conn.calls.when("isResponseAvailable", CallLog.ANY).thenReturn(Boolean.TRUE);

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("sendRequestEntity", request);
        conn.calls.assertCalled(2, "flush");
        conn.calls.assertCalledOnce("isResponseAvailable", 3000);
        conn.calls.assertCalled(2, "receiveResponseHeader");
        conn.calls.assertCalledOnce("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);

        Assertions.assertEquals(Boolean.TRUE, context.isRequestSent());
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueFailure() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final HttpEntity entity = new ByteArrayEntity(new byte[0]);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 402, "OK"));
        conn.calls.when("isResponseAvailable", CallLog.ANY).thenReturn(Boolean.TRUE);

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertNeverCalled("sendRequestEntity", request);
        conn.calls.assertCalled(2, "flush");
        conn.calls.assertCalledOnce("isResponseAvailable", 3000);
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertCalledOnce("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
    }

    @Test
    public void testExecutionEntityEnclosingRequestUnsupportedIntermediateResponse() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final HttpEntity entity = new ByteArrayEntity(new byte[0]);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 101, "OK"));
        conn.calls.when("isResponseAvailable", CallLog.ANY).thenReturn(Boolean.TRUE);

        try {
            executor.execute(request, conn, context);
            Assertions.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            conn.calls.assertCalledOnce("close");
            Assertions.assertEquals(Boolean.FALSE, context.isRequestSent());
        }
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueNoResponse() throws Exception {
        final FakeHttpProcessor httprocessor = new FakeHttpProcessor();
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final HttpEntity entity = new ByteArrayEntity(new byte[0]);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", request, context);

        conn.calls.when("receiveResponseHeader").thenReturn(new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"));
        conn.calls.when("isResponseAvailable", CallLog.ANY).thenReturn(Boolean.FALSE);

        final HttpResponse response = executor.execute(request, conn, context);
        conn.calls.assertCalledOnce("sendRequestHeader", request);
        conn.calls.assertCalledOnce("sendRequestEntity", request);
        conn.calls.assertCalled(2, "flush");
        conn.calls.assertCalledOnce("isResponseAvailable", 3000);
        conn.calls.assertCalledOnce("receiveResponseHeader");
        conn.calls.assertCalledOnce("receiveResponseEntity", response);

        executor.postProcess(response, httprocessor, context);
        httprocessor.calls.assertCalledOnce("process", response, context);
    }

    @Test
    public void testExecutionIOException() throws Exception {
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        conn.calls.when("sendRequestHeader", request).thenThrow(new IOException("Oopsie"));
        try {
            executor.execute(request, conn, context);
            Assertions.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            conn.calls.assertCalledOnce("close");
            Assertions.assertEquals(Boolean.FALSE, context.isRequestSent());
        }
    }

    @Test
    public void testExecutionRuntimeException() throws Exception {
        final FakeHttpClientConnection conn = new FakeHttpClientConnection();
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        conn.calls.when("receiveResponseHeader").thenThrow(new RuntimeException("Oopsie"));
        try {
            executor.execute(request, conn, context);
            Assertions.fail("IOException should have been thrown");
        } catch (final RuntimeException ex) {
            conn.calls.assertCalledOnce("close");
            Assertions.assertEquals(Boolean.TRUE, context.isRequestSent());
        }
    }

}
