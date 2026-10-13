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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.config.MessageConstraints;
import org.tamacat.httpcore4.fakes.CallLog;
import org.tamacat.httpcore4.fakes.FakeSocket;
import org.tamacat.httpcore4.fakes.RecordingByteArrayInputStream;
import org.tamacat.httpcore4.fakes.RecordingInputStream;
import org.tamacat.httpcore4.fakes.RecordingOutputStream;
import org.tamacat.httpcore4.impl.entity.LaxContentLengthStrategy;
import org.tamacat.httpcore4.impl.entity.StrictContentLengthStrategy;
import org.tamacat.httpcore4.impl.io.ChunkedInputStream;
import org.tamacat.httpcore4.impl.io.ChunkedOutputStream;
import org.tamacat.httpcore4.impl.io.ContentLengthInputStream;
import org.tamacat.httpcore4.impl.io.ContentLengthOutputStream;
import org.tamacat.httpcore4.impl.io.IdentityInputStream;
import org.tamacat.httpcore4.impl.io.IdentityOutputStream;
import org.tamacat.httpcore4.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestBHttpConnectionBase {

    private FakeSocket socket;

    private BHttpConnectionBase conn;

    @BeforeEach
    public void setUp() throws Exception {
        socket = new FakeSocket();
        conn = new BHttpConnectionBase(1024, 1024,
            null, null,
            MessageConstraints.DEFAULT,
            LaxContentLengthStrategy.INSTANCE,
            StrictContentLengthStrategy.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertEquals(-1, conn.getLocalPort());
        Assertions.assertEquals(-1, conn.getRemotePort());
        Assertions.assertEquals(null, conn.getLocalAddress());
        Assertions.assertEquals(null, conn.getRemoteAddress());
        Assertions.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testSocketBind() throws Exception {
        final InetAddress localAddress = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final int localPort = 8888;
        final InetAddress remoteAddress = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
        final int remotePort = 80;
        final InetSocketAddress localSockAddress = new InetSocketAddress(localAddress, localPort);
        final InetSocketAddress remoteSockAddress = new InetSocketAddress(remoteAddress, remotePort);
        socket.calls.when("getLocalSocketAddress").thenReturn(localSockAddress);
        socket.calls.when("getRemoteSocketAddress").thenReturn(remoteSockAddress);
        socket.calls.when("getLocalAddress").thenReturn(localAddress);
        socket.calls.when("getLocalPort").thenReturn(localPort);
        socket.calls.when("getInetAddress").thenReturn(remoteAddress);
        socket.calls.when("getPort").thenReturn(remotePort);
        conn.bind(socket);

        Assertions.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());
        Assertions.assertTrue(conn.isOpen());
        Assertions.assertEquals(8888, conn.getLocalPort());
        Assertions.assertEquals(80, conn.getRemotePort());
        Assertions.assertEquals(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), conn.getLocalAddress());
        Assertions.assertEquals(InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), conn.getRemoteAddress());
    }

    @Test
    public void testConnectionClose() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        final RecordingOutputStream outStream = new RecordingOutputStream();

        socket.calls.when("getInputStream").thenReturn(inStream);
        socket.calls.when("getOutputStream").thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionOutputBuffer().write(0);

        Assertions.assertTrue(conn.isOpen());

        conn.close();

        Assertions.assertFalse(conn.isOpen());

        outStream.calls.assertCalled(1, "write(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
        socket.calls.assertCalled(1, "close");

        conn.close();
        socket.calls.assertCalled(1, "close");
        outStream.calls.assertCalled(1, "write(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
    }

    @Test
    public void testConnectionShutdown() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        final RecordingOutputStream outStream = new RecordingOutputStream();
        socket.calls.when("getInputStream").thenReturn(inStream);
        socket.calls.when("getOutputStream").thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionOutputBuffer().write(0);

        Assertions.assertTrue(conn.isOpen());

        conn.shutdown();

        Assertions.assertFalse(conn.isOpen());

        outStream.calls.assertNeverCalled("write(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
        socket.calls.assertNeverCalled("shutdownInput");
        socket.calls.assertNeverCalled("shutdownOutput");
        socket.calls.assertCalled(1, "close");

        conn.close();
        socket.calls.assertCalled(1, "close");

        conn.shutdown();
        socket.calls.assertCalled(1, "close");
    }

    @Test
    public void testPrepareInputLengthDelimited() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Content-Length", "10");
        message.addHeader("Content-Type", "stuff");
        message.addHeader("Content-Encoding", "identity");
        final HttpEntity entity = conn.prepareInput(message);
        Assertions.assertNotNull(entity);
        Assertions.assertFalse(entity.isChunked());
        Assertions.assertEquals(10, entity.getContentLength());
        final Header ct = entity.getContentType();
        Assertions.assertNotNull(ct);
        Assertions.assertEquals("stuff", ct.getValue());
        final Header ce = entity.getContentEncoding();
        Assertions.assertNotNull(ce);
        Assertions.assertEquals("identity", ce.getValue());
        final InputStream inStream = entity.getContent();
        Assertions.assertNotNull(inStream);
        Assertions.assertTrue((inStream instanceof ContentLengthInputStream));
    }

    @Test
    public void testPrepareInputChunked() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Transfer-Encoding", "chunked");
        final HttpEntity entity = conn.prepareInput(message);
        Assertions.assertNotNull(entity);
        Assertions.assertTrue(entity.isChunked());
        Assertions.assertEquals(-1, entity.getContentLength());
        final InputStream inStream = entity.getContent();
        Assertions.assertNotNull(inStream);
        Assertions.assertTrue((inStream instanceof ChunkedInputStream));
    }

    @Test
    public void testPrepareInputIdentity() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final HttpEntity entity = conn.prepareInput(message);
        Assertions.assertNotNull(entity);
        Assertions.assertFalse(entity.isChunked());
        Assertions.assertEquals(-1, entity.getContentLength());
        final InputStream inStream = entity.getContent();
        Assertions.assertNotNull(inStream);
        Assertions.assertTrue((inStream instanceof IdentityInputStream));
    }

    @Test
    public void testPrepareOutputLengthDelimited() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Content-Length", "10");
        final OutputStream outStream = conn.prepareOutput(message);
        Assertions.assertNotNull(outStream);
        Assertions.assertTrue((outStream instanceof ContentLengthOutputStream));
    }

    @Test
    public void testPrepareOutputChunked() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Transfer-Encoding", "chunked");
        final OutputStream outStream = conn.prepareOutput(message);
        Assertions.assertNotNull(outStream);
        Assertions.assertTrue((outStream instanceof ChunkedOutputStream));
    }

    @Test
    public void testPrepareOutputIdentity() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final OutputStream outStream = conn.prepareOutput(message);
        Assertions.assertNotNull(outStream);
        Assertions.assertTrue((outStream instanceof IdentityOutputStream));
    }

    @Test
    public void testSetSocketTimeout() throws Exception {
        conn.bind(socket);

        conn.setSocketTimeout(123);

        socket.calls.assertCalled(1, "setSoTimeout", 123);
    }

    @Test
    public void testSetSocketTimeoutException() throws Exception {
        conn.bind(socket);

        socket.calls.when("setSoTimeout", CallLog.ANY).thenThrow(new SocketException());

        conn.setSocketTimeout(123);

        socket.calls.assertCalled(1, "setSoTimeout", 123);
    }

    @Test
    public void testGetSocketTimeout() throws Exception {
        Assertions.assertEquals(-1, conn.getSocketTimeout());

        socket.calls.when("getSoTimeout").thenReturn(345);
        conn.bind(socket);

        Assertions.assertEquals(345, conn.getSocketTimeout());
    }

    @Test
    public void testGetSocketTimeoutException() throws Exception {
        Assertions.assertEquals(-1, conn.getSocketTimeout());

        socket.calls.when("getSoTimeout").thenThrow(new SocketException());
        conn.bind(socket);

        Assertions.assertEquals(-1, conn.getSocketTimeout());
    }

    @Test
    public void testAwaitInputInBuffer() throws Exception {
        final RecordingByteArrayInputStream inStream = new RecordingByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        socket.calls.when("getInputStream").thenReturn(inStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionInputBuffer().read();

        Assertions.assertTrue(conn.awaitInput(432));

        socket.calls.assertNeverCalled("setSoTimeout", CallLog.ANY);
        inStream.calls.assertCalled(1, "read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
    }

    @Test
    public void testAwaitInputInSocket() throws Exception {
        final RecordingByteArrayInputStream inStream = new RecordingByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        socket.calls.when("getInputStream").thenReturn(inStream);
        socket.calls.when("getSoTimeout").thenReturn(345);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertTrue(conn.awaitInput(432));

        socket.calls.assertCalled(1, "setSoTimeout", 432);
        socket.calls.assertCalled(1, "setSoTimeout", 345);
        inStream.calls.assertCalled(1, "read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
    }

    @Test
    public void testAwaitInputNoData() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        socket.calls.when("getInputStream").thenReturn(inStream);
        inStream.calls.when("read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY).thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertFalse(conn.awaitInput(432));
    }

    @Test
    public void testStaleWhenClosed() throws Exception {
        conn.bind(socket);
        conn.ensureOpen();
        conn.close();
        Assertions.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenHasData() throws Exception {
        final RecordingByteArrayInputStream inStream = new RecordingByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        socket.calls.when("getInputStream").thenReturn(inStream);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenEndOfStream() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        socket.calls.when("getInputStream").thenReturn(inStream);
        inStream.calls.when("read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY).thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenTimeout() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        socket.calls.when("getInputStream").thenReturn(inStream);
        inStream.calls.when("read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY)
                .thenThrow(new SocketTimeoutException());

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenIOError() throws Exception {
        final RecordingInputStream inStream = new RecordingInputStream();
        socket.calls.when("getInputStream").thenReturn(inStream);
        inStream.calls.when("read(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY)
                .thenThrow(new SocketException());

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertTrue(conn.isStale());
    }

}
