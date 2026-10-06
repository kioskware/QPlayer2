package com.fivesoft.qplayer.impl.extractor.rtp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Frame;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.DatagramDataSource;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.impl.extractor.ts.TsDemuxer;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Selects elementary RTP or MP2T before reporting the decoder's sample format. */
public final class RtpStreamMediaExtractor extends MediaExtractor {
    private final RtpMediaExtractor transport;
    private final Tracks tracks = new Tracks();
    private final TsDemuxer ts;
    private boolean prepared;
    private boolean transportStream;
    private Sample pending;
    private long position = -1;
    private long sampleIndex = -1;
    private long lastSsrc = -1;

    public RtpStreamMediaExtractor(@NonNull DatagramDataSource source, @Nullable TrackSelector selector, @NonNull URI uri) {
        super(source, selector);
        Tracks configured = RtpTrackConfig.fromUri(uri);
        int program = 0;
        for (Track track : configured) {
            Object value = track.getMetadata("program");
            if (value != null) program = Integer.parseInt(value.toString());
        }
        if (RtpTrackConfig.isAutoDetectUri(uri)) configured.put(new RtpTransportStreamTrack(33));
        transport = new RtpMediaExtractor(source, TrackSelector.ALL, configured);
        ts = new TsDemuxer(trackSelector, program);
    }

    public RtpStreamMediaExtractor(@NonNull DatagramDataSource source, @NonNull URI uri) {
        this(source, null, uri);
    }

    public void setReorderDelay(int delayMs) { transport.setReorderDelay(delayMs); }
    public void setMaxPendingPackets(int count) { transport.setMaxPendingPackets(count); }
    public void setRtcpReportInterval(int intervalMs) { transport.setRtcpReportInterval(intervalMs); }

    @Override
    public void prepare(int timeout) throws IOException, InterruptedException {
        if (prepared) throw new IllegalStateException("Already prepared");
        ts.reset();
        tracks.clear();
        pending = null;
        lastSsrc = position = sampleIndex = -1;
        transport.prepare(timeout);
        long started = System.nanoTime();
        boolean decided = false;
        try {
            while (true) {
                Sample sample = readPacket(started, timeout);
                if (!decided) {
                    transportStream = sample.getTrack() instanceof RtpTransportStreamTrack;
                    decided = true;
                    if (!transportStream) {
                        for (Track track : transport.getTracks()) {
                            if (!(track instanceof RtpTransportStreamTrack)) tracks.put(track);
                        }
                    }
                }
                if (transportStream) {
                    consumeTs(sample);
                    if (ts.isProgramMapRead()) {
                        if (!ts.hasSelectedTracks()) throw new IOException("MPEG-TS has no selected supported H.264/AAC tracks");
                        tracks.set(ts.getTracks());
                        prepared = true;
                        return;
                    }
                } else if (!(sample.getTrack() instanceof RtpTransportStreamTrack) && trackSelector.selectTrack(sample.getTrack())) {
                    pending = sample;
                    prepared = true;
                    return;
                }
            }
        } catch (IOException | InterruptedException | RuntimeException error) {
            transport.close();
            throw error;
        }
    }

    @Override
    public Sample nextSample() throws IOException, InterruptedException {
        checkPrepared();
        long started = System.nanoTime();
        while (true) {
            checkPrepared();
            Sample sample;
            if (transportStream) {
                Frame frame = ts.poll();
                if (frame == null) {
                    consumeTs(readPacket(started, getTimeout()));
                    tracks.set(ts.getTracks());
                    continue;
                }
                sample = frame;
            } else {
                sample = pending;
                pending = null;
                if (sample == null) sample = readPacket(started, getTimeout());
                if (sample.getTrack() instanceof RtpTransportStreamTrack || !trackSelector.selectTrack(sample.getTrack())) continue;
            }
            sampleIndex++;
            position = Math.max(position, sample.getTimestamp());
            return sample;
        }
    }

    private void consumeTs(Sample sample) throws IOException {
        if (!(sample.getTrack() instanceof RtpTransportStreamTrack)) return;
        if (sample instanceof RtpSample) {
            RtpSample packet = (RtpSample) sample;
            if (lastSsrc != -1 && packet.ssrc != lastSsrc) ts.reset();
            else if (packet.discontinuity) ts.discontinuity();
            lastSsrc = packet.ssrc;
        }
        ts.consume(sample.getArray(), sample.getOffset(), sample.getLength(), sample.getTimestamp());
    }

    private Sample readPacket(long started, int timeout) throws IOException, InterruptedException {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (timeout > 0 && elapsed >= timeout) throw new TimeoutException(timeout);
        int previous = transport.getTimeout();
        int remaining = timeout <= 0 ? 0 : (int) Math.max(1, timeout - elapsed);
        transport.setTimeout(remaining);
        try { return transport.nextSample(); }
        finally { transport.setTimeout(previous); }
    }

    @Override
    public boolean isPrepared() { return prepared && transport.isPrepared(); }

    @NonNull
    @Override
    public Tracks getTracks() { checkPrepared(); return tracks; }

    @Override
    public int getSampleFormat() { return transportStream ? MediaDecoder.FORMAT_RAW : MediaDecoder.FORMAT_RTP; }

    @Override
    public long getPosition() { return position; }

    @Override
    public long getSampleIndex() { return sampleIndex; }

    @Override
    public void close() {
        prepared = false;
        pending = null;
        transport.close();
        ts.reset();
        tracks.clear();
    }

    @Override
    public void setAuthentication(@Nullable Authentication authentication) { transport.setAuthentication(authentication); }

    @Override
    protected void onTimeoutSet(int timeout) { transport.setTimeout(timeout); }

    private void checkPrepared() {
        if (!isPrepared()) throw new IllegalStateException("Not prepared, call prepare() first");
    }
}
