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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.tamacat.httpcore4.Consts;
import org.tamacat.httpcore4.MessageConstraintException;
import org.tamacat.httpcore4.config.MessageConstraints;
import org.tamacat.httpcore4.fakes.CallLog;
import org.tamacat.httpcore4.fakes.RecordingByteArrayOutputStream;
import org.tamacat.httpcore4.impl.SessionInputBufferMock;
import org.tamacat.httpcore4.impl.SessionOutputBufferMock;
import org.tamacat.httpcore4.io.HttpTransportMetrics;
import org.tamacat.httpcore4.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSessionInOutBuffers {

    @Test
    public void testBasicBufferProperties() throws Exception {
        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(new byte[] { 1, 2 , 3});
        Assertions.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inBuffer.capacity());
        Assertions.assertEquals(SessionInputBufferMock.BUFFER_SIZE, inBuffer.available());
        Assertions.assertEquals(0, inBuffer.length());
        inBuffer.read();
        Assertions.assertEquals(SessionInputBufferMock.BUFFER_SIZE - 2, inBuffer.available());
        Assertions.assertEquals(2, inBuffer.length());

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        Assertions.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.capacity());
        Assertions.assertEquals(SessionOutputBufferMock.BUFFER_SIZE, outbuffer.available());
        Assertions.assertEquals(0, outbuffer.length());
        outbuffer.write(new byte[] {1, 2, 3});
        Assertions.assertEquals(SessionOutputBufferMock.BUFFER_SIZE - 3, outbuffer.available());
        Assertions.assertEquals(3, outbuffer.length());
    }

    @Test
    public void testBasicReadWriteLine() throws Exception {

        final String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine((String)null);
        outbuffer.writeLine((CharArrayBuffer)null);
        outbuffer.flush();

        HttpTransportMetrics tmetrics = outbuffer.getMetrics();
        final long bytesWritten = tmetrics.getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assertions.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData());

        for (final String teststr : teststrs) {
            Assertions.assertEquals(teststr, inBuffer.readLine());
        }

        Assertions.assertNull(inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        tmetrics = inBuffer.getMetrics();
        final long bytesRead = tmetrics.getBytesTransferred();
        Assertions.assertEquals(expected, bytesRead);
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        outbuffer.write(new byte[] {'a', '\n'});
        outbuffer.write(new byte[] {'\r', '\n'});
        outbuffer.write(new byte[] {'\r', '\r', '\n'});
        outbuffer.write(new byte[] {'\n'});
        //these write operations should have no effect
        outbuffer.write(null);
        outbuffer.write(null, 0, 12);

        outbuffer.flush();

        long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(8, bytesWritten);

        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        final String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(Consts.ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(8 + 14 +2, bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        final String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(Consts.ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(8 + 14 + 2 + 15 + 2 , bytesWritten);

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        final String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuffer.write(buffer.toString().getBytes(Consts.ASCII));
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2, bytesWritten);

        outbuffer.write(new byte[] {'a'});
        outbuffer.flush();
        bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(8 + 14 + 2 + 15 + 2 + 16 + 2 + 1, bytesWritten);

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData());

        Assertions.assertEquals("a", inBuffer.readLine());
        Assertions.assertEquals("", inBuffer.readLine());
        Assertions.assertEquals("\r", inBuffer.readLine());
        Assertions.assertEquals("", inBuffer.readLine());
        Assertions.assertEquals(s1, inBuffer.readLine());
        Assertions.assertEquals(s2, inBuffer.readLine());
        Assertions.assertEquals(s3, inBuffer.readLine());
        Assertions.assertEquals("a", inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(bytesWritten, bytesRead);
    }

    @Test
    public void testBasicReadWriteLineLargeBuffer() throws Exception {

        final String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the output buffer " +
                "which is only 16 bytes for this test";
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final String teststr : teststrs) {
            chbuffer.clear();
            chbuffer.append(teststr);
            outbuffer.writeLine(chbuffer);
        }
        //these write operations should have no effect
        outbuffer.writeLine((String)null);
        outbuffer.writeLine((CharArrayBuffer)null);
        outbuffer.flush();

        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        long expected = 0;
        for (final String teststr : teststrs) {
            expected += (teststr.length() + 2/*CRLF*/);
        }
        Assertions.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData(), 1024);

        for (final String teststr : teststrs) {
            Assertions.assertEquals(teststr, inBuffer.readLine());
        }
        Assertions.assertNull(inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(expected, bytesRead);
    }

    @Test
    public void testReadWriteBytes() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        int off = 0;
        int remaining = out.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            outbuffer.write(out, off, chunk);
            off += chunk;
            remaining -= chunk;
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outbuffer.getData();
        Assertions.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assertions.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(tmp);

        // these read operations will have no effect
        Assertions.assertEquals(0, inBuffer.read(null, 0, 10));
        Assertions.assertEquals(0, inBuffer.read(null));
        long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(0, bytesRead);

        final byte[] in = new byte[40];
        off = 0;
        remaining = in.length;
        while (remaining > 0) {
            int chunk = 10;
            if (chunk > remaining) {
                chunk = remaining;
            }
            final int readLen = inBuffer.read(in, off, chunk);
            if (readLen == -1) {
                break;
            }
            off += readLen;
            remaining -= readLen;
        }
        for (int i = 0; i < out.length; i++) {
            Assertions.assertEquals(out[i], in[i]);
        }
        Assertions.assertEquals(-1, inBuffer.read(tmp));
        Assertions.assertEquals(-1, inBuffer.read(tmp));
        bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testReadWriteByte() throws Exception {
        // make the buffer larger than that of outbuffer
        final byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)(120 + i);
        }
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        for (final byte element : out) {
            outbuffer.write(element);
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(out.length, bytesWritten);

        final byte[] tmp = outbuffer.getData();
        Assertions.assertEquals(out.length, tmp.length);
        for (int i = 0; i < out.length; i++) {
            Assertions.assertEquals(out[i], tmp[i]);
        }

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(tmp);
        final byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inBuffer.read();
        }
        for (int i = 0; i < out.length; i++) {
            Assertions.assertEquals(out[i], in[i]);
        }
        Assertions.assertEquals(-1, inBuffer.read());
        Assertions.assertEquals(-1, inBuffer.read());
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(out.length, bytesRead);
    }

    @Test
    public void testWriteSmallFragmentBuffering() throws Exception {
        final RecordingByteArrayOutputStream outStream = new RecordingByteArrayOutputStream();
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(outStream, 16, 16, null);
        outbuffer.write(1);
        outbuffer.write(2);
        outbuffer.write(new byte[] {1, 2});
        outbuffer.write(new byte[]{3, 4});
        outbuffer.flush();
        outStream.calls.assertCalled(1, "write(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
        outStream.calls.assertNeverCalled("write(int)", CallLog.ANY);
    }

    @Test
    public void testWriteSmallFragmentNoBuffering() throws Exception {
        final RecordingByteArrayOutputStream outStream = new RecordingByteArrayOutputStream();
        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(outStream, 16, 0, null);
        outbuffer.write(1);
        outbuffer.write(2);
        outbuffer.write(new byte[] {1, 2});
        outbuffer.write(new byte[]{3, 4});
        outStream.calls.assertCalled(2, "write(byte[],int,int)", CallLog.ANY, CallLog.ANY, CallLog.ANY);
        outStream.calls.assertCalled(2, "write(int)", CallLog.ANY);
    }

    @Test
    public void testLineLimit() throws Exception {
        final String s = "a very looooooooooooooooooooooooooooooooooooooooooong line\r\n";
        final byte[] tmp = s.getBytes(Consts.ASCII);
        // no limit
        final SessionInputBufferMock inBuffer1 = new SessionInputBufferMock(tmp, 5,
                MessageConstraints.DEFAULT);
        Assertions.assertNotNull(inBuffer1.readLine());
        final long bytesRead = inBuffer1.getMetrics().getBytesTransferred();
        Assertions.assertEquals(60, bytesRead);

        // 15 char limit
        final SessionInputBufferMock inBuffer2 = new SessionInputBufferMock(tmp, 5,
                MessageConstraints.lineLen(15));
        try {
            inBuffer2.readLine();
            Assertions.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test
    public void testLineLimit2() throws Exception {
        final String s = "just a line\r\n";
        final byte[] tmp = s.getBytes(Consts.ASCII);
        // no limit
        final SessionInputBufferMock inBuffer1 = new SessionInputBufferMock(tmp, 25,
                MessageConstraints.DEFAULT);
        Assertions.assertNotNull(inBuffer1.readLine());
        final long bytesRead = inBuffer1.getMetrics().getBytesTransferred();
        Assertions.assertEquals(13, bytesRead);

        // 10 char limit
        final SessionInputBufferMock inBuffer2 = new SessionInputBufferMock(tmp, 25,
                MessageConstraints.lineLen(10));
        try {
            inBuffer2.readLine();
            Assertions.fail("MessageConstraintException expected");
        } catch (final MessageConstraintException ex) {
        }
    }

    @Test //HTTPCORE-472
    public void testLineLimit3() throws Exception {
        final String s = "012345678\r\nblaaaaaaaaaaaaaaaaaah";
        final byte[] tmp = s.getBytes(Consts.ASCII);
        final SessionInputBufferMock inBuffer1 = new SessionInputBufferMock(tmp, 10,
                MessageConstraints.lineLen(20));
        Assertions.assertEquals("012345678", inBuffer1.readLine());
    }

    @Test
    public void testReadLineFringeCase1() throws Exception {
        final String s = "abc\r\n";
        final byte[] tmp = s.getBytes(Consts.ASCII);
        final SessionInputBufferMock inBuffer1 = new SessionInputBufferMock(tmp, 128);
        Assertions.assertEquals('a', inBuffer1.read());
        Assertions.assertEquals('b', inBuffer1.read());
        Assertions.assertEquals('c', inBuffer1.read());
        Assertions.assertEquals('\r', inBuffer1.read());
        Assertions.assertEquals("", inBuffer1.readLine());
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultibyteCodedReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);
        final String s2 = constructString(RUSSIAN_HELLO);
        final String s3 = "Like hello and stuff";

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(Consts.UTF_8);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 10; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s2);
            outbuffer.writeLine(chbuffer);
            chbuffer.clear();
            chbuffer.append(s3);
            outbuffer.writeLine(chbuffer);
        }
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.getBytes(Consts.UTF_8).length + 2)+
                (s2.getBytes(Consts.UTF_8).length + 2) +
                (s3.getBytes(Consts.UTF_8).length + 2)) * 10;
        Assertions.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData(), Consts.UTF_8);

        for (int i = 0; i < 10; i++) {
            Assertions.assertEquals(s1, inBuffer.readLine());
            Assertions.assertEquals(s2, inBuffer.readLine());
            Assertions.assertEquals(s3, inBuffer.readLine());
        }
        Assertions.assertNull(inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(expected, bytesRead);
    }

    @Test
    public void testMultibyteCodedReadWriteLongLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);
        final String s2 = constructString(RUSSIAN_HELLO);
        final String s3 = "Like hello and stuff";
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            buf.append(s1).append(s2).append(s3);
        }
        final String s = buf.toString();

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(Consts.UTF_8);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append(s);
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData(), Consts.UTF_8);

        Assertions.assertEquals(s, inBuffer.readLine());
    }

    @Test
    public void testNonAsciiReadWriteLine() throws Exception {
        final String s1 = constructString(SWISS_GERMAN_HELLO);

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock(Consts.ISO_8859_1);

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        for (int i = 0; i < 5; i++) {
            chbuffer.clear();
            chbuffer.append(s1);
            outbuffer.writeLine(chbuffer);
        }
        for (int i = 0; i < 5; i++) {
            outbuffer.writeLine(s1);
        }
        chbuffer.clear();
        outbuffer.writeLine(chbuffer);
        outbuffer.flush();
        final long bytesWritten = outbuffer.getMetrics().getBytesTransferred();
        final long expected = ((s1.toString().getBytes(Consts.ISO_8859_1).length + 2)) * 10 + 2;
        Assertions.assertEquals(expected, bytesWritten);

        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(
                outbuffer.getData(), Consts.ISO_8859_1);

        final CharArrayBuffer buf = new CharArrayBuffer(64);
        for (int i = 0; i < 10; i++) {
            buf.clear();
            final int readLen = inBuffer.readLine(buf);
            Assertions.assertEquals(readLen, SWISS_GERMAN_HELLO.length);
            Assertions.assertEquals(s1, buf.toString());
        }
        buf.clear();
        Assertions.assertEquals("", inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        Assertions.assertNull(inBuffer.readLine());
        final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
        Assertions.assertEquals(expected, bytesRead);
    }

    @Test
    public void testUnmappableInputActionReport() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        Assertions.assertThrows(CharacterCodingException.class, () -> outbuf.writeLine(s));
    }

    @Test
    public void testUnmappableInputActionReplace() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        outbuf.writeLine(s);
        outbuf.flush();
        final String result = new String(outbuf.getData(), "ISO-8859-1");
        Assertions.assertEquals("This text contains a circumflex ? !!!\r\n", result);
    }

    @Test
    public void testUnmappableInputActionIgnore() throws Exception {
        final String s = "This text contains a circumflex \u0302 !!!";
        final CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionOutputBufferMock outbuf = new SessionOutputBufferMock(encoder);
        outbuf.writeLine(s);
        outbuf.flush();
        final String result = new String(outbuf.getData(), "ISO-8859-1");
        Assertions.assertEquals("This text contains a circumflex  !!!\r\n", result);
    }

    @Test
    public void testMalformedInputActionReport() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(Consts.ISO_8859_1);
        final CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(tmp, decoder);
        Assertions.assertThrows(CharacterCodingException.class, () -> inBuffer.readLine());
    }

    @Test
    public void testMalformedInputActionReplace() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(Consts.ISO_8859_1);
        final CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(tmp, decoder);
        final String s = inBuffer.readLine();
        Assertions.assertEquals("Gr\ufffdezi_z\ufffdm\ufffd", s);
    }

    @Test
    public void testMalformedInputActionIgnore() throws Exception {
        final byte[] tmp = constructString(SWISS_GERMAN_HELLO).getBytes(Consts.ISO_8859_1);
        final CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(tmp, decoder);
        final String s = inBuffer.readLine();
        Assertions.assertEquals("Grezi_zm", s);
    }

    @Test
    public void testInvalidCharArrayBuffer() throws Exception {
        final SessionInputBufferMock inBuffer = new SessionInputBufferMock(new byte[] {});
        try {
            inBuffer.readLine(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
            final long bytesRead = inBuffer.getMetrics().getBytesTransferred();
            Assertions.assertEquals(0, bytesRead);
        }
    }

}

