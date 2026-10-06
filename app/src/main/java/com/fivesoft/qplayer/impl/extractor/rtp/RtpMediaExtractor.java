package com.fivesoft.qplayer.impl.extractor.rtp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.DatagramDataSource;
import com.fivesoft.qplayer.base.components.MediaDecoder;
import com.fivesoft.qplayer.base.components.MediaExtractor;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.common.Constants;
import com.fivesoft.qplayer.common.TimeoutException;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.common.resolver.Creator;

import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** Direct RTP/UDP extraction using the same FORMAT_RTP decoder pipeline as RTSP. */
public class RtpMediaExtractor extends MediaExtractor {
    public static final int DEFAULT_REORDER_DELAY_MS = 50;
    public static final int DEFAULT_MAX_PENDING_PACKETS = 32;

    public static final Creator<Descriptor, MediaExtractor> CREATOR = new Creator<Descriptor, MediaExtractor>() {
        @Override
        public int accept(Descriptor descriptor) {
            return descriptor != null && descriptor.dataSource instanceof DatagramDataSource
                    && descriptor.uri != null && ("rtp".equalsIgnoreCase(descriptor.uri.getScheme())
                    || "udp".equalsIgnoreCase(descriptor.uri.getScheme())) ? 1 : 0;
        }

        @Nullable
        @Override
        public MediaExtractor create(Descriptor descriptor) {
            return accept(descriptor) > 0 ? new RtpMediaExtractor(
                    (DatagramDataSource) descriptor.dataSource, descriptor.trackSelector,
                    RtpTrackConfig.fromUri(descriptor.uri)) : null;
        }
    };

    private final Tracks tracks;
    private final DatagramDataSource datagramSource;
    private final byte[] readBuffer = new byte[DatagramDataSource.MAX_DATAGRAM_SIZE];
    private final Map<Integer, StreamState> streams = new LinkedHashMap<>();
    private volatile boolean prepared;
    private volatile long position = Constants.UNKNOWN_VALUE;
    private volatile long sampleIndex = Constants.UNKNOWN_VALUE;
    private int reorderDelayMs = DEFAULT_REORDER_DELAY_MS;
    private int maxPendingPackets = DEFAULT_MAX_PENDING_PACKETS;

    public RtpMediaExtractor(@NonNull DatagramDataSource source, @Nullable TrackSelector selector,
                             @NonNull Tracks tracks) {
        super(source, selector);
        datagramSource = source;
        this.tracks = new Tracks(tracks);
    }

    public RtpMediaExtractor(@NonNull DatagramDataSource source, @NonNull URI uri) {
        this(source, null, RtpTrackConfig.fromUri(uri));
    }

    public RtpMediaExtractor(@NonNull DatagramDataSource source, @NonNull String sdp) {
        this(source, null, RtpTrackConfig.fromSdp(sdp));
    }

    /** Set before prepare(); zero disables waiting for reordered packets. */
    public void setReorderDelay(int delayMs) {
        if (prepared) throw new IllegalStateException("Already prepared");
        if (delayMs < 0) throw new IllegalArgumentException("Negative reorder delay");
        reorderDelayMs = delayMs;
    }

    public void setMaxPendingPackets(int count) {
        if (prepared) throw new IllegalStateException("Already prepared");
        if (count <= 0 || count > 1024) throw new IllegalArgumentException("Invalid packet queue capacity");
        maxPendingPackets = count;
    }

    @Override
    public void prepare(int timeout) throws IOException {
        if (prepared) throw new IllegalStateException("Already prepared");
        if (!dataSource.isConnected()) throw new IllegalStateException("Data source is not connected");
        streams.clear();
        for (Track track : tracks) {
            RtpTrackConfig.validateTrack(track);
            if (streams.containsKey(track.getPayloadType())) {
                throw new IOException("Ambiguous RTP payload type: " + track.getPayloadType());
            }
            if (trackSelector.selectTrack(track)) streams.put(track.getPayloadType(), new StreamState(track));
        }
        if (streams.isEmpty()) throw new IOException("No RTP tracks selected");
        sampleIndex = Constants.UNKNOWN_VALUE;
        position = Constants.UNKNOWN_VALUE;
        prepared = true;
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Nullable
    @Override
    public Sample nextSample() throws IOException, InterruptedException {
        checkPrepared();
        int timeout = effectiveTimeout();
        long started = System.nanoTime();
        while (true) {
            checkPrepared();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long now = System.nanoTime();
            int waitMs = 100;
            for (StreamState stream : streams.values()) {
                RtpSample sample = stream.poll(now);
                if (sample != null) {
                    position = Math.max(position, sample.getTimestamp());
                    sampleIndex++;
                    return sample;
                }
                waitMs = Math.min(waitMs, stream.waitMs(now));
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now - started);
            if (timeout > 0) {
                if (elapsed >= timeout) throw new TimeoutException(timeout);
                waitMs = (int) Math.min(waitMs, timeout - elapsed);
            }
            int length;
            try {
                length = datagramSource.readDatagram(readBuffer, 0, readBuffer.length, Math.max(1, waitMs));
            } catch (SocketTimeoutException pollTimeout) {
                continue;
            }
            if (RtcpParser.isRtcp(readBuffer, length)) {
                consumeRtcp(length);
                continue;
            }
            RtpParser.RtpPacket packet = RtpParser.parsePacket(readBuffer, 0, length);
            if (packet == null || packet.header.payloadSize == 0) continue;
            StreamState stream = streams.get(packet.header.payloadType);
            if (stream != null) stream.offer(packet, readBuffer, length, System.nanoTime());
        }
    }

    private void consumeRtcp(int length) {
        List<RtcpParser.Report> reports = RtcpParser.parsePacket(readBuffer, length);
        if (reports == null) return;
        for (RtcpParser.Report report : reports) {
            for (StreamState stream : streams.values()) {
                if (stream.ssrc != report.ssrc) continue;
                if (report.bye) {
                    stream.retiredSources.add(report.ssrc);
                    stream.pending.clear();
                    stream.ssrc = -1;
                    stream.discontinuity = true;
                } else {
                    stream.track.setMetadata("rtcp-ntp-timestamp-ms", report.ntpTimestampMs);
                    stream.track.setMetadata("rtcp-rtp-timestamp", report.rtpTimestamp);
                }
            }
        }
    }

    private int effectiveTimeout() {
        int extractorTimeout = getTimeout();
        int sourceTimeout = dataSource.getTimeout();
        if (extractorTimeout <= 0) return sourceTimeout;
        return sourceTimeout <= 0 ? extractorTimeout : Math.min(extractorTimeout, sourceTimeout);
    }

    @NonNull
    @Override
    public Tracks getTracks() {
        checkPrepared();
        return tracks;
    }

    @Override
    public long getPosition() { return position; }

    @Override
    public long getSampleIndex() { return sampleIndex; }

    @Override
    public int getSampleFormat() { return MediaDecoder.FORMAT_RTP; }

    @Override
    public void close() {
        prepared = false;
    }

    @Override
    public void setAuthentication(@Nullable Authentication authentication) {
        // Direct RTP has no authentication handshake.
    }

    @Override
    protected void onTimeoutSet(int timeout) {}

    private void checkPrepared() {
        if (!prepared) throw new IllegalStateException("Not prepared, call prepare() first");
    }

    private final class StreamState {
        final Track track;
        final TreeMap<Long, PendingPacket> pending = new TreeMap<>();
        final Set<Long> retiredSources = new HashSet<>();
        long ssrc = -1;
        long nextSequence;
        long lastRtpTimestamp = -1;
        long timestampTicks;
        long baseTimeMs;
        long lastTimeMs;
        boolean discontinuity;

        StreamState(Track track) { this.track = track; }

        void offer(RtpParser.RtpPacket packet, byte[] data, int length, long now) {
            long source = packet.header.ssrc;
            if (retiredSources.contains(source)) return;
            if (source != ssrc) {
                if (ssrc != -1) retiredSources.add(ssrc);
                pending.clear();
                discontinuity = lastRtpTimestamp != -1;
                baseTimeMs = lastTimeMs;
                timestampTicks = 0;
                lastRtpTimestamp = -1;
                ssrc = source;
                nextSequence = packet.header.sequenceNumber;
            }
            int distance = (packet.header.sequenceNumber - (int) nextSequence) & 0xffff;
            if (distance >= 0x8000) return; // Late/duplicate packet already emitted.
            long sequence = nextSequence + distance;
            if (!pending.containsKey(sequence)) {
                pending.put(sequence, new PendingPacket(packet, Arrays.copyOf(data, length), now));
            }
        }

        RtpSample poll(long now) {
            if (pending.isEmpty()) return null;
            Map.Entry<Long, PendingPacket> first = pending.firstEntry();
            if (first.getKey() != nextSequence && pending.size() < maxPendingPackets
                    && TimeUnit.NANOSECONDS.toMillis(now - first.getValue().receivedAt) < reorderDelayMs) {
                return null;
            }
            pending.pollFirstEntry();
            boolean gap = discontinuity || first.getKey() != nextSequence;
            discontinuity = false;
            nextSequence = first.getKey() + 1;
            PendingPacket packet = first.getValue();
            RtpParser.RtpHeader header = packet.packet.header;
            if (lastRtpTimestamp != -1) timestampTicks += (int) (header.timestamp - lastRtpTimestamp);
            lastRtpTimestamp = header.timestamp;
            long timeMs = baseTimeMs + timestampTicks * 1000 / track.getClockRate();
            lastTimeMs = Math.max(lastTimeMs, timeMs);
            byte[] data = packet.data;
            int offset = packet.packet.payloadOffset;
            if (!isReuseBuffersAllowed()) {
                data = Arrays.copyOfRange(data, offset, offset + header.payloadSize);
                offset = 0;
            }
            return new RtpSample(data, offset, header.payloadSize, timeMs, track, header, gap);
        }

        int waitMs(long now) {
            if (pending.isEmpty()) return 100;
            long age = TimeUnit.NANOSECONDS.toMillis(now - pending.firstEntry().getValue().receivedAt);
            return (int) Math.max(1, reorderDelayMs - age);
        }
    }

    private static final class PendingPacket {
        final RtpParser.RtpPacket packet;
        final byte[] data;
        final long receivedAt;
        PendingPacket(RtpParser.RtpPacket packet, byte[] data, long receivedAt) {
            this.packet = packet;
            this.data = data;
            this.receivedAt = receivedAt;
        }
    }
}
