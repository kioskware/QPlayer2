# Direct RTP streams

QPlayer receives RTP over UDP through its existing component pipeline:

```text
URI -> DataSourceResolver -> RtpDataSource / UdpDataSource
    -> MediaExtractorResolver -> RtpMediaExtractor
    -> FORMAT_RTP -> H264Decoder / AACDecoder
```

The sources and extractor are registered in `ComponentManifest`. The public player,
view and Compose APIs accept RTP URIs without additional component registration.

## Playback

```java
// H.264: listen on local RTP port 5004 and RTCP port 5005.
view.setMediaSource("rtp://0.0.0.0:5004");
view.setPlayback(true);

// Join the multicast group on the RTP/RTCP port pair.
view.setMediaSource("rtp://239.10.10.10:5004");

// MPEG4-GENERIC AAC-LC, stereo, 48 kHz; receive RTP/RTCP on 5006/5007.
view.setMediaSource("rtp://0.0.0.0:5006?codec=aac&payload-type=97"
        + "&clock-rate=48000&channels=2&mode=AAC-hbr&config=1190");

// RTP and RTCP multiplexed on one port.
view.setMediaSource("rtp://0.0.0.0:5004?rtcp-mux=true");
```

For unicast, the URI port is a **local listening port**. The source binds to the
wildcard address and waits for the sender; opening a URI does not ask a server to
start sending. For multicast, the URI host identifies the group to join.
An explicit nonzero port is required by the source resolver.

`udp://host:port` also selects the RTP extractor, with one socket and RTCP mux.
It expects RTP packets, not an arbitrary UDP byte stream or MPEG-TS datagrams.
`rtsp://` continues to use the existing RTSP/TCP transport and session setup.

## URI parameters

| Parameter | Default | Purpose |
| --- | --- | --- |
| `codec` | `h264` | `h264`, `aac`, or `mpeg4-generic` |
| `payload-type` | `96` | RTP payload type; match the sender's mapping |
| `clock-rate` | `90000` for H.264, `44100` for AAC | Timestamp clock in Hz |
| `channels` | `2` | AAC output channel count |
| `mode` | `AAC-hbr` | AAC RTP mode: `AAC-hbr` or `AAC-lbr` |
| `config` | Required for AAC | Hexadecimal AudioSpecificConfig, e.g. `1210` for AAC-LC stereo 44.1 kHz |
| `sprop-parameter-sets` | None | Base64 SPS and PPS separated by a comma; optional when H.264 sends them in-band |
| `packetization-mode` | Unspecified | H.264 mode `0` or `1`; mode `2` is rejected |
| `rtcp-mux` | `false` for `rtp://` | Use one port for RTP and RTCP |
| `rtcp-port` | RTP port + 1 | Explicit local RTCP listening port |
| `sdp` | None | URL-encoded SDP text; replaces the single-track URI codec configuration |

AAC also accepts `sizeLength`, `indexLength`, `indexDeltaLength`, and
`constantDuration`. Keys are case-insensitive in URI and SDP configuration.
The default AU header lengths are 13/3/3 bits for HBR and 6/2/2 bits for LBR;
the default access-unit duration is 1024 clock ticks. Configure
`constantDuration` when the sender uses another duration.

URL-encode parameter values, particularly SDP, `+` in Base64, and characters
such as `&` or `=` inside values. Unsupported codecs, ambiguous payload mappings,
invalid clocks and unsupported AAC layouts fail explicitly during configuration.
Payload types 64–95 are reserved here to distinguish RTP from multiplexed RTCP.

## SDP and custom transports

RTP does not carry codec descriptions. Provide them through URI parameters, SDP
(`rtpmap`/`fmtp`), or the library's existing `Track`/`Tracks` model.

```java
String sdp = "v=0\r\n"
        + "m=video 5004 RTP/AVP 96\r\n"
        + "a=rtpmap:96 H264/90000\r\n"
        + "a=fmtp:96 packetization-mode=1\r\n";

String encoded = java.net.URLEncoder.encode(sdp, "UTF-8");
view.setMediaSource("rtp://0.0.0.0:5004?sdp=" + encoded);
```

For direct component use:

```java
try (RtpDataSource source = new RtpDataSource(URI.create("rtp://0.0.0.0:5004"))) {
    source.setTimeout(10000);
    source.connect();
    try (RtpMediaExtractor extractor = new RtpMediaExtractor(source, sdp)) {
        extractor.setReorderDelay(50);
        extractor.setMaxPendingPackets(32);
        extractor.setRtcpReportInterval(5000);
        extractor.prepare(10000);
        Sample sample = extractor.nextSample();
        // Feed samples to the existing FORMAT_RTP decoder pipeline.
    }
}
```

`RtpMediaExtractor(source, selector, tracks)` supports custom tracks and the
existing `TrackSelector` API. Custom datagram transports extend
`DatagramDataSource`; its `readDatagram` method preserves packet boundaries.
`sendControlDatagram` is optional and allows transports to return RTCP feedback.
The ordinary `InputStream` remains available, but RTP extraction uses datagrams.
Avoid mixing both reading APIs on the same source.

One source receives one RTP session/port pair. Several distinct payload types can
share that source. SDP media connection addresses and ports describe the session;
they do not open additional sockets. For separate media ports, create a source
and extractor for each session with its own track configuration. Passing a `.sdp`
file URI does not automatically start a multi-port session.

The caller connects and closes the source. Closing an extractor sends RTCP BYE
when feedback is enabled, but leaves the source open. The same extractor can be
prepared again; its `Tracks` collection keeps the same identity. Samples keep
their own data by default. Allowing buffer reuse avoids an extra payload copy;
sample data is still independent of the next received datagram.

## Multicast on Android

The application needs `android.permission.INTERNET`. On Wi-Fi networks that
filter multicast, hold a `WifiManager.MulticastLock` for the lifetime of playback
and declare `android.permission.CHANGE_WIFI_MULTICAST_STATE` in the application's
manifest. The application owns the lock and releases it when playback ends.

When using components directly, call `source.setNetworkInterface(networkInterface)`
before connecting to select the multicast interface. Membership is dropped when
the receiving socket closes. The older `MulticastSocketDataSource` constructors
now delegate to the working UDP implementation.

## Packet handling and supported formats

- RTP v2 fixed headers, CSRC lists, header extensions, padding and unsigned fields.
- Bounded reordering queues (32 packets, 50 ms by default), duplicate/late packet
  rejection, 16-bit sequence wrap, 32-bit timestamp wrap and sender restart recovery.
- One active SSRC per track; a source change discards pending fragments and marks
  a discontinuity. Recent retired sources cannot mix their packets into the new stream.
- H.264 packetization modes 0/1: single NAL units, STAP-A and FU-A. Lost or
  inconsistent fragments are discarded; frame size limits apply to every path.
- MPEG4-GENERIC AAC HBR/LBR: one or several access units per packet, per-unit
  timestamps, fragmented access units, explicit header lengths and sample offsets.
- Separate RTP/RTCP ports and RTCP mux; compound RTCP parsing, sender reports,
  shared NTP timing between tracks, receiver reports with loss/jitter/LSR/DLSR,
  SDES CNAME and BYE. Feedback returns to the control sender or multicast group.
- I/O deadlines, interruption, closing blocked reads, reconnecting and track selection.

The codec scope follows QPlayer's existing H.264 and AAC decoders. H.264
interleaved mode 2, AAC interleaving/auxiliary/CTS/DTS header extensions, LATM,
SRTP, H.265, MPEG-TS and retransmission/FEC payload formats are unsupported.
The extractor uses millisecond sample timestamps, following the library's convention.

The implementation follows [RTP/RTCP (RFC 3550)](https://www.rfc-editor.org/rfc/rfc3550),
[H.264 RTP payloads (RFC 6184)](https://www.rfc-editor.org/rfc/rfc6184),
[MPEG4-GENERIC payloads (RFC 3640)](https://www.rfc-editor.org/rfc/rfc3640), and
[SDP (RFC 8866)](https://www.rfc-editor.org/rfc/rfc8866) within this codec scope.

## Verification

Run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` on Windows.
Tests cover malformed datagrams, reordering, packet loss, wraparound, restarts,
codec aggregation/fragmentation, RTCP reports, port pairs and feedback. UDP,
RTCP and multicast integration tests use real local network sockets; the multicast
test is skipped only when no suitable multicast interface exists.
