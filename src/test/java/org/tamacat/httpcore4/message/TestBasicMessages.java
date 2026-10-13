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

import org.tamacat.httpcore4.HttpEntity;
import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.HttpStatus;
import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.entity.BasicHttpEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.tamacat.httpcore4.HttpMessage}.
 *
 */
public class TestBasicMessages {

    @Test
    public void testDefaultResponseConstructors() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assertions.assertNotNull(response.getProtocolVersion());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        Assertions.assertNotNull(response.getProtocolVersion());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        Assertions.assertEquals("whatever", response.getStatusLine().getReasonPhrase());
    }

    @Test
    public void testSetResponseStatus() {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Assertions.assertNotNull(response.getProtocolVersion());
        Assertions.assertNotNull(response.getStatusLine());
        Assertions.assertEquals(200, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, "Bad Request");
        Assertions.assertNotNull(response.getProtocolVersion());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        response = new BasicHttpResponse(new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever"));
        Assertions.assertNotNull(response.getProtocolVersion());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
        Assertions.assertEquals("whatever", response.getStatusLine().getReasonPhrase());

        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusCode(-23);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        try {
            response.setStatusLine(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testSetResponseEntity() {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Assertions.assertNull(response.getEntity());

        final HttpEntity entity = new BasicHttpEntity();
        response.setEntity(entity);
        Assertions.assertTrue(entity == response.getEntity());
    }

    @Test
    public void testDefaultRequestConstructors() {
        HttpRequest request = new BasicHttpRequest("WHATEVER", "/");
        Assertions.assertNotNull(request.getProtocolVersion());
        Assertions.assertEquals("WHATEVER", request.getRequestLine().getMethod());
        Assertions.assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        Assertions.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assertions.assertEquals("GET", request.getRequestLine().getMethod());
        Assertions.assertEquals("/", request.getRequestLine().getUri());

        try {
            new BasicHttpRequest(null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest("GET", null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            new BasicHttpRequest(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testDefaultEntityEnclosingRequestConstructors() {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assertions.assertNotNull(request.getProtocolVersion());
        Assertions.assertEquals("GET", request.getRequestLine().getMethod());
        Assertions.assertEquals("/", request.getRequestLine().getUri());

        request = new BasicHttpEntityEnclosingRequest("GET", "/", HttpVersion.HTTP_1_0);
        Assertions.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assertions.assertEquals("GET", request.getRequestLine().getMethod());
        Assertions.assertEquals("/", request.getRequestLine().getUri());
    }

    @Test
    public void testSetRequestEntity() {
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assertions.assertNull(request.getEntity());

        final HttpEntity entity = new BasicHttpEntity();
        request.setEntity(entity);
        Assertions.assertTrue(entity == request.getEntity());
    }

    @Test
    public void testExpectContinue() {
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
        Assertions.assertFalse(request.expectContinue());
        request.addHeader("Expect", "100-Continue");
        Assertions.assertTrue(request.expectContinue());
    }

}

