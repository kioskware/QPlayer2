package com.fivesoft.qplayer.core;

import android.util.Log;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.core.resolvers.AudioDecoderResolver;
import com.fivesoft.qplayer.core.resolvers.DataSourceResolver;
import com.fivesoft.qplayer.core.resolvers.MediaExtractorResolver;
import com.fivesoft.qplayer.core.resolvers.VideoDecoderResolver;
import com.fivesoft.qplayer.impl.decoder.audio.aac.AACDecoder;
import com.fivesoft.qplayer.impl.decoder.video.h264.H264Decoder;
import com.fivesoft.qplayer.impl.extractor.rtsp.RtspMediaExtractor;
import com.fivesoft.qplayer.impl.source.FileDataSource;
import com.fivesoft.qplayer.impl.source.SocketDataSource;

/**
 * This class is used to register all components of the api like:
 * <ul>
 *     <li>{@link DataSource}s</li>
 *     <li>{@link MediaExtractor}s</li>
 *     <li>{@link MediaDecoder}s</li>
 *     </ul>
 */

public class ComponentManifest {

    private ComponentManifest() {
    }

    /**
     * Mounts the component manifest.
     */

    public static void mount() {}

    /*
     * All component's creators must be registered here in static block.
     */

    static {
        Log.println(Log.ASSERT, "QPlayer", "Mounting component manifest");

        //Register sources
        DataSourceResolver.getInstance()
                .registerCreator(SocketDataSource.CREATOR)
                .registerCreator(FileDataSource.CREATOR);

        //Register extractors
        MediaExtractorResolver.getInstance()
                .registerCreator(RtspMediaExtractor.CREATOR);

        //Register decoders
        VideoDecoderResolver.getInstance()
                .registerCreator(H264Decoder.CREATOR);

        AudioDecoderResolver.getInstance()
                .registerCreator(AACDecoder.CREATOR);
    }

}
