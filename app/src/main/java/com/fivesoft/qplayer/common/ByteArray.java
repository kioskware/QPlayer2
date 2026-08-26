package com.fivesoft.qplayer.common;

import java.util.Arrays;

public class ByteArray extends ReadOnlyByteArray {

    public ByteArray(int length) {
        super(new byte[length]);
    }

    public ByteArray(byte[] array, int offset, int length) {
        super(array, offset, length);
    }

    public ByteArray(byte[] array) {
        super(array);
    }

    public void set(int index, byte value) {
        //Throw exception if index is out of bounds
        if(index < 0 || index >= length)
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length +
                    ", Offset: " + offset + ", Array Length: " + array.length);

        array[offset + index] = value;
    }

    public void set(int index, byte[] value) {
        //Throw exception if index is out of bounds
        if(index < 0 || index >= length)
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length +
                    ", Offset: " + offset + ", Array Length: " + array.length);

        //Throw exception if value is null
        if(value == null)
            throw new NullPointerException("Value is null");

        //Throw exception if value is too long
        if(value.length > length - index)
            throw new IndexOutOfBoundsException("Value length: " + value.length + ", Index: " + index + ", Length: " + length +
                    ", Offset: " + offset + ", Array Length: " + array.length);

        System.arraycopy(value, 0, array, offset + index, value.length);
    }

    public void set(int index, byte[] value, int offset, int length) {
        //Throw exception if index is out of bounds
        if(index < 0 || index >= this.length)
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length +
                    ", Offset: " + this.offset + ", Array Length: " + array.length);

        //Throw exception if value is null
        if(value == null)
            throw new NullPointerException("Value is null");

        //Throw exception if value is too long
        if(length > this.length - index)
            throw new IndexOutOfBoundsException("Value length: " + length + ", Index: " + index + ", Length: " + this.length +
                    ", Offset: " + this.offset + ", Array Length: " + array.length);

        System.arraycopy(value, offset, array, this.offset + index, length);
    }

    public void set(int index, ReadOnlyByteArray value) {

        //Throw exception if index is out of bounds
        if(index < 0 || index >= length)
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length +
                    ", Offset: " + offset + ", Array Length: " + array.length);

        //Throw exception if value is null
        if(value == null)
            throw new NullPointerException("Value is null");

        //Throw exception if value is too long
        if(value.length > length - index)
            throw new IndexOutOfBoundsException("Value length: " + value.length + ", Index: " + index + ", Length: " + length +
                    ", Offset: " + offset + ", Array Length: " + array.length);

        System.arraycopy(value.array, value.offset, array, this.offset + index, value.length);
    }

    public byte[] getArray() {
        return array;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public ByteArray trimLeft(int length) {
        this.offset = Math.max(0, this.offset + length);
        this.length = Math.max(0, this.length - length);
        return this;
    }

    public ByteArray trimRight(int length) {
        this.length = Math.max(0, this.length - length);
        return this;
    }

    public byte[] getArrayCopy() {
        return Arrays.copyOfRange(array, offset, offset + length);
    }
}
