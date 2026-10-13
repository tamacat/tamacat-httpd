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

package org.tamacat.httpcore4.impl.io;

import java.io.IOException;

import org.tamacat.httpcore4.Consts;
import org.tamacat.httpcore4.Header;
import org.tamacat.httpcore4.HeaderElement;
import org.tamacat.httpcore4.NameValuePair;
import org.tamacat.httpcore4.ProtocolException;
import org.tamacat.httpcore4.impl.SessionInputBufferMock;
import org.tamacat.httpcore4.io.SessionInputBuffer;
import org.tamacat.httpcore4.message.BufferedHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AbstractMessageParser}.
 */
public class TestMessageParser {

    @Test
    public void testInvalidInput() throws Exception {
        try {
            // the first argument must not be null
            AbstractMessageParser.parseHeaders(null, -1, -1, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            new BufferedHeader(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testBasicHeaderParsing() throws Exception {
        final String s =
            "header1: stuff\r\n" +
            "header2  : stuff \r\n" +
            "header3: stuff\r\n" +
            "     and more stuff\r\n" +
            "\t and even more stuff\r\n" +
            "     \r\n" +
            "\r\n";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        final Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(3, headers.length);
        Assertions.assertEquals("header1", headers[0].getName());
        Assertions.assertEquals("stuff", headers[0].getValue());
        Assertions.assertEquals("header2", headers[1].getName());
        Assertions.assertEquals("stuff", headers[1].getValue());
        Assertions.assertEquals("header3", headers[2].getName());
        Assertions.assertEquals("stuff and more stuff and even more stuff", headers[2].getValue());

        final Header h = headers[0];

        Assertions.assertTrue(h instanceof BufferedHeader);
        Assertions.assertNotNull(((BufferedHeader)h).getBuffer());
        Assertions.assertEquals("header1: stuff", ((BufferedHeader)h).toString());
        Assertions.assertEquals(8, ((BufferedHeader)h).getValuePos());
    }

    @Test
    public void testBufferedHeader() throws Exception {
        final String s =
            "header1  : stuff; param1 = value1; param2 = \"value 2\" \r\n" +
            "\r\n";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        final Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.length);
        Assertions.assertEquals("header1  : stuff; param1 = value1; param2 = \"value 2\" ", headers[0].toString());
        final HeaderElement[] elements = headers[0].getElements();
        Assertions.assertNotNull(elements);
        Assertions.assertEquals(1, elements.length);
        Assertions.assertEquals("stuff", elements[0].getName());
        Assertions.assertEquals(null, elements[0].getValue());
        final NameValuePair[] params = elements[0].getParameters();
        Assertions.assertNotNull(params);
        Assertions.assertEquals(2, params.length);
        Assertions.assertEquals("param1", params[0].getName());
        Assertions.assertEquals("value1", params[0].getValue());
        Assertions.assertEquals("param2", params[1].getName());
        Assertions.assertEquals("value 2", params[1].getValue());
    }

    @Test
    public void testParsingInvalidHeaders() throws Exception {
        String s = "    stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        try {
            AbstractMessageParser.parseHeaders(receiver, -1, -1, null);
            Assertions.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
        s = "  :  stuff\r\n" +
            "header1: stuff\r\n" +
            "\r\n";
        receiver = new SessionInputBufferMock(s, Consts.ASCII);
        try {
            AbstractMessageParser.parseHeaders(receiver, -1, -1, null);
            Assertions.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testParsingMalformedFirstHeader() throws Exception {
        final String s =
            "    header1: stuff\r\n" +
            "header2  : stuff \r\n";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        final Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(2, headers.length);
        Assertions.assertEquals("header1", headers[0].getName());
        Assertions.assertEquals("stuff", headers[0].getValue());
        Assertions.assertEquals("header2", headers[1].getName());
        Assertions.assertEquals("stuff", headers[1].getValue());
    }

    @Test
    public void testEmptyDataStream() throws Exception {
        final String s = "";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        final Header[] headers = AbstractMessageParser.parseHeaders
            (receiver, -1, -1, null);
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(0, headers.length);
    }

    @Test
    public void testMaxHeaderCount() throws Exception {
        final String s =
            "header1: stuff\r\n" +
            "header2: stuff \r\n" +
            "header3: stuff\r\n" +
            "\r\n";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        try {
            AbstractMessageParser.parseHeaders(receiver, 2, -1, null);
            Assertions.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
    }

    @Test
    public void testMaxHeaderCountForFoldedHeader() throws Exception {
        final String s =
            "header1: stuff\r\n" +
            " stuff \r\n" +
            " stuff\r\n" +
            "\r\n";
        final SessionInputBuffer receiver = new SessionInputBufferMock(s, Consts.ASCII);
        try {
            AbstractMessageParser.parseHeaders(receiver, 2, 15, null);
            Assertions.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            // expected
        }
    }

}

