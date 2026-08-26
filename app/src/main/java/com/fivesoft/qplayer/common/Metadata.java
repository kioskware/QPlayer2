package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Objects;

public class Metadata {

    private final HashMap<String, Object> metadata = new HashMap<>();

    /**
     * Sets the value for the specified key.<br>
     * @param key The key.
     * @param value The value at given key to be set.
     */

    public void set(String key, Object value) {
        synchronized (metadata) {
            metadata.put(key, value);
        }
    }

    /**
     * Returns the value for the specified key or null if the key is not found.<br>
     * @param key The key to get value from.
     * @return The value for the specified key or null if the key is not found.
     */

    public Object get(@NonNull String key) {
        synchronized (metadata) {
            return metadata.get(Objects.requireNonNull(key));
        }
    }

    /**
     * Returns the value for the specified key or the default value
     * if the value is null or the type of the value is not the same as the type of the default value.<br>
     * @param key The key to get value from.
     * @param defaultValue The default value.
     * @return The value for the specified key or the default value
     * @param <T> The type of the value and default value.
     */

    public <T>T getOrDefault(@NonNull String key, T defaultValue) {
        synchronized (metadata) {
            Object value = metadata.get(Objects.requireNonNull(key));
            if (value == null) {
                return defaultValue;
            }
            try {
                //noinspection unchecked
                return (T) value;
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    /**
     * Removes all key-value pairs from the map.
     */

    public void clear() {
        synchronized (metadata) {
            metadata.clear();
        }
    }


}
