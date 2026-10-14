package com.fivesoft.qplayer.common.buffer;


import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

public class SortedSynchronizedList<T> {

    private final ArrayList<T> parent = new ArrayList<>();
    private final Comparator<T> comparator;
    private volatile boolean changedAfterGet = true;

    public SortedSynchronizedList(Comparator<T> comparator) {
        this.comparator = Objects.requireNonNull(comparator, "Comparator cannot be null.");
    }

    public void add(T obj){
        synchronized (parent){
            int s = parent.size();

            if(s == 0) {
                parent.add(obj);
                changedAfterGet = false;
                notifyChanged();
                return;
            }

            boolean added = false;

            for (int i = 0; i < s; i++) {
                T o = parent.get(i);
                if(comparator.compare(obj, o) < 0) {
                    if(i == 0) {
                        changedAfterGet = true;
                    }
                    parent.add(i, obj);
                    added = true;
                    notifyChanged();
                    break;
                }
            }

            if(!added){
                parent.add(obj);
                notifyChanged();
            }
        }
    }

    public boolean modified(){
        return changedAfterGet;
    }

    public T getFirst(){
        synchronized (parent) {
            return get(0);
        }
    }

    public T get(int index){
        synchronized (parent){
            changedAfterGet = false;
            return parent.get(index);
        }
    }

    @Nullable
    public T getLast(){
        synchronized (parent){
            int s = parent.size();
            changedAfterGet = false;
            if(s == 0)
                return null;
            return parent.get(s - 1);
        }
    }

    public boolean remove(int index){
        synchronized (parent){
            if(index < 0 || index >= parent.size())
                return false;

            parent.remove(index);
            notifyChanged();
            return true;
        }
    }

    public void clear(){
        synchronized (parent){
            parent.clear();
            notifyChanged();
        }
    }

    public int size(){
        synchronized (parent) {
            return parent.size();
        }
    }

    private void notifyChanged(){

    }

}
