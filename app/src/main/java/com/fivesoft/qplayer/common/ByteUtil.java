package com.fivesoft.qplayer.common;

import java.nio.ByteBuffer;

public class ByteUtil {

    public static String toReadableBytes(byte[] arr, int length){

        if(arr == null)
            return "null";

        if(length > arr.length)
            return "length > arr.length";

        if(length < 0)
            return "length < 0";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X ", arr[i]));
        }
        return sb.toString();
    }

    public static String toReadableBytes(byte[] arr, int off, int len){
            if(arr == null)
                return "null";

            if(len > arr.length)
                return "length > arr.length";

            if(len < 0)
                return "length < 0";

            StringBuilder sb = new StringBuilder();
            for (int i = off; i < off + len; i++) {
                sb.append(String.format("%02X ", arr[i]));
            }
            return sb.toString();
    }

    public static String toReadableBytes(byte... arr){
        return toReadableBytes(arr, arr.length);
    }

    public static String toReadableBytes(ByteBuffer buffer){

        if(buffer == null)
            return "null";

        StringBuilder sb = new StringBuilder();
        buffer.rewind();
        while (buffer.hasRemaining()) {
            sb.append(String.format("%02X ", buffer.get()));
        }
        return sb.toString();
    }

}
