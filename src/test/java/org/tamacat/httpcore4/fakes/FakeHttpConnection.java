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

import org.tamacat.httpcore4.HttpConnection;
import org.tamacat.httpcore4.HttpConnectionMetrics;

/**
 * Recording, configurable {@link HttpConnection} for tests.
 * <p>
 * Every call is recorded in {@link #calls} and answered from the responses
 * configured there; a call without a configured response returns {@code false}, {@code 0} or {@code null}.
 * <p>
 * {@code close()} and {@code shutdown()} only record the call: {@code isOpen()}
 * keeps returning what the test configured.
 */
public class FakeHttpConnection implements HttpConnection {

    /** The calls received by this fake and the responses configured for them. */
    public final CallLog calls;

    public FakeHttpConnection() {
        this(HttpConnection.class);
    }

    /** For the fakes of the sub-interfaces of {@link HttpConnection}. */
    protected FakeHttpConnection(final Class<?> type) {
        super();
        this.calls = new CallLog(type);
    }

    @Override
    public void close() {
        this.calls.call(null, "close");
    }

    @Override
    public boolean isOpen() {
        return this.calls.call(Boolean.FALSE, "isOpen");
    }

    @Override
    public boolean isStale() {
        return this.calls.call(Boolean.FALSE, "isStale");
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        this.calls.call(null, "setSocketTimeout", timeout);
    }

    @Override
    public int getSocketTimeout() {
        return this.calls.call(0, "getSocketTimeout");
    }

    @Override
    public void shutdown() {
        this.calls.call(null, "shutdown");
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        return this.calls.call((HttpConnectionMetrics) null, "getMetrics");
    }

}
