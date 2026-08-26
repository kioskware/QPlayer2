package com.fivesoft.qplayer.core.resolvers;

import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.common.resolver.Creator;
import com.fivesoft.qplayer.common.resolver.Resolver;
import com.fivesoft.qplayer.core.decoders.AudioDecoder;

public class AudioDecoderResolver extends Resolver<MediaDecoder.Descriptor, AudioDecoder> {

    private final static AudioDecoderResolver instance = new AudioDecoderResolver();

    private AudioDecoderResolver() {
        //Prevent instantiation
    }

    public static AudioDecoderResolver getInstance() {
        return instance;
    }

    public AudioDecoderResolver registerCreator(Creator<MediaDecoder.Descriptor, AudioDecoder> creator) {
        register(creator);
        return this;
    }

    public static AudioDecoder resolveDecoder(MediaDecoder.Descriptor descriptor) {
        return getInstance().resolve(descriptor);
    }

}
