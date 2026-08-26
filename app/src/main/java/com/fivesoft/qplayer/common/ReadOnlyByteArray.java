package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;

import java.util.Iterator;

public class ReadOnlyByteArray implements Iterable<Byte> {

    protected final byte[] array;
    protected int offset, length;

    public ReadOnlyByteArray(byte[] array, int offset, int length) {
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    public ReadOnlyByteArray(byte[] array) {
        this(array, 0, array.length);
    }

    public byte get(int index) {
        if(index < 0 || index >= length)
            throw new IndexOutOfBoundsException();
        return array[offset + index];
    }

    public byte[] get(int index, int length) {
        if(index < 0 || index + length > this.length)
            throw new IndexOutOfBoundsException();

        byte[] result = new byte[length];
        System.arraycopy(array, offset + index, result, 0, length);
        return result;
    }

    public byte[] getAll() {
        return get(0, length);
    }

    public int length() {
        return length;
    }

    @NonNull
    @Override
    public Iterator<Byte> iterator() {
        return new Iterator<Byte>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < length;
            }

            @Override
            public Byte next() {
                return array[offset + index++];
            }
        };
    }
}
