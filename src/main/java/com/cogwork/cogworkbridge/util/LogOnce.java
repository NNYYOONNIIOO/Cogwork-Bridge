package com.cogwork.cogworkbridge.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents a repeated render or tick failure from flooding the log. */
public final class LogOnce {
    private static final Set<Key> REPORTED = ConcurrentHashMap.newKeySet();

    private LogOnce() { }

    public static boolean first(String operation, Object subject, Throwable failure) {
        return REPORTED.add(new Key(operation, subject, failure == null ? null : failure.getClass()));
    }

    public static boolean first(String operation, Object subject) {
        return first(operation, subject, null);
    }

    private record Key(String operation, Object subject, Class<?> failure) { }
}
