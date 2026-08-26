package com.fivesoft.qplayer.core.player;

import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.QPlayer;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.SubtitleReceiver;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.common.Util;
import com.fivesoft.qplayer.common.X;
import com.fivesoft.qplayer.common.buffer.Buffer;
import com.fivesoft.qplayer.common.measure.BitrateMeasure;
import com.fivesoft.qplayer.core.resolvers.DataSourceResolver;
import com.fivesoft.qplayer.core.resolvers.MediaExtractorResolver;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/*
 * Android QPlayer core implementation.
 * This is for internal use only.
 * For public API, see QPlayerFactory.getDefault() method.
 */

class QPlayerImpl implements QPlayer<Surface, android.media.AudioTrack, SubtitleReceiver> {


    //Internal fields
    private final Object mainThreadLock = new Object();
    private volatile MainThread mainThread;
    private volatile boolean started = false;
    private boolean isReleased = false;

    private final BitrateMeasure bitrateMeasure = new BitrateMeasure(50, 5000);

    //Config
    private volatile long bufferLatency = DEFAULT_BUFFER_LATENCY;
    private volatile float volume = DEFAULT_VOLUME;

    private int dataSourceConnectTimeout = DataSource.DEFAULT_CONNECT_TIMEOUT;
    private int dataSourceReadTimeout = DataSource.DEFAULT_IO_TIMEOUT;

    private MediaDecoderOutput.Creator<Surface> videoOutputCreator;
    private MediaDecoderOutput.Creator<android.media.AudioTrack> audioOutputCreator;
    private MediaDecoderOutput.Creator<SubtitleReceiver> subtitleOutputCreator;

    private TrackSelector trackSelector;
    private Authentication authentication;

    private volatile URI uri;

    //Components

    private volatile DataSource dataSource;

    //Caches

    private final Tracks tracks = new Tracks();

    @Override
    public void setMediaSource(@Nullable URI uri) {
        if (!Objects.equals(this.uri, uri)) {
            this.uri = uri;
            onURIChanged(uri);
        }
    }

    @Override
    public void setAuthentication(@Nullable Authentication auth) {
        this.authentication = auth;
    }

    @Override
    public Authentication getAuthentication() {
        return authentication;
    }

    @Override
    public void setPlayback(boolean playback) {
        if (playback) {
            start();
        } else {
            stop();
        }
    }

    @Override
    @Deprecated
    public void start() {
        started = true;
        ensureMainThread();
    }

    @Override
    @Deprecated
    public void stop() {
        started = false;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public int setBufferLatency(long latency) {
        if (latency < 0) {
            return -1;
        } else {
            bufferLatency = latency;
        }
        return 0;
    }

    @Override
    public void setTrackSelector(@Nullable TrackSelector selector) {
        if (trackSelector != selector) {
            this.trackSelector = selector;
            onTrackSelectorChanged(selector);
        }
    }

    @Override
    public void setVideoOutputCreator(@Nullable MediaDecoderOutput.Creator<Surface> creator) {
        this.videoOutputCreator = creator;
    }

    @Override
    public void setAudioOutputCreator(@Nullable MediaDecoderOutput.Creator<android.media.AudioTrack> creator) {
        this.audioOutputCreator = creator;
    }

    @Override
    public void setSubtitleOutputCreator(@Nullable MediaDecoderOutput.Creator<SubtitleReceiver> creator) {
        this.subtitleOutputCreator = creator;
    }

    @Nullable
    @Override
    public Tracks getTracks() {
        return tracks;
    }

    @Override
    public void release() {
        isReleased = true;
        started = false;
        synchronized (mainThreadLock) {
            if (mainThread != null) {
                mainThread.interrupt();
            }
            mainThread = null;
        }
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    @Override
    public double getTotalBitrate() {
        return bitrateMeasure.getBitrate();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        dataSourceConnectTimeout = Util.limit(timeout, 0, Integer.MAX_VALUE);
    }

    @Override
    public void setReadTimeout(int timeout) {
        dataSourceReadTimeout = Util.limit(timeout, 0, Integer.MAX_VALUE);
        Thread t = new Thread(() -> {
            DataSource ds = dataSource;
            if (ds != null) {
                ds.setTimeout(timeout);
            }
        });
        t.start();
    }

    //Internal methods

    private void onURIChanged(@Nullable URI uri) {
        if (uri == null) {
            dataSource = null;
        } else {
            Thread t = new Thread(() -> {
                dataSource = DataSourceResolver.resolveSource(uri);
                X.l("DataSource resolved: " + dataSource + " for " + uri);
                if (dataSource != null) {
                    dataSource.setTimeout(dataSourceReadTimeout);
                }
            });
            t.start();
        }
    }

    private void onTrackSelectorChanged(@Nullable TrackSelector selector) {
        //Restart data source and extractor to select another tracks
        this.trackSelector = selector;
        onURIChanged(uri);
    }

    private void ensureMainThread() {
        synchronized (mainThreadLock) {
            if (mainThread == null || !mainThread.isAlive()) {
                mainThread = new MainThread();
                mainThread.start();
            }
        }
    }

    private class MainThread extends Thread {

        private DataSource cDataSource;
        private MediaExtractor cExtractor;

        private DecodersManager decodersManager;

        @Override
        public void run() {
            cDataSource = dataSource;

            boolean cmpChanged;
            Sample sample;
            Buffer<Frame> buffer = null;

            while (!isInterrupted() && !isReleased && started) {
                try {
                    //Check if components were changed
                    cmpChanged = checkComponentsReferences();

                    if (!checkDataSource()) {
                        //DataSource is not ready
                        Util.sleep(1000);
                        continue;
                    }

                    int sampleFormat;
                    if (checkExtractor()) {
                        sampleFormat = cExtractor.getSampleFormat();
                        tracks.set(cExtractor.getTracks());
                    } else {
                        //Extractor is not ready
                        Util.sleep(1000);
                        continue;
                    }

                    if (cmpChanged || decodersManager == null || buffer == null) {
                        if (decodersManager != null) {
                            //Release old decoders
                            decodersManager.releaseAll();
                        }
                        //Components were changed, we need to close old decoders
                        decodersManager = DecodersManager.createDefault(videoOutputCreator,
                                audioOutputCreator, subtitleOutputCreator, sampleFormat);

                        if (buffer != null) {
                            //Release old buffer
                            buffer.destroy();
                        }
                        buffer = new Buffer<Frame>() {
                            @Override
                            protected void onFrame(Frame frame) {
                                if (decodersManager != null) {
                                    decodersManager.decode(frame);
                                }
                            }
                        };
                    }

                    buffer.setLatency(bufferLatency);

                    //Components are ready, we can read sample
                    try {
                        sample = cExtractor.nextSample();
                        if (sample != null) {
                            bitrateMeasure.countBytes(sample.getLength() + 4); //+4 for sample header.
                                                                               //This may depend on protocol, but let's treat it as a estimation
                            sample.getTrack().notifySampleReceived(sample);

                            Frame[] frame = decodersManager.feed(sample);
                            if (frame != null) {
                                for (Frame f : frame) {
                                    buffer.receive(f);
                                }
                            }
                        }
                    } catch (IOException | TimeoutException e) {
                        //Try reconnecting
                        Log.println(Log.ASSERT, "", "Error reading sample: " + e.getMessage());
                        Util.sleep(1000);
                        reconnectDataSource();
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }

            Util.sleep(1000);

            if (started) {
                //Restart thread
                //noinspection CallToThreadRun
                run();
            } else {
                //Release all resources
                Util.closeQuietly(cDataSource);
                Util.closeQuietly(cExtractor);
                if (decodersManager != null) {
                    decodersManager.releaseAll();
                }
            }
        }

        //Returns true if components were changed
        private boolean checkComponentsReferences() {
            boolean dataSourceChanged = cDataSource != dataSource;
            if (dataSourceChanged) {
                Util.closeQuietly(cDataSource);
                this.cDataSource = dataSource;
            }
            MediaExtractor oldExtractor = cExtractor;
            if (oldExtractor == null || dataSourceChanged) {
                URI uri = QPlayerImpl.this.uri;
                if (oldExtractor != null) {
                    Util.closeQuietly(oldExtractor);
                }
                if (uri != null) {
                    MediaExtractor.Descriptor descriptor = new MediaExtractor.Descriptor(dataSource, trackSelector, uri);
                    cExtractor = MediaExtractorResolver.resolveExtractor(descriptor);
                } else {
                    cExtractor = null;
                }
                return true;
            }
            return false;
        }

        //Returns true if dataSource is ready to use
        private boolean checkDataSource() {
            if (cDataSource == null) {
                return false;
            } else if (dataSource.isConnected()) {
                return true;
            } else {
                try {
                    reconnectDataSource();
                } catch (Exception e) {
                    return false;
                }
            }
            return true;
        }

        //Returns true if extractor is ready to use
        private boolean checkExtractor() {
            if (cExtractor == null) {
                if (uri != null && cDataSource != null) {
                    MediaExtractor.Descriptor descriptor = new MediaExtractor.Descriptor(dataSource, trackSelector, uri);
                    cExtractor = MediaExtractorResolver.resolveExtractor(descriptor);
                }
            } else if (cExtractor.isPrepared()) {
                return true;
            }

            if(cExtractor == null) {
                return false;
            }

            try {
                cExtractor.setAuthentication(authentication);
                cExtractor.prepare(10000);
            } catch (Exception e) {
                reconnectDataSource();
                return false;
            }

            return cExtractor.isPrepared();
        }

        private void reconnectDataSource() {
            if (cDataSource != null) {
                try {
                    Util.closeQuietly(cExtractor);
                    cDataSource.close();
                    cDataSource.connect(dataSourceConnectTimeout);
                    cExtractor = null;
                } catch (Exception e) {
                    //Ignore
                }
            }
        }

    }

}