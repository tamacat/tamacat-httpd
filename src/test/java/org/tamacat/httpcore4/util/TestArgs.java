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

package org.tamacat.httpcore4.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Args}.
 */
public class TestArgs {

    @Test
    public void testArgCheckPass() {
        Args.check(true, "All is well");
    }

    @Test
    public void testArgCheckFail() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.check(false, "Oopsie"));
    }

    @Test
    public void testArgNotNullPass() {
        final String stuff = "stuff";
        Assertions.assertSame(stuff, Args.notNull(stuff, "Stuff"));
    }

    @Test
    public void testArgNotNullFail() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notNull(null, "Stuff"));
    }

    @Test
    public void testArgNotEmptyPass() {
        final String stuff = "stuff";
        Assertions.assertSame(stuff, Args.notEmpty(stuff, "Stuff"));
    }

    @Test
    public void testArgNotEmptyFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notEmpty((String) null, "Stuff"));
    }

    @Test
    public void testArgNotEmptyFail2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notEmpty("", "Stuff"));
    }

    @Test
    public void testArgNotBlankFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notBlank((String) null, "Stuff"));
    }

    @Test
    public void testArgNotBlankFail2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notBlank("", "Stuff"));
    }

    @Test
    public void testArgNotBlankFail3() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notBlank(" \t \n\r", "Stuff"));
    }

    @Test
    public void testArgCollectionNotEmptyPass() {
        final List<String> list = Arrays.asList("stuff");
        Assertions.assertSame(list, Args.notEmpty(list, "List"));
    }

    @Test
    public void testArgCollectionNotEmptyFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notEmpty((List<?>) null, "List"));
    }

    @Test
    public void testArgCollectionNotEmptyFail2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notEmpty(Collections.emptyList(), "List"));
    }

    @Test
    public void testPositiveIntPass() {
        Assertions.assertEquals(1, Args.positive(1, "Number"));
    }

    @Test
    public void testPositiveIntFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.positive(-1, "Number"));
    }

    @Test
    public void testPositiveIntFail2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.positive(0, "Number"));
    }

    @Test
    public void testPositiveLongPass() {
        Assertions.assertEquals(1L, Args.positive(1L, "Number"));
    }

    @Test
    public void testPositiveLongFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.positive(-1L, "Number"));
    }

    @Test
    public void testPositiveLongFail2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.positive(0L, "Number"));
    }

    @Test
    public void testNotNegativeIntPass1() {
        Assertions.assertEquals(1, Args.notNegative(1, "Number"));
    }

    @Test
    public void testNotNegativeIntPass2() {
        Assertions.assertEquals(0, Args.notNegative(0, "Number"));
    }

    @Test
    public void testNotNegativeIntFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notNegative(-1, "Number"));
    }

    @Test
    public void testNotNegativeLongPass1() {
        Assertions.assertEquals(1L, Args.notNegative(1L, "Number"));
    }

    @Test
    public void testNotNegativeLongPass2() {
        Assertions.assertEquals(0L, Args.notNegative(0L, "Number"));
    }

    @Test
    public void testNotNegativeLongFail1() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Args.notNegative(-1L, "Number"));
    }

}
