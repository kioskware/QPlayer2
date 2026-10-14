package com.fivesoft.qplayer.core;

/**
 * Defines the mode of the keep screen on feature.
 */
public enum  KeepScreenOnMode {

    /**
     * Keep the screen on only when the player is started.
     */
    WHEN_STARTED,

    /**
     * Always keep the screen on. Even if the player is not started.
     */
    ALWAYS,

    /**
     * Never keep the screen on.
     */
    NEVER

}
