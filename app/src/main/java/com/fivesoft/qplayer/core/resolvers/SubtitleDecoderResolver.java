package com.fivesoft.qplayer.core.resolvers;

import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.common.resolver.Resolver;
import com.fivesoft.qplayer.core.decoders.SubtitleDecoder;

public class SubtitleDecoderResolver extends Resolver<MediaDecoder.Descriptor, SubtitleDecoder> {

    private final static SubtitleDecoderResolver instance = new SubtitleDecoderResolver();

    private SubtitleDecoderResolver() {
        //Prevent instantiation
    }

    public static SubtitleDecoderResolver getInstance() {
        return instance;
    }

    public SubtitleDecoderResolver registerCreator(Creator<MediaDecoder.Descriptor, SubtitleDecoder> creator) {
        register(creator);
        return this;
    }

    public static SubtitleDecoder resolveDecoder(MediaDecoder.Descriptor descriptor) {
        return getInstance().resolve(descriptor);
    }

}
