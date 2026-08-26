package com.fivesoft.qplayer.common.resolver;

import androidx.annotation.Nullable;

/**
 * Represents a class that can create an object from descriptor.<br>
 * The implementation should decide if it's able to create or not.<br>
 * <br>The decision is made by the {@link #accept(Object)} method.<br><br>
 * If positive value is returned, the creator can create the object, otherwise the creator cannot create such object.<br>
 * The higher the value, the more suitable the creator is for such object creation.<br><br>
 * @param <D> The type of the object description.
 * @param <R> The type of the result object.
 */
public interface Creator<D, R> {

    /**
     * Checks if the creator can create the task based on given task description (T).<br>
     * @param t The description of the task.
     * @return positive value if the creator can create an object from description, non-positive value otherwise.<br>
     * Higher values mean that the performer is more suitable for the task.
     */
    int accept(D t);

    /**
     * Creates a result based on the given description (T).<br>
     * @param t The description of the object to be created.
     * @return The result. May be null if the creator cannot create such object.
     */

    @Nullable
    R create(D t);

}
