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
package org.tamacat.httpcore4.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.tamacat.httpcore4.Consts;
import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpEntityEnclosingRequest;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.config.MessageConstraints;
import org.tamacat.httpcore4.entity.ContentType;
import org.tamacat.httpcore4.entity.StringEntity;
import org.tamacat.httpcore4.fakes.FakeSocket;
import org.tamacat.httpcore4.impl.entity.LaxContentLengthStrategy;
import org.tamacat.httpcore4.impl.entity.StrictContentLengthStrategy;
import org.tamacat.httpcore4.impl.io.DefaultHttpRequestWriterFactory;
import org.tamacat.httpcore4.impl.io.DefaultHttpResponseParserFactory;
import org.tamacat.httpcore4.message.BasicHttpEntityEnclosingRequest;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

public class TestDefaultBHttpClientConnection {

    private FakeSocket socket;

    private DefaultBHttpClientConnection conn;

    @BeforeEach
    public void setUp() throws Exception {
        socket = new FakeSocket();
        conn = new DefaultBHttpClientConnection(1024, 1024,
            null, null,
            MessageConstraints.DEFAULT,
            LaxContentLengthStrategy.INSTANCE,
            StrictContentLengthStrategy.INSTANCE,
            DefaultHttpRequestWriterFactory.INSTANCE,
            DefaultHttpResponseParserFactory.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testReadRequestHead() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(Consts.ASCII));
        socket.calls.when("getInputStream").thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getMetrics().getResponseCount());

        final HttpResponse response = conn.receiveResponseHeader();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
        Assertions.assertTrue(response.containsHeader("User-Agent"));
        Assertions.assertEquals(1, conn.getMetrics().getResponseCount());
    }

    @Test
    public void testReadRequestEntity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(Consts.ASCII));
        socket.calls.when("getInputStream").thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getMetrics().getResponseCount());

        final HttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assertions.assertEquals(200, response.getStatusLine().getStatusCode());
        Assertions.assertTrue(response.containsHeader("User-Agent"));
        Assertions.assertEquals(1, conn.getMetrics().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(3, entity.getContentLength());
        Assertions.assertEquals(1, conn.getMetrics().getResponseCount());
    }

    @Test
    public void testWriteResponseHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        socket.calls.when("getOutputStream").thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getMetrics().getRequestCount());

        final HttpRequest request = new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1);
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getMetrics().getRequestCount());
        final String s = new String(outStream.toByteArray(), "ASCII");
        Assertions.assertEquals("GET /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    public void testWriteResponseEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        socket.calls.when("getOutputStream").thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getMetrics().getRequestCount());

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST",
                "/stuff", HttpVersion.HTTP_1_1);
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getMetrics().getRequestCount());
        final String s = new String(outStream.toByteArray(), "ASCII");
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123", s);
    }

}
