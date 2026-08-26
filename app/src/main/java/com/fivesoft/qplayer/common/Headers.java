package com.fivesoft.qplayer.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Headers implements Iterable<Header> {

    private final List<Header> headers = new ArrayList<>();

    public void add(String name, String value) {
        headers.add(new Header(name, value));
    }

    public void add(Header header) {
        headers.add(header);
    }

    public void addAll(Iterable<Header> headers) {
        for(Header header : headers)
            add(header);
    }

    public void put(String name, String value) {
        remove(name);
        add(name, value);
    }

    @Nullable
    public List<String> getAll(String name) {
        List<String> res = new ArrayList<>();
        for(Header header : headers) {
            if(header != null && header.name.equalsIgnoreCase(name))
                res.add(header.value);
        }
        return res;
    }

    public String get(String name) {
        for (Header header : headers) {
            if (header != null && header.name.equalsIgnoreCase(name))
                return header.value;
        }
        return null;
    }

    public void remove(String name) {
        headers.removeIf(header -> header == null || header.name.equalsIgnoreCase(name));
    }

    public void clear() {
        headers.clear();
    }

    public boolean contains(String name) {
        return headers.stream().anyMatch(header -> header != null && header.name.equalsIgnoreCase(name));
    }

    @NonNull
    @Override
    public Iterator<Header> iterator() {
        return headers.iterator();
    }

    private static String prepare(String str) {
        if(str == null)
            return null;
        return str.toLowerCase();
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(Header header : this) {
            sb.append(header.toString());
            sb.append("\r\n");
        }
        return sb.toString();
    }

}
