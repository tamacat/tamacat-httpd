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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;

/**
 * The call recorder shared by the hand-written test fakes of this package.
 * <p>
 * A fake passes every call it receives to {@link #call(Object, String, Object...)}.
 * The log records the call (method name and argument values, in the order
 * received) and then answers it: with the next value of the newest matching
 * response sequence configured through {@link #when(String, Object...)}, by
 * throwing the configured exception, or with the default value given by the fake.
 * Tests assert on the recorded calls with {@link #assertCalled(int, String, Object...)}
 * and its variants.
 * <p>
 * Arguments are compared with {@link Objects#equals(Object, Object)};
 * {@link #ANY} matches any value. A call matches only if the method name and
 * the number of arguments are the same. Overloads which differ only in the
 * parameter types are told apart by the argument values; where that is not
 * possible (for instance {@code write(int)} and {@code write(byte[])}) the
 * fake records them under distinct names such as {@code "write(int)"}, which
 * it announces with {@link #declare(String...)}.
 * <p>
 * Method names are checked against the types the log was created for and
 * against the declared names, so that a misspelt or ambiguous name in a
 * "never called" assertion fails instead of passing.
 * <p>
 * This class is thread safe: the pool tests call fakes from several threads.
 */
public final class CallLog {

    /** Matches any argument value, including {@code null}. */
    public static final Object ANY = new Object() {

        @Override
        public String toString() {
            return "<any>";
        }

    };

    private static final class Call {

        final String method;
        final Object[] args;

        Call(final String method, final Object[] args) {
            this.method = method;
            this.args = args;
        }

        @Override
        public String toString() {
            return this.method + Arrays.toString(this.args);
        }

    }

    /** A response sequence, or an exception, configured for the calls matching a pattern. */
    public final class Stub {

        private final String method;
        private final Object[] pattern;
        private Object[] values;
        private Throwable failure;
        private int next;

        Stub(final String method, final Object[] pattern) {
            this.method = method;
            this.pattern = pattern;
        }

        /**
         * Answers the matching calls with the given values in turn; the last
         * value is repeated once the sequence is exhausted.
         */
        public void thenReturn(final Object... sequence) {
            if (sequence == null || sequence.length == 0) {
                throw new IllegalArgumentException("At least one value is required");
            }
            for (final Object value : sequence) {
                checkReturnValue(this.method, this.pattern.length, value);
            }
            synchronized (CallLog.this) {
                this.values = sequence.clone();
                this.failure = null;
                this.next = 0;
            }
        }

        /** Makes every matching call throw the given exception. */
        public void thenThrow(final Throwable ex) {
            if (ex == null) {
                throw new IllegalArgumentException("Exception may not be null");
            }
            checkThrowable(this.method, this.pattern.length, ex);
            synchronized (CallLog.this) {
                this.failure = ex;
                this.values = null;
            }
        }

    }

    private final Class<?>[] types;
    private final List<Call> calls = new ArrayList<Call>();
    private final List<Stub> stubs = new ArrayList<Stub>();
    private final List<String> declared = new ArrayList<String>();

    /**
     * @param types the types whose methods the owning fake records. Method
     *   names passed to this log must name a public or protected method of
     *   one of them with the same number of parameters.
     */
    public CallLog(final Class<?>... types) {
        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("At least one type is required");
        }
        this.types = types.clone();
    }

    /**
     * Announces the names, of the form {@code name(parameter types)}, under
     * which the owning fake records overloads that argument values cannot
     * tell apart. Once such a name is declared the bare name is rejected.
     * To be called by fakes only.
     */
    public CallLog declare(final String... names) {
        for (final String name : names) {
            if (name.indexOf('(') < 0) {
                throw new IllegalArgumentException("Not an overload name: " + name);
            }
            this.declared.add(name);
        }
        return this;
    }

    /**
     * Records a call and answers it. To be called by fakes only.
     *
     * @param defaultValue the value to return if no response is configured.
     * @return the configured response or {@code defaultValue}.
     */
    @SuppressWarnings("unchecked")
    public <T> T call(final T defaultValue, final String method, final Object... args) {
        checkMethod(method, args.length);
        Object result = defaultValue;
        Throwable failure = null;
        synchronized (this) {
            this.calls.add(new Call(method, args.clone()));
            for (int i = this.stubs.size() - 1; i >= 0; i--) {
                final Stub stub = this.stubs.get(i);
                if (stub.method.equals(method) && matches(stub.pattern, args)
                        && (stub.values != null || stub.failure != null)) {
                    if (stub.failure != null) {
                        failure = stub.failure;
                    } else {
                        result = stub.values[Math.min(stub.next, stub.values.length - 1)];
                        stub.next++;
                    }
                    break;
                }
            }
        }
        if (failure != null) {
            CallLog.<RuntimeException>sneakyThrow(failure);
        }
        return (T) result;
    }

    /**
     * Starts the configuration of the response to the calls of {@code method}
     * whose arguments match {@code argPattern}. A later configuration takes
     * precedence over an earlier one matching the same call.
     */
    public Stub when(final String method, final Object... argPattern) {
        checkMethod(method, argPattern.length);
        final Stub stub = new Stub(method, argPattern.clone());
        synchronized (this) {
            this.stubs.add(stub);
        }
        return stub;
    }

    /** Returns the number of recorded calls of {@code method} whose arguments match. */
    public int count(final String method, final Object... argPattern) {
        checkMethod(method, argPattern.length);
        int n = 0;
        synchronized (this) {
            for (final Call call : this.calls) {
                if (call.method.equals(method) && matches(argPattern, call.args)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Asserts that {@code method} was called with matching arguments exactly {@code times} times. */
    public void assertCalled(final int times, final String method, final Object... argPattern) {
        Assertions.assertEquals(times, count(method, argPattern),
                "Number of calls of " + new Call(method, argPattern) + "; recorded calls: " + this);
    }

    /** Asserts that {@code method} was called with matching arguments exactly once. */
    public void assertCalledOnce(final String method, final Object... argPattern) {
        assertCalled(1, method, argPattern);
    }

    /** Asserts that {@code method} was never called with matching arguments. */
    public void assertNeverCalled(final String method, final Object... argPattern) {
        assertCalled(0, method, argPattern);
    }

    /** Asserts that {@code method} was called with matching arguments once or more. */
    public void assertCalledAtLeastOnce(final String method, final Object... argPattern) {
        Assertions.assertTrue(count(method, argPattern) >= 1,
                "No call of " + new Call(method, argPattern) + "; recorded calls: " + this);
    }

    /** Returns the recorded calls, in the order received, in a printable form. */
    public synchronized List<String> calls() {
        final List<String> result = new ArrayList<String>(this.calls.size());
        for (final Call call : this.calls) {
            result.add(call.toString());
        }
        return result;
    }

    @Override
    public String toString() {
        return calls().toString();
    }

    private static boolean matches(final Object[] pattern, final Object[] args) {
        if (pattern.length != args.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] != ANY && !Objects.equals(pattern[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private List<Method> candidates(final String method, final int arity) {
        final int paren = method.indexOf('(');
        final String name = paren < 0 ? method : method.substring(0, paren);
        final List<Method> result = new ArrayList<Method>();
        for (final Class<?> type : this.types) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                collect(c.getDeclaredMethods(), name, arity, result);
            }
            collect(type.getMethods(), name, arity, result);
        }
        return result;
    }

    private static void collect(
            final Method[] methods, final String name, final int arity, final List<Method> result) {
        for (final Method m : methods) {
            final int mod = m.getModifiers();
            if (m.getName().equals(name) && m.getParameterCount() == arity
                    && (Modifier.isPublic(mod) || Modifier.isProtected(mod))) {
                result.add(m);
            }
        }
    }

    private List<Method> checkMethod(final String method, final int arity) {
        if (method.indexOf('(') >= 0) {
            if (!this.declared.contains(method)) {
                throw new IllegalArgumentException("Undeclared overload name " + method
                        + "; declared: " + this.declared);
            }
        } else {
            for (final String name : this.declared) {
                if (name.startsWith(method + "(")) {
                    throw new IllegalArgumentException("Ambiguous method name " + method
                            + "; use one of " + this.declared);
                }
            }
        }
        final List<Method> candidates = candidates(method, arity);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No method " + method + " with " + arity
                    + " parameter(s) in " + Arrays.toString(this.types));
        }
        return candidates;
    }

    private void checkReturnValue(final String method, final int arity, final Object value) {
        for (final Method m : checkMethod(method, arity)) {
            final Class<?> type = m.getReturnType();
            if (type == void.class) {
                continue;
            }
            if (value == null ? !type.isPrimitive() : box(type).isInstance(value)) {
                return;
            }
        }
        throw new IllegalArgumentException(method + " cannot return " + value);
    }

    private void checkThrowable(final String method, final int arity, final Throwable ex) {
        if (ex instanceof RuntimeException || ex instanceof Error) {
            return;
        }
        for (final Method m : checkMethod(method, arity)) {
            for (final Class<?> declared : m.getExceptionTypes()) {
                if (declared.isInstance(ex)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(method + " cannot throw " + ex);
    }

    private static Class<?> box(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return Double.class;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(final Throwable ex) throws E {
        throw (E) ex;
    }

}
