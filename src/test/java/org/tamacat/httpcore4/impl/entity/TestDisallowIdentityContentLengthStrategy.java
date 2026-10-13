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

package org.tamacat.httpcore4.impl.entity;

import org.tamacat.httpcore4.HttpMessage;
import org.tamacat.httpcore4.ProtocolException;
import org.tamacat.httpcore4.entity.ContentLengthStrategy;
import org.tamacat.httpcore4.fakes.FakeContentLengthStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestDisallowIdentityContentLengthStrategy {

    @Test
    public void testZeroLength() throws Exception {
        final FakeContentLengthStrategy strat1 = new FakeContentLengthStrategy();
        final HttpMessage message = new DummyHttpMessage();
        strat1.calls.when("determineLength", message).thenReturn(0L);
        final DisallowIdentityContentLengthStrategy strat2 = new DisallowIdentityContentLengthStrategy(strat1);
        Assertions.assertEquals(0L, strat2.determineLength(message));
    }

    @Test
    public void testIdentity() throws Exception {
        final FakeContentLengthStrategy strat1 = new FakeContentLengthStrategy();
        final HttpMessage message = new DummyHttpMessage();
        strat1.calls.when("determineLength", message).thenReturn((long) ContentLengthStrategy.IDENTITY);
        final DisallowIdentityContentLengthStrategy strat2 = new DisallowIdentityContentLengthStrategy(strat1);
        Assertions.assertThrows(ProtocolException.class, () -> strat2.determineLength(message));
    }

}

