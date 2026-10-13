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

import java.io.OutputStream;

/**
 * Recording {@link OutputStream} for tests which discards what is written.
 * <p>
 * The three {@code write} methods, {@code flush()} and {@code close()} are
 * recorded in {@link #calls}, the {@code write} methods under the names
 * {@code "write(int)"}, {@code "write(byte[])"} and
 * {@code "write(byte[],int,int)"}. An exception to throw can be configured
 * there.
 */
public class RecordingOutputStream extends OutputStream {

    /** The calls received by this fake and the responses configured for them. */
    public final CallLog calls = new CallLog(OutputStream.class)
            .declare("write(int)", "write(byte[])", "write(byte[],int,int)");

    @Override
    public void write(final int b) {
        this.calls.call(null, "write(int)", b);
    }

    @Override
    public void write(final byte[] b) {
        this.calls.call(null, "write(byte[])", (Object) b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) {
        this.calls.call(null, "write(byte[],int,int)", b, off, len);
    }

    @Override
    public void flush() {
        this.calls.call(null, "flush");
    }

    @Override
    public void close() {
        this.calls.call(null, "close");
    }

}
