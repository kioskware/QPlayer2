package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

public interface ErrorCallback {

    default void onError(@NonNull Exception e){}

}
