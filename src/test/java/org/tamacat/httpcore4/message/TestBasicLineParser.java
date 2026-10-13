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

import org.tamacat.httpcore4.HttpVersion;
import org.tamacat.httpcore4.ParseException;
import org.tamacat.httpcore4.RequestLine;
import org.tamacat.httpcore4.StatusLine;
import org.tamacat.httpcore4.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicLineParser}.
 *
 */
public class TestBasicLineParser {

    @Test
    public void testRLParseSuccess() throws Exception {
        //typical request line
        RequestLine requestline = BasicLineParser.parseRequestLine
            ("GET /stuff HTTP/1.1", null);
        Assertions.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assertions.assertEquals("GET", requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //Lots of blanks
        requestline = BasicLineParser.parseRequestLine
            ("  GET    /stuff   HTTP/1.1   ", null);
        Assertions.assertEquals("GET /stuff HTTP/1.1", requestline.toString());
        Assertions.assertEquals("GET", requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());

        //this is not strictly valid, but is lenient
        requestline = BasicLineParser.parseRequestLine
            ("\rGET /stuff HTTP/1.1", null);
        Assertions.assertEquals("GET", requestline.getMethod());
        Assertions.assertEquals("/stuff", requestline.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, requestline.getProtocolVersion());
    }

    @Test
    public void testRLParseFailure() throws Exception {
        try {
            BasicLineParser.parseRequestLine("    ", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("  GET", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET/stuff HTTP/1.1", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseRequestLine("GET /stuff HTTP/1.1 Oooooooooooppsie", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }
    }

    @Test
    public void testSLParseSuccess() throws Exception {
        //typical status line
        StatusLine statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 OK", null);
        Assertions.assertEquals("HTTP/1.1 200 OK", statusLine.toString());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());

        //status line with multi word reason phrase
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found", null);
        Assertions.assertEquals(404, statusLine.getStatusCode());
        Assertions.assertEquals("Not Found", statusLine.getReasonPhrase());

        //reason phrase can be anyting
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Non Trouve", null);
        Assertions.assertEquals("Non Trouve", statusLine.getReasonPhrase());

        //its ok to end with a \n\r
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 404 Not Found\r\n", null);
        Assertions.assertEquals("Not Found", statusLine.getReasonPhrase());

        //this is valid according to the Status-Line BNF
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200 ", null);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1 200", null);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("HTTP/1.1     200 OK", null);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("\rHTTP/1.1 200 OK", null);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());

        //this is not strictly valid, but is lenient
        statusLine = BasicLineParser.parseStatusLine
            ("  HTTP/1.1 200 OK", null);
        Assertions.assertEquals(200, statusLine.getStatusCode());
        Assertions.assertEquals("OK", statusLine.getReasonPhrase());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, statusLine.getProtocolVersion());
    }

    @Test
    public void testSLParseFailure() throws Exception {
        try {
            BasicLineParser.parseStatusLine("xxx 200 OK", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 xxx OK", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1    ", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }

        try {
            BasicLineParser.parseStatusLine("HTTP/1.1 -200 OK", null);
            Assertions.fail();
        } catch (final ParseException e) {
            // expected
        }
    }

    @Test
    public void testHttpVersionParsing() throws Exception {

        String s = "HTTP/1.1";
        HttpVersion version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        Assertions.assertEquals("HTTP", version.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals(1, version.getMajor(), "HTTP major version number");
        Assertions.assertEquals(1, version.getMinor(), "HTTP minor version number");
        Assertions.assertEquals(s, version.toString(), "HTTP version number");

        s = "HTTP/123.4567";
        version = (HttpVersion)
            BasicLineParser.parseProtocolVersion(s, null);
        Assertions.assertEquals("HTTP", version.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals(123, version.getMajor(), "HTTP major version number");
        Assertions.assertEquals(4567, version.getMinor(), "HTTP minor version number");
        Assertions.assertEquals(s, version.toString(), "HTTP version number");
    }

    @Test
    public void testHttpVersionParsingUsingCursor() throws Exception {

        String s = "HTTP/1.1";
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        ParserCursor cursor = new ParserCursor(0, s.length());

        final LineParser parser = BasicLineParser.INSTANCE;

        HttpVersion version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assertions.assertEquals("HTTP", version.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals(1, version.getMajor(), "HTTP major version number");
        Assertions.assertEquals(1, version.getMinor(), "HTTP minor version number");
        Assertions.assertEquals("HTTP/1.1", version.toString(), "HTTP version number");
        Assertions.assertEquals(s.length(), cursor.getPos());
        Assertions.assertTrue(cursor.atEnd());

        s = "HTTP/1.123 123";
        buffer = new CharArrayBuffer(16);
        buffer.append(s);
        cursor = new ParserCursor(0, s.length());

        version = (HttpVersion) parser.parseProtocolVersion(buffer, cursor);
        Assertions.assertEquals("HTTP", version.getProtocol(), "HTTP protocol name");
        Assertions.assertEquals(1, version.getMajor(), "HTTP major version number");
        Assertions.assertEquals(123, version.getMinor(), "HTTP minor version number");
        Assertions.assertEquals("HTTP/1.123", version.toString(), "HTTP version number");
        Assertions.assertEquals(' ', buffer.charAt(cursor.getPos()));
        Assertions.assertEquals(s.length() - 4, cursor.getPos());
        Assertions.assertFalse(cursor.atEnd());
    }

    @Test
    public void testInvalidHttpVersionParsing() throws Exception {
        try {
            BasicLineParser.parseProtocolVersion((String)null, null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("    ", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTT", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("crap", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/crap", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1234   ", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/whatever.whatever whatever", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
        try {
            BasicLineParser.parseProtocolVersion
                ("HTTP/1.whatever whatever", null);
            Assertions.fail("ParseException should have been thrown");
        } catch (final ParseException e) {
            //expected
        }
    }

}
