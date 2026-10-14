package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

public class Header {

    public final String name;
    public final String value;

    public Header(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @NonNull
    public String toString() {
        return name + ": " + value;
    }

}
