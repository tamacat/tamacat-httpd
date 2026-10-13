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

import org.tamacat.httpcore4.HttpClientConnection;
import org.tamacat.httpcore4.HttpEntityEnclosingRequest;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;

/**
 * Recording, configurable {@link HttpClientConnection} for tests.
 * <p>
 * Every call is recorded in {@link #calls} and answered from the responses
 * configured there; a call without a configured response returns {@code false} or {@code null}.
 */
public class FakeHttpClientConnection extends FakeHttpConnection implements HttpClientConnection {

    public FakeHttpClientConnection() {
        super(HttpClientConnection.class);
    }

    @Override
    public boolean isResponseAvailable(final int timeout) {
        return this.calls.call(Boolean.FALSE, "isResponseAvailable", timeout);
    }

    @Override
    public void sendRequestHeader(final HttpRequest request) {
        this.calls.call(null, "sendRequestHeader", request);
    }

    @Override
    public void sendRequestEntity(final HttpEntityEnclosingRequest request) {
        this.calls.call(null, "sendRequestEntity", request);
    }

    @Override
    public HttpResponse receiveResponseHeader() {
        return this.calls.call((HttpResponse) null, "receiveResponseHeader");
    }

    @Override
    public void receiveResponseEntity(final HttpResponse response) {
        this.calls.call(null, "receiveResponseEntity", response);
    }

    @Override
    public void flush() {
        this.calls.call(null, "flush");
    }

}
