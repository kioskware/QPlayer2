package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigInteger;

public class Util {

    @NonNull
    public static String getHexStringFromBytes(@NonNull byte[] bytes) {
        StringBuilder buf = new StringBuilder();
        for (byte b : bytes)
            buf.append(String.format("%02x", b));
        return buf.toString();
    }

    @NonNull
    public static byte[] getBytesFromHexString(@NonNull String config) {
        // "1210fff1" -> [12, 10, ff, f1]
        return new BigInteger(config, 16).toByteArray();
    }

    public static boolean readUntilBytesFound(@NonNull InputStream inputStream, @NonNull byte[] array) throws IOException {
        byte[] buffer = new byte[array.length];

        // Fill in buffer
        if (NetUtils.readData(inputStream, buffer, 0, buffer.length) != buffer.length)
            return false; // EOF

        while (!Thread.interrupted()) {
            // Check if buffer is the same one
            if (memcmp(buffer, 0, array, 0, buffer.length)) {
                return true;
            }
            // ABCDEF -> BCDEFF
            shiftLeftArray(buffer, buffer.length);
            // Read 1 byte into last buffer item
            if (NetUtils.readData(inputStream, buffer, buffer.length - 1, 1) != 1) {
                return false; // EOF
            }
        }

        return false;
    }

    public static boolean memcmp(@NonNull byte[] source1, int offsetSource1, @NonNull byte[] source2, int offsetSource2, int num) {
        if (source1.length - offsetSource1 < num) {
            return false;
        }

        if (source2.length - offsetSource2 < num) {
            return false;
        }

        for (int i = 0; i < num; i++) {
            if (source1[offsetSource1 + i] != source2[offsetSource2 + i]) {
                return false;
            }
        }
        return true;
    }

    public static void shiftLeftArray(@NonNull byte[] array, int num) {
        // ABCDEF -> BCDEF
        if (num - 1 >= 0) System.arraycopy(array, 1, array, 0, num - 1);
    }

    public static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException();
    }

    public static boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    public static int limit(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long limit(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float limit(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double limit(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String byteArrayToJavaString(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        sb.append("byte[] bytes = new byte[] {");
        for (int i = offset; i < offset + length; i++) {
            sb.append(bytes[i]);
            if (i != offset + length - 1) {
                sb.append(", ");
            }
        }
        sb.append("};");
        return sb.toString();
    }

    public static int getDefaultPort(String schema) {
        if (schema == null)
            return -1;

        switch (schema) {
            case "http":
            case "rtmpte":
            case "rtmpt":
                return 80;
            case "https":
            case "rtmps":
            case "tls":
                return 443;
            case "rtsp":
            case "tcp":
                return 554;
            case "rtmp":
            case "rtmpe":
            case "rtmfp":
                return 1935;
            case "mms":
                return 1755;
            case "udp":
            case "rtcp":
            case "rtp":
            case "sdp":
            case "file":
            case "rtpm":
                return 0;
            case "rtpu":
                return 5004;
            case "rtspu":
                return 5005;
            case "rtmpts":
                return 5008;
            case "rtmptsu":
                return 5009;

        }

        return -1;
    }

    public static boolean closeQuietly(@Nullable Closeable closeable) {
        if (closeable == null)
            return false;

        try {
            closeable.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null)
            return false;

        try {
            closeable.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

}
