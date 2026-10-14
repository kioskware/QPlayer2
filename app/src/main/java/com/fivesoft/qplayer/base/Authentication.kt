package com.fivesoft.qplayer.base

import com.fivesoft.qplayer.common.Credentials

/**
 * Holds authentication data. This supports many authentication methods:
 * 
 *  * Basic authentication (username & password)
 *  * Token authentication
 *  * Username, password and token (rare type)
 *  * No authentication
 * 
 */
data class Authentication(
    /**
     * Credentials for basic authentication. May be null if not used.
     */
    val credentials: Credentials?,
    /**
     * Token for token authentication. May be null if not used.
     */
    val token: ByteArray?
) {
    @JvmOverloads
    constructor(credentials: Credentials?, token: String? = null as String?) : this(
        credentials,
        token?.toByteArray()
    )

    constructor(
        username: String?,
        password: String?,
        token: String?
    ) : this(if (username == null) null else Credentials(username, password), token)

    constructor(
        username: String?,
        password: String?,
        token: ByteArray?
    ) : this(if (username == null) null else Credentials(username, password), token)

    constructor(
        username: String?,
        password: String?
    ) : this(if (username == null) null else Credentials(username, password), null as String?)

    /**
     * Checks if token authentication is set.
     * @return true if token authentication is set, false otherwise.
     */
    fun isToken(): Boolean {
        return token != null
    }

    /**
     * Checks if basic authentication is set.
     * @return true if basic authentication is set, false otherwise.
     */
    fun isCredentials(): Boolean {
        return credentials != null
    }

    val isNone: Boolean
        /**
         * Checks if no authentication data is set.
         * @return true if no authentication data is set, false otherwise.
         */
        get() = credentials == null && token == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Authentication
        if (credentials != other.credentials) return false
        if (!token.contentEquals(other.token)) return false
        if (isNone != other.isNone) return false
        return true
    }

    override fun hashCode(): Int {
        var result = credentials?.hashCode() ?: 0
        result = 31 * result + (token?.contentHashCode() ?: 0)
        result = 31 * result + isNone.hashCode()
        return result
    }
}
