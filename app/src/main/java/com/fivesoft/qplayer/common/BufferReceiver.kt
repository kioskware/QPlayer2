package com.fivesoft.qplayer.common

import kotlin.ByteArray

interface BufferReceiver {

    fun onBufferReceived(
        timestamp: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Boolean

}
