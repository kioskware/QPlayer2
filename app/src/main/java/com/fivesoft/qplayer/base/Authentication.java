package com.fivesoft.qplayer.base;

import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.Credentials;

/**
 * Holds authentication data. This supports many authentication methods:
 * <ul>
 *     <li>Basic authentication (username & password)</li>
 *     <li>Token authentication</li>
 *     <li>Username, password and token (rare type)</li>
 *     <li>No authentication</li>
 * </ul>
 */

public class Authentication {

    /**
     * Credentials for basic authentication. May be null if not used.
     */

    @Nullable
    public final Credentials credentials;

    /**
     * Token for token authentication. May be null if not used.
     */

    @Nullable
    public final byte[] token;

    public Authentication(@Nullable Credentials credentials, @Nullable byte[] token) {
        this.credentials = credentials;
        this.token = token;
    }

    public Authentication(@Nullable Credentials credentials, @Nullable String token) {
        this(credentials, token == null ? null : token.getBytes());
    }

    public Authentication(@Nullable String username, @Nullable String password, @Nullable String token) {
        this(username == null ? null : new Credentials(username, password), token);
    }

    public Authentication(@Nullable String username, @Nullable String password, @Nullable byte[] token) {
        this(username == null ? null : new Credentials(username, password), token);
    }

    public Authentication(@Nullable String username, @Nullable String password) {
        this(username == null ? null : new Credentials(username, password), (String) null);
    }

    public Authentication(@Nullable Credentials credentials) {
        this(credentials, (String) null);
    }

    /**
     * Checks if token authentication is set.
     * @return true if token authentication is set, false otherwise.
     */

    public boolean isToken() {
        return token != null;
    }

    /**
     * Checks if basic authentication is set.
     * @return true if basic authentication is set, false otherwise.
     */

    public boolean isCredentials() {
        return credentials != null;
    }

    /**
     * Checks if no authentication data is set.
     * @return true if no authentication data is set, false otherwise.
     */

    public boolean isNone() {
        return credentials == null && token == null;
    }

}
