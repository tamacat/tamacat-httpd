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

package org.tamacat.httpcore4.fakes;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketImpl;

/**
 * Recording, configurable {@link Socket} for tests.
 * <p>
 * The socket is never connected and the methods overridden here never reach
 * the socket implementation, so no real socket is opened. The overridden
 * methods are the ones the blocking connection classes call. Each records
 * the call in {@link #calls} and answers it from the responses configured
 * there; a call without a configured response returns {@code 0} or
 * {@code null}. {@code close()} only records the call.
 */
public class FakeSocket extends Socket {

    /** The calls received by this fake and the responses configured for them. */
    public final CallLog calls = new CallLog(Socket.class);

    public FakeSocket() throws java.net.SocketException {
        super((SocketImpl) null);
    }

    @Override
    public InputStream getInputStream() {
        return this.calls.call((InputStream) null, "getInputStream");
    }

    @Override
    public OutputStream getOutputStream() {
        return this.calls.call((OutputStream) null, "getOutputStream");
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return this.calls.call((SocketAddress) null, "getLocalSocketAddress");
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return this.calls.call((SocketAddress) null, "getRemoteSocketAddress");
    }

    @Override
    public InetAddress getLocalAddress() {
        return this.calls.call((InetAddress) null, "getLocalAddress");
    }

    @Override
    public int getLocalPort() {
        return this.calls.call(0, "getLocalPort");
    }

    @Override
    public InetAddress getInetAddress() {
        return this.calls.call((InetAddress) null, "getInetAddress");
    }

    @Override
    public int getPort() {
        return this.calls.call(0, "getPort");
    }

    @Override
    public int getSoTimeout() {
        return this.calls.call(0, "getSoTimeout");
    }

    @Override
    public void setSoTimeout(final int timeout) {
        this.calls.call(null, "setSoTimeout", timeout);
    }

    @Override
    public void setSoLinger(final boolean on, final int linger) {
        this.calls.call(null, "setSoLinger", on, linger);
    }

    @Override
    public void shutdownInput() {
        this.calls.call(null, "shutdownInput");
    }

    @Override
    public void shutdownOutput() {
        this.calls.call(null, "shutdownOutput");
    }

    @Override
    public void close() {
        this.calls.call(null, "close");
    }

}
