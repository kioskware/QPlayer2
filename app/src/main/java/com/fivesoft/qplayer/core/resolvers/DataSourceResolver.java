package com.fivesoft.qplayer.core.resolvers;

import androidx.annotation.NonNull;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.common.resolver.Resolver;

import java.net.URI;

public class DataSourceResolver extends Resolver<URI, DataSource> {

    private final static DataSourceResolver instance = new DataSourceResolver();

    private DataSourceResolver() {
        //Prevent instantiation
    }

    public static DataSourceResolver getInstance() {
        return instance;
    }

    public DataSourceResolver registerCreator(@NonNull Creator<URI, DataSource> creator) {
        register(creator);
        return this;
    }

    public static DataSource resolveSource(@NonNull URI uri) {
        return getInstance().resolve(uri);
    }

}
