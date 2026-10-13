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
package org.tamacat.httpcore4.message;

import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HttpMessage;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.ProtocolVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AbstractHttpMessage}.
 *
 */
public class TestAbstractMessage {

    static class TestHttpMessage extends AbstractHttpMessage {

        private final ProtocolVersion ver;

        public TestHttpMessage(final ProtocolVersion ver) {
            super();
            this.ver = ver != null ? ver : HttpVersion.HTTP_1_1;
        }

        public TestHttpMessage() {
            this(HttpVersion.HTTP_1_1);
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return ver;
        }

    }

    @Test
    public void testBasicProperties() {
        final HttpMessage message = new TestHttpMessage();
        Assertions.assertNotNull(message.headerIterator());
        final Header[] headers = message.getAllHeaders();
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(0, headers.length);
    }

    @Test
    public void testBasicHeaderOps() {
        final HttpMessage message = new TestHttpMessage();
        Assertions.assertFalse(message.containsHeader("whatever"));

        message.addHeader("name", "1");
        message.addHeader("name", "2");

        Header[] headers = message.getAllHeaders();
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.length);

        Header h = message.getFirstHeader("name");
        Assertions.assertNotNull(h);
        Assertions.assertEquals("1", h.getValue());

        message.setHeader("name", "3");
        h = message.getFirstHeader("name");
        Assertions.assertNotNull(h);
        Assertions.assertEquals("3", h.getValue());
        h = message.getLastHeader("name");
        Assertions.assertNotNull(h);
        Assertions.assertEquals("2", h.getValue());

        // Should have no effect
        message.addHeader(null);
        message.setHeader(null);

        headers = message.getHeaders("name");
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.length);
        Assertions.assertEquals("3", headers[0].getValue());
        Assertions.assertEquals("2", headers[1].getValue());

        message.addHeader("name", "4");

        headers[1] = new BasicHeader("name", "5");
        message.setHeaders(headers);

        headers = message.getHeaders("name");
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.length);
        Assertions.assertEquals("3", headers[0].getValue());
        Assertions.assertEquals("5", headers[1].getValue());

        message.setHeader("whatever", null);
        message.removeHeaders("name");
        message.removeHeaders(null);
        headers = message.getAllHeaders();
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.length);
        Assertions.assertEquals(null, headers[0].getValue());

        message.removeHeader(message.getFirstHeader("whatever"));
        headers = message.getAllHeaders();
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(0, headers.length);
    }

    @Test
    public void testInvalidInput() {
        final HttpMessage message = new TestHttpMessage();
        try {
            message.addHeader(null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            message.setHeader(null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

}

