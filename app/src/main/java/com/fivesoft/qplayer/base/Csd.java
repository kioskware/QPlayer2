package com.fivesoft.qplayer.base;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Class for storing and managing Codec Specific Data (CSD) of a track.<br>
 * CSD is a sequence of bytes that is required for decoding a track for example for
 * H264 video tracks they are SPS and PPS.<br>
 */

public class Csd {

    public static final int DEFAULT_CAPACITY = 20;

    private final byte[][] csd;

    /**
     * Creates a Csd with the default capacity of {@link #DEFAULT_CAPACITY}
     */

    public Csd() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a Csd with the specified capacity.
     * @param capacity The capacity of the Csd.
     * @throws IllegalArgumentException If the capacity is less than 0.
     */

    public Csd(int capacity) {

        if(capacity < 0)
            throw new IllegalArgumentException("Capacity must be >= 0");

        csd = new byte[capacity][];
    }

    /**
     * Sets the CSD at the specified index.
     * @param index The index of the CSD.
     * @param csd The CSD to set.
     * @throws IndexOutOfBoundsException If the index is >= the capacity of this Csd or < 0.
     */

    public void setCsd(int index, byte[] csd) {
        if (index >= csd.length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + csd.length);
        }

        this.csd[index] = csd;
    }

    /**
     * Returns the CSD at the specified index.
     * @param index The index of the CSD.
     * @return The CSD at the specified index.
     * @throws IndexOutOfBoundsException If the index is >= the capacity of this Csd or < 0.
     */

    public byte[] getCsd(int index) {
        if (index >= csd.length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + csd.length);
        }

        return csd[index];
    }

    /**
     * Removes the CSD at the specified index.
     * @param index The index of the CSD to remove.
     * @return True if the CSD was removed successfully, false otherwise.
     */

    public boolean removeCsd(int index){
        if (index >= csd.length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + csd.length);
        }
        if (csd[index] == null) {
            return false;
        }
        csd[index] = null;
        return true;
    }

    /**
     * Returns true if this Csd contains a CSD at the specified index.
     * @param index The index of the CSD.
     * @return True if this Csd contains a CSD at the specified index, false otherwise.
     */

    public boolean hasCsd(int index){
        if (index >= csd.length || index < 0) {
            return false;
        }
        return csd[index] != null;
    }

    /**
     * Removes all CSDs stored in this Csd.
     */

    public void clear() {
        for (int i = 0; i < csd.length; i++) {
            removeCsd(i);
        }
    }

    /**
     * Returns the number of CSDs stored in this Csd.
     */

    public int getSize() {
        int size = 0;
        for (byte[] bytes : csd) {
            if (bytes != null) {
                size++;
            }
        }
        return size;
    }

    /**
     * Returns the capacity of this Csd. (The maximum number of CSDs that can be stored)<br>
     * The capacity is set in the constructor and cannot be changed.
     * @return The capacity of this Csd.
     */

    public int getCapacity() {
        return csd.length;
    }

    /**
     * Returns the total length of all CSDs in bytes.
     * @return The total length of all CSDs in bytes.
     */

    public int getLength() {
        int length = 0;
        for (byte[] bytes : csd) {
            if (bytes != null) {
                length += bytes.length;
            }
        }
        return length;
    }

    /**
     * Creates deep copy of this Csd.
     * @return A deep copy of this Csd.
     */

    public Csd copy(){
        Csd csd = new Csd(getCapacity());
        for(int i = 0; i < getCapacity(); i++){
            csd.csd[i] = Arrays.copyOf(this.csd[i], this.csd[i].length);
        }
        return csd;
    }

    /**
     * Writes the Csd to the specified Csd.
     * @param csd The Csd to write to.
     * @return The specified Csd.
     * @throws NullPointerException If the specified Csd is null.
     * @throws IllegalArgumentException If the specified Csd has a different capacity than this Csd.
     */

    public Csd writeTo(@NonNull Csd csd){
        Objects.requireNonNull(csd);

        if(csd.getCapacity() < getCapacity())
            throw new IllegalArgumentException("Csd capacity must >= this Csd capacity");

        for(int i = 0; i < getCapacity(); i++){
            if (this.csd[i] != null) {
                csd.setCsd(i, this.csd[i]);
            }
        }
        return csd;
    }

}
