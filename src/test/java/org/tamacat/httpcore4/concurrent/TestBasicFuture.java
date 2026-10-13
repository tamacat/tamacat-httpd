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
package org.tamacat.httpcore4.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBasicFuture {

    @Test
    public void testCompleted() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);

        Assertions.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Assertions.assertTrue(callback.isCompleted());
        Assertions.assertSame(result, callback.getResult());
        Assertions.assertFalse(callback.isFailed());
        Assertions.assertNull(callback.getException());
        Assertions.assertFalse(callback.isCancelled());

        Assertions.assertSame(result, future.get());
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());

    }

    @Test
    public void testCompletedWithTimeout() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);

        Assertions.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Assertions.assertTrue(callback.isCompleted());
        Assertions.assertSame(result, callback.getResult());
        Assertions.assertFalse(callback.isFailed());
        Assertions.assertNull(callback.getException());
        Assertions.assertFalse(callback.isCancelled());

        Assertions.assertSame(result, future.get(1, TimeUnit.MILLISECONDS));
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testFailed() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.failed(boom);
        future.completed(result);
        Assertions.assertFalse(callback.isCompleted());
        Assertions.assertNull(callback.getResult());
        Assertions.assertTrue(callback.isFailed());
        Assertions.assertSame(boom, callback.getException());
        Assertions.assertFalse(callback.isCancelled());

        try {
            future.get();
        } catch (final ExecutionException ex) {
            Assertions.assertSame(boom, ex.getCause());
        }
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testCancelled() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.cancel(true);
        future.failed(boom);
        future.completed(result);
        Assertions.assertFalse(callback.isCompleted());
        Assertions.assertNull(callback.getResult());
        Assertions.assertFalse(callback.isFailed());
        Assertions.assertNull(callback.getException());
        Assertions.assertTrue(callback.isCancelled());

        try {
            future.get();
            Assertions.fail("CancellationException expected");
        } catch (final CancellationException ex) {
        }
        Assertions.assertTrue(future.isDone());
        Assertions.assertTrue(future.isCancelled());
    }

    @Test
    public void testAsyncCompleted() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Object result = new Object();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.completed(result);
                } catch (final InterruptedException boom) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        Assertions.assertSame(result, future.get(60, TimeUnit.SECONDS));
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testAsyncFailed() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Exception boom = new Exception();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.failed(boom);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        try {
            future.get(60, TimeUnit.SECONDS);
        } catch (final ExecutionException ex) {
            Assertions.assertSame(boom, ex.getCause());
        }
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testAsyncCancelled() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.cancel(true);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        Assertions.assertThrows(CancellationException.class, () -> future.get(60, TimeUnit.SECONDS));
    }

    @Test
    public void testAsyncTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Object result = new Object();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    future.completed(result);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        Assertions.assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAsyncNegativeTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        Assertions.assertThrows(TimeoutException.class, () -> future.get(-1, TimeUnit.MILLISECONDS));
    }

}
