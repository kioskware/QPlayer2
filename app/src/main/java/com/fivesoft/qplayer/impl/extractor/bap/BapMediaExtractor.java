package com.fivesoft.qplayer.impl.extractor.bap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.TrackSelector;

import java.io.IOException;

public class BapMediaExtractor extends MediaExtractor {

    private Tracks tracks = new Tracks();

    public BapMediaExtractor(@NonNull DataSource dataSource, @Nullable TrackSelector trackSelector) {
        super(dataSource, trackSelector);
    }

    public BapMediaExtractor(@NonNull DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void prepare(int timeout) throws IOException, TimeoutException, InterruptedException {

    }

    @Override
    public boolean isPrepared() {
        return false;
    }

    @Override
    public Sample nextSample() throws IOException, TimeoutException, IllegalStateException, InterruptedException {
        return null;
    }

    @NonNull
    @Override
    public Tracks getTracks() throws IllegalStateException, RuntimeException {
        return tracks;
    }

    @Override
    public long getPosition() {
        return Constants.UNKNOWN_VALUE;
    }

    @Override
    public long getSampleIndex() {
        return Constants.UNKNOWN_VALUE;
    }

    @Override
    public int getSampleFormat() {
        return MediaDecoder.FORMAT_ANNEX_B;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void setAuthentication(@Nullable Authentication authentication) {

    }

    @Override
    protected void onTimeoutSet(int timeout) throws IOException {

    }

}
