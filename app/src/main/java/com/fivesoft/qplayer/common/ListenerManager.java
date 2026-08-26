package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ListenerManager<T> {

    //map cannot have null entries!!!
    private final HashMap<String, T> listeners = new HashMap<>();

    /**
     * Adds a listener. If a listener with the same id already exists, it will be replaced.
     * @param id The id of the listener.
     * @param listener The listener.
     * @return The previous listener with the same id or null if there was no such listener.
     */

    public final T setListener(@NonNull String id, @NonNull T listener) {
        synchronized (listeners) {
            return listeners.put(Objects.requireNonNull(id), Objects.requireNonNull(listener));
        }
    }

    /**
     * Removes a listener with the specified id.<br>
     * If there is no such listener, nothing happens.
     * @param id The id of the listener.
     * @return The removed listener or null if there was no such listener.
     */

    public final T removeListener(@NonNull String id) {
        synchronized (listeners) {
            return listeners.remove(Objects.requireNonNull(id));
        }
    }

    /**
     * Removes all listeners.<br>
     * If there are no listeners, nothing happens.
     */

    public final void clearListeners() {
        synchronized (listeners) {
            listeners.clear();
        }
    }

    /**
     * Calls the specified consumer for each listener.
     * @param consumer The consumer.
     */

    protected final void forEachListener(@NonNull BiConsumer<String, T> consumer) {
        synchronized (listeners) {
            listeners.forEach(Objects.requireNonNull(consumer));
        }
    }

}
