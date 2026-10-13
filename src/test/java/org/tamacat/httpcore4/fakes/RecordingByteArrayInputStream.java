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

import java.io.ByteArrayInputStream;

/**
 * A {@link ByteArrayInputStream} which records the calls of {@code read()},
 * {@code read(byte[], int, int)} and {@code close()} in {@link #calls}, under
 * the names {@code "read()"}, {@code "read(byte[],int,int)"} and
 * {@code "close"}, and otherwise behaves like its superclass.
 */
public class RecordingByteArrayInputStream extends ByteArrayInputStream {

    /** The calls received by this stream. */
    public final CallLog calls =
            new CallLog(ByteArrayInputStream.class).declare("read()", "read(byte[],int,int)");

    public RecordingByteArrayInputStream(final byte[] buf) {
        super(buf);
    }

    @Override
    public synchronized int read() {
        this.calls.call(null, "read()");
        return super.read();
    }

    @Override
    public synchronized int read(final byte[] b, final int off, final int len) {
        this.calls.call(null, "read(byte[],int,int)", b, off, len);
        return super.read(b, off, len);
    }

    @Override
    public void close() throws java.io.IOException {
        this.calls.call(null, "close");
        super.close();
    }

}
