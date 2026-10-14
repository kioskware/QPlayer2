package com.fivesoft.qplayer.core;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Authentication;
import com.fivesoft.qplayer.base.QPlayer;
import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.base.components.MediaDecoderOutput;
import com.fivesoft.qplayer.base.track.AudioTrack;
import com.fivesoft.qplayer.base.track.SubtitleTrack;
import com.fivesoft.qplayer.base.track.Track;
import com.fivesoft.qplayer.base.track.TrackListener;
import com.fivesoft.qplayer.base.track.Tracks;
import com.fivesoft.qplayer.base.track.VideoTrack;
import com.fivesoft.qplayer.common.SubtitleReceiver;
import com.fivesoft.qplayer.common.TrackSelector;
import com.fivesoft.qplayer.core.player.QPlayerFactory;

import java.net.URI;
import java.util.Objects;

public class QPlayerView extends ViewGroup {

    static {
        ComponentManifest.mount();
    }

    private final QPlayer<Surface, android.media.AudioTrack, SubtitleReceiver>
            player = QPlayerFactory.createDefault();

    private final TrackListener trackListener = new TrackListener() {
        @Override
        public void onTrackFieldChanged(@NonNull Track track, @NonNull String field, @Nullable Object value) {
            if (track instanceof VideoTrack && (VideoTrack.FIELD_WIDTH.equals(field) ||
                    VideoTrack.FIELD_HEIGHT.equals(field) ||
                    VideoTrack.FIELD_PIXEL_ASPECT_RATIO.equals(field))) {
                updatePlayerSize();
            }
        }

        @Override
        public void onSampleReceived(@NonNull Track track, @NonNull Sample sample) {

        }

        @Override
        public void onFrameRendered(@NonNull Track track, long timestamp) {
            if (!enoughFramesRendered && track.getStatRenderedFrames() >= firstFramesCount) {
                enoughFramesRendered = true;
                changeSurfaceVisibility(true);
            }
        }
    };

    private final String TRACK_LISTENER_ID = "QPlayerViewTrackListener#" + hashCode();

    private final SurfaceView surfaceView;
    private final View shutterView;
    private final MediaDecoderOutput<Surface> videoOutput = new MediaDecoderOutput<>(null, null);

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback2() {
        @Override
        public void surfaceRedrawNeeded(@NonNull SurfaceHolder holder) {
            videoOutput.setRenderer(holder.getSurface());
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            videoOutput.setRenderer(holder.getSurface());
            if (enoughFramesRendered) {
                changeSurfaceVisibility(false);
                enoughFramesRendered = false;
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            videoOutput.setRenderer(holder.getSurface());
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            videoOutput.setRenderer(null);
            if (enoughFramesRendered) {
                changeSurfaceVisibility(false);
                enoughFramesRendered = false;
            }
        }
    };

    private volatile VideoTrack currentVideoTrack;
    private volatile AudioTrack currentAudioTrack;
    private volatile SubtitleTrack currentSubtitleTrack;
    private boolean isStarted = false;

    private boolean enoughFramesRendered = false;

    private boolean videoEnabled = true, audioEnabled = true, subtitlesEnabled = false;

    @NonNull
    private KeepScreenOnMode keepScreenOnMode = KeepScreenOnMode.WHEN_STARTED;

    private int firstFramesCount = 5;
    private float aspectRatio = Float.NaN;

    public QPlayerView(@NonNull Context context) {
        this(context, null, 0);
    }

    public QPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        //Setup views
        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(surfaceCallback);
        addView(surfaceView);

        shutterView = new View(context);
        shutterView.setBackgroundColor(Color.BLACK);
        addView(shutterView);

        //Setup player
        MediaDecoderOutput.Creator<Surface> videoOutputCreator = new MediaDecoderOutput.Creator<Surface>() {
            @Override
            public MediaDecoderOutput<Surface> create(Track t) {
                if (t instanceof VideoTrack) {
                    updateVideoTrack((VideoTrack) t);
                    return videoOutput;
                }
                return null;
            }
        };

        player.setVideoOutputCreator(videoOutputCreator);
        player.setAudioOutputCreator(DefaultAudioOutputCreator.INSTANCE);
        player.setTrackSelector(new MyTrackSelector(videoEnabled, audioEnabled, subtitlesEnabled));

    }

    /**
     * Sets media source to be played.<br>
     * @param uri Media source to be played or null to release current source.<br>
     *            Empty or only whitespace uri will be treated as null.
     * @return true if uri was set successfully, false otherwise.
     */

    public QPlayerView setMediaSource(@NonNull String uri) {
        player.setMediaSource(uri);
        return this;
    }

    /**
     * Sets media source to be played.<br>
     * @param uri Media source to be played or null to release current source.
     */

    public QPlayerView setMediaSource(@NonNull URI uri) {
        player.setMediaSource(uri);
        return this;
    }

    public QPlayerView setBufferLatency(long latency) {
        player.setBufferLatency(latency);
        return this;
    }

    public QPlayerView setDecoderLowLatency(boolean lowLatency) {
        player.setDecoderLowLatency(lowLatency);
        return this;
    }
    public QPlayerView setAuthentication(@Nullable Authentication auth) {
        player.setAuthentication(auth);
        return this;
    }

    /**
     * Sets if video should be played when available.<br>
     * @param enabled true if video should be played, false otherwise.
     * @return This instance for chaining
     */

    public QPlayerView setVideoEnabled(boolean enabled) {
        if (videoEnabled != enabled) {
            videoEnabled = enabled;
            player.setTrackSelector(new MyTrackSelector(videoEnabled, audioEnabled, subtitlesEnabled));
        }
        return this;
    }

    /**
     * Sets if audio should be played when available.<br>
     * @param enabled true if audio should be played, false otherwise.
     * @return This instance for chaining
     */

    public QPlayerView setAudioEnabled(boolean enabled) {
        if (audioEnabled != enabled) {
            audioEnabled = enabled;
            player.setTrackSelector(new MyTrackSelector(videoEnabled, audioEnabled, subtitlesEnabled));
        }
        return this;
    }

    public QPlayerView setVolume(float volume) {
        player.setVolume(volume);
        return this;
    }

    /**
     * Sets if subtitles should be displayed when available.<br>
     * @param enabled true if subtitles should be displayed, false otherwise.
     * @return This instance for chaining
     */

    public QPlayerView setSubtitlesEnabled(boolean enabled) {
        if (subtitlesEnabled != enabled) {
            subtitlesEnabled = enabled;
            player.setTrackSelector(new MyTrackSelector(videoEnabled, audioEnabled, subtitlesEnabled));
        }
        return this;
    }

    /**
     * Sets data source connection timeout in milliseconds.<br>
     * @param timeout The timeout in milliseconds.
     */

    public QPlayerView setConnectTimeout(int timeout) {
        player.setConnectTimeout(timeout);
        return this;
    }

    /**
     * Sets data source read timeout in milliseconds.<br>
     * @param timeout The timeout in milliseconds.
     */

    public QPlayerView setReadTimeout(int timeout) {
        player.setReadTimeout(timeout);
        return this;
    }

    /**
     * Sets keep screen on mode.<br>
     * @param mode Keep screen on mode to be used.
     * @return This instance for chaining
     * @see KeepScreenOnMode KeepScreenOnMode for more information.
     */

    public QPlayerView setKeepScreenOnMode(@NonNull KeepScreenOnMode mode) {
        this.keepScreenOnMode = Objects.requireNonNull(mode);
        updateKeepScreenOnMode();
        return this;
    }

    /**
     * Set the number of frames to be decoded before the first frame is displayed.<br>
     * This won't introduce any additional latency, given number of first frames will not be displayed.<br>
     * First frame counter is reset when the player is stopped or started.
     *
     * @param count number of frames to be decoded before the first frame is displayed
     * @return this for chaining
     */

    public QPlayerView setFirstFramesCount(int count) {
        firstFramesCount = Math.max(0, count);
        return this;
    }

    @NonNull
    public Tracks getTracks() {
        return player.getTracks();
    }

    /**
     * Gets latest video track or null if there is no video track available.
     * @return Latest video track or null if there is no video track available.
     */

    @Nullable
    public VideoTrack getVideoTrack() {
        return currentVideoTrack;
    }

    /**
     * Sets whether the player should play or not.<br>
     * @param playback true to start playback, false to stop playback.
     */
    public void setPlayback(boolean playback) {
        isStarted = playback;
        player.setPlayback(playback);
        if (!playback) {
            enoughFramesRendered = false;
            changeSurfaceVisibility(false); //Hide surface
        }
        updateKeepScreenOnMode();
    }

    /**
     * If you want player to play, call this method.<br>
     * Player will play always when it's possible.<br>
     * When any error occurs, player will try to play again.<br>
     * To stop player, call {@link #stop()}.
     */
    @Deprecated(since = "Use setPlayback(true) instead")
    public void start() {
        setPlayback(true);
    }

    /**
     * If you want player to not play, call this method.<br>
     * Player will stop playback as soon as possible.<br>
     * If you want to play again, call {@link #start()}.
     */
    @Deprecated(since = "Use setPlayback(false) instead")
    public void stop() {
        setPlayback(false);
    }

    public void release() {
        player.release();
    }

    public int getVideoDisplayWidth() {
        return surfaceView.getWidth();
    }

    public int getVideoDisplayHeight() {
        return surfaceView.getHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isStarted) {
            player.setPlayback(true);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        player.setPlayback(false);
    }

    private synchronized void updatePlayerSize() {

        VideoTrack videoTrack = currentVideoTrack;
        if (videoTrack == null) {
            return;
        }

        float displayAspectRatio = videoTrack.getDisplayAspectRatio();
        aspectRatio = Float.isFinite(displayAspectRatio) && displayAspectRatio > 0
                ? displayAspectRatio
                : Float.NaN;

        post(this::requestLayout);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        float layoutAspectRatio = Float.isFinite(aspectRatio) && aspectRatio > 0
                ? aspectRatio
                : heightSize > 0 ? (float) widthSize / heightSize : 1f;

        int width;
        int height;

        if (widthSize / layoutAspectRatio < heightSize) {
            width = widthSize;
            height = Math.round(widthSize / layoutAspectRatio);
        } else {
            height = heightSize;
            width = Math.round(heightSize * layoutAspectRatio);
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child != null) {
                measureChild(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        int parentWidth = right - left;
        int parentHeight = bottom - top;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child != null) {
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int childLeft = (parentWidth - childWidth) / 2;
                int childTop = (parentHeight - childHeight) / 2;

                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            }
        }
    }



    private synchronized void updateVideoTrack(@NonNull VideoTrack newTrack) {
        if (newTrack == currentVideoTrack)
            return;

        if (currentVideoTrack != null) {
            currentVideoTrack.removeListener(TRACK_LISTENER_ID);
        }

        currentVideoTrack = newTrack;

        if (currentVideoTrack != null) {
            currentVideoTrack.setListener(TRACK_LISTENER_ID, trackListener);
            updatePlayerSize();
        }
    }

    private synchronized void updateAudioTrack(@NonNull AudioTrack newTrack) {
        if (newTrack == currentAudioTrack)
            return;

        if (currentAudioTrack != null) {
            currentAudioTrack.removeListener(TRACK_LISTENER_ID);
        }

        currentAudioTrack = newTrack;

        if (currentAudioTrack != null) {
            currentAudioTrack.setListener(TRACK_LISTENER_ID, trackListener);
        }
    }

    private void changeSurfaceVisibility(boolean visible) {
        post(() -> {
            shutterView.animate()
                    .setInterpolator(new AccelerateInterpolator())
                    .alpha(visible ? 0 : 1)
                    .setDuration(300)
                    .start();
        });
    }

    private void updateKeepScreenOnMode() {
        switch (keepScreenOnMode) {
            case ALWAYS:
                setKeepScreenOn(true);
                break;
            case NEVER:
                setKeepScreenOn(false);
                break;
            default:
                setKeepScreenOn(isStarted);
                break;
        }
    }

    private static class MyTrackSelector implements TrackSelector {

        public final boolean video, audio, subtitles;

        public MyTrackSelector(boolean video, boolean audio, boolean subtitles) {
            this.video = video;
            this.audio = audio;
            this.subtitles = subtitles;
        }

        @Override
        public boolean selectTrack(@NonNull Track track) {
            if (track instanceof VideoTrack)
                return video;

            if (track instanceof AudioTrack)
                return audio;

            if (track instanceof SubtitleTrack)
                return subtitles;

            return false;
        }
    }

}
