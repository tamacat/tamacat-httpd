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

import java.net.InetAddress;

import org.tamacat.httpcore4.HttpInetConnection;

/**
 * Recording, configurable {@link HttpInetConnection} for tests.
 * <p>
 * Every call is recorded in {@link #calls} and answered from the responses
 * configured there; a call without a configured response returns {@code 0} or {@code null}.
 */
public class FakeHttpInetConnection extends FakeHttpConnection implements HttpInetConnection {

    public FakeHttpInetConnection() {
        super(HttpInetConnection.class);
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
    public InetAddress getRemoteAddress() {
        return this.calls.call((InetAddress) null, "getRemoteAddress");
    }

    @Override
    public int getRemotePort() {
        return this.calls.call(0, "getRemotePort");
    }

}
