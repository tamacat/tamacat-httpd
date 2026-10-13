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

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HttpContext;
import org.tamacat.httpcore4.protocol.HttpExpectationVerifier;

/**
 * Recording {@link HttpExpectationVerifier} for tests.
 * <p>
 * Every call is recorded in {@link #calls}; an exception configured there for
 * {@code "verify"} is thrown. The response is otherwise left untouched.
 */
public class FakeHttpExpectationVerifier implements HttpExpectationVerifier {

    /** The calls received by this fake and the responses configured for them. */
    public final CallLog calls = new CallLog(HttpExpectationVerifier.class);

    @Override
    public void verify(final HttpRequest request, final HttpResponse response, final HttpContext context) {
        this.calls.call(null, "verify", request, response, context);
    }

}
