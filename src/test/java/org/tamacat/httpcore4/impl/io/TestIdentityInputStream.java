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

import org.tamacat.httpcore4.impl.SessionInputBufferMock;
import org.tamacat.httpcore4.io.SessionInputBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link IdentityInputStream}.
 *
 */
public class TestIdentityInputStream {

    @Test
    public void testConstructor() throws Exception {
        final SessionInputBuffer receiver = new SessionInputBufferMock(new byte[] {});
        final IdentityInputStream in = new IdentityInputStream(receiver);
        in.close();
        try {
            new IdentityInputStream(null);
            Assertions.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testBasicRead() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        final IdentityInputStream inStream = new IdentityInputStream(receiver);
        final byte[] tmp = new byte[2];
        Assertions.assertEquals(2, inStream.read(tmp, 0, tmp.length));
        Assertions.assertEquals('a', tmp[0]);
        Assertions.assertEquals('b', tmp[1]);
        Assertions.assertEquals('c', inStream.read());
        Assertions.assertEquals(-1, inStream.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, inStream.read());
        Assertions.assertEquals(-1, inStream.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, inStream.read());
        inStream.close();
    }

    @Test
    public void testClosedCondition() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        final IdentityInputStream inStream = new IdentityInputStream(receiver);

        inStream.close();
        inStream.close();

        Assertions.assertEquals(0, inStream.available());
        final byte[] tmp = new byte[2];
        Assertions.assertEquals(-1, inStream.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, inStream.read());
        Assertions.assertEquals(-1, inStream.read(tmp, 0, tmp.length));
        Assertions.assertEquals(-1, inStream.read());
    }

    @Test
    public void testAvailable() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final SessionInputBufferMock receiver = new SessionInputBufferMock(input);
        final IdentityInputStream inStream = new IdentityInputStream(receiver);
        inStream.read();
        Assertions.assertEquals(2, inStream.available());
        inStream.close();
    }

}
