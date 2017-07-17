/*
 * Copyright (c) 2017 Antony Esik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ae.camunda.dispatcher.util;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class JavaUtils {

    public static <T> T callWithoutCheckedException(Callable<T> callable) throws RuntimeException {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> void consumeIfNotNull(Consumer<T> consumer, T value) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    public static <T> void tryConsume(ConsumerWithCheckedException<T> consumer,
                                      T value,
                                      long coolDownTime,
                                      long retryCount) {
        long iteration = 0;
        Exception exception = null;
        while (iteration < retryCount) {
            try {
                consumer.accept(value);
                return;
            } catch (Exception ex) {
                exception = ex;
                iteration++;
                try {
                    Thread.sleep(coolDownTime);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
        throw new RuntimeException("Exception in tryConsume after delay", exception);
    }

    public static String toString(StackTraceElement[] stackTraceElements) {
        return toString(stackTraceElements, "\n");
    }

    public static String toString(StackTraceElement[] stackTraceElements, String delimeter) {
        return Arrays.stream(stackTraceElements).map(StackTraceElement::toString).collect(Collectors.joining(delimeter));
    }

    public static void setFieldWithoutCheckedException(Field field, Object target, Object value) {
        callWithoutCheckedException(() ->  {
            setFieldUnsafe(field, target, value);
            return null;
        });
    }

    public static void setFieldWithoutCheckedException(Field field, Object target, Callable supplier) {
        callWithoutCheckedException(() ->  {
            setFieldUnsafe(field, target, supplier.call());
            return null;
        });
    }

    public static Object getFieldWithoutCheckedException(Field field, Object target) {
        return callWithoutCheckedException(() ->  getFieldUnsafe(field, target));
    }

    private static Object getFieldUnsafe(Field field, Object target) throws IllegalAccessException {
        try {
            field.setAccessible(true);
            return field.get(target);
        } finally {
            field.setAccessible(false);
        }
    }

    private static void setFieldUnsafe(Field field, Object target, Object value) throws IllegalAccessException {
        try {
            field.setAccessible(true);
            if (value == null
                    && field.getType().isPrimitive()) {
                field.set(target, 0);
            } else {
                field.set(target, value);
            }
        } finally {
            field.setAccessible(false);
        }
    }
}
