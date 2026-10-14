package com.fivesoft.qplayer.common

data class ResponseHeader(
    val code: Int,
    val message: String?,
    val headers: Headers = Headers()
)
