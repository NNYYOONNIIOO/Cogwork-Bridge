package com.cogwork.cogworkbridge.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/** Snapshot iteration for tracker callbacks that may re-enter entity tracking. */
public final class EntityTrackerIteration {

    private EntityTrackerIteration() {
    }

    public static <T> Iterator<T> snapshotIterator(Set<T> entries) {
        // Reentrant additions are already registered and will be ticked next
        // time. Removed entries must not send another spawn or movement packet.
        Iterator<T> snapshot = new ArrayList<>(entries).iterator();
        return new Iterator<>() {
            private T next;
            private boolean ready;
            private T lastReturned;
            private boolean canRemove;

            @Override
            public boolean hasNext() {
                if (ready && entries.contains(next)) return true;
                ready = false;
                while (snapshot.hasNext()) {
                    T candidate = snapshot.next();
                    if (entries.contains(candidate)) {
                        next = candidate;
                        ready = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                T result = next;
                next = null;
                ready = false;
                lastReturned = result;
                canRemove = true;
                return result;
            }

            @Override
            public void remove() {
                if (!canRemove) throw new IllegalStateException();
                entries.remove(lastReturned);
                lastReturned = null;
                canRemove = false;
            }
        };
    }
}
