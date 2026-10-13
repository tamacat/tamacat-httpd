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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Future} for tests which never completes.
 * <p>
 * Every call is recorded in {@link #calls} and answered from the responses
 * configured there; a call without a configured response returns
 * {@code false} or {@code null}. {@code cancel} only records the call.
 *
 * @param <V> the result type.
 */
public class RecordingFuture<V> implements Future<V> {

    /** The calls received by this fake and the responses configured for them. */
    public final CallLog calls = new CallLog(Future.class);

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return this.calls.call(Boolean.FALSE, "cancel", mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return this.calls.call(Boolean.FALSE, "isCancelled");
    }

    @Override
    public boolean isDone() {
        return this.calls.call(Boolean.FALSE, "isDone");
    }

    @Override
    public V get() {
        return this.calls.call((V) null, "get");
    }

    @Override
    public V get(final long timeout, final TimeUnit unit) {
        return this.calls.call((V) null, "get", timeout, unit);
    }

}
