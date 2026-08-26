package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Variation of {@link Runnable} that allows to throw any {@link Throwable}.
 */

public interface ThrowingRunnable {

    /**
     * Runs the action.
     * @throws Throwable Any throwable.
     */

    void run() throws Throwable;

    /**
     * Runs the action and returns any throwable that was thrown or null if no throwable was thrown.
     * @param runnable Action to run.
     * @return Any throwable that was thrown or null if no throwable was thrown.
     */

    static Throwable run(@NonNull ThrowingRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable is null");
        try {
            runnable.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

}
