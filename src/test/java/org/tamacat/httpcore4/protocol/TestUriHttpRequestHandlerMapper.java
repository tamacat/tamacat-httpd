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

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.fakes.FakeHttpRequestHandler;
import org.tamacat.httpcore4.fakes.RecordingUriPatternMatcher;
import org.tamacat.httpcore4.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestUriHttpRequestHandlerMapper {

    @Test
    public void testRegisterUnregister() throws Exception {
        final FakeHttpRequestHandler h = new FakeHttpRequestHandler();

        final RecordingUriPatternMatcher<HttpRequestHandler> matcher =
                new RecordingUriPatternMatcher<HttpRequestHandler>();
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);

        registry.register("/h1", h);
        registry.unregister("/h1");

        matcher.calls.assertCalledOnce("register", "/h1", h);
        matcher.calls.assertCalledOnce("unregister", "/h1");
    }

    @Test
    public void testLookup() throws Exception {
        final RecordingUriPatternMatcher<HttpRequestHandler> matcher =
                new RecordingUriPatternMatcher<HttpRequestHandler>();
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        registry.lookup(request);
        registry.unregister("/h1");

        matcher.calls.assertCalledOnce("lookup", "/");
    }

    @Test
    public void testRegisterNull() throws Exception {
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper();
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.register(null, null));
    }

    @Test
    public void testLookupNull() throws Exception {
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper();
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.register(null, null));
    }

    @Test
    public void testWildCardMatchingWithQuery() throws Exception {
        final FakeHttpRequestHandler h1 = new FakeHttpRequestHandler();
        final FakeHttpRequestHandler h2 = new FakeHttpRequestHandler();
        final FakeHttpRequestHandler def = new FakeHttpRequestHandler();

        final RecordingUriPatternMatcher<HttpRequestHandler> matcher =
                new RecordingUriPatternMatcher<HttpRequestHandler>();
        final UriHttpRequestHandlerMapper registry = new UriHttpRequestHandlerMapper(matcher);
        registry.register("*", def);
        registry.register("*.view", h1);
        registry.register("*.form", h2);

        HttpRequestHandler h;

        h = registry.lookup(new BasicHttpRequest("GET", "/that.view?param=value"));
        Assertions.assertNotNull(h);
        Assertions.assertTrue(h1 == h);

        h = registry.lookup(new BasicHttpRequest("GET", "/that.form?whatever"));
        Assertions.assertNotNull(h);
        Assertions.assertTrue(h2 == h);

        h = registry.lookup(new BasicHttpRequest("GET", "/whatever"));
        Assertions.assertNotNull(h);
        Assertions.assertTrue(def == h);
    }

}
