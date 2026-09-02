package com.fivesoft.qplayer.base.track;

import androidx.annotation.NonNull;

public class VideoTrack extends Track {

    public static final String FIELD_WIDTH = "width";
    public static final String FIELD_HEIGHT = "height";
    public static final String FIELD_FPS = "fps";
    public static final String FIELD_PIXEL_ASPECT_RATIO = "pixelAspectRatio";

    private int width = UNKNOWN, height = UNKNOWN;
    private float fps = UNKNOWN;
    private float pixelAspectRatio = 1f;

    public VideoTrack(@NonNull String id, int payloadType, int width, int height, float fps) {
        super(id, payloadType);
        this.width = width;
        this.height = height;
        this.fps = fps;
    }

    public VideoTrack(@NonNull String id, int payloadType) {
        super(id, payloadType);
    }

    /**
     * Returns the width of the video track (in pixels) or {@link Track#UNKNOWN} if the width is unknown.<br>
     * @return The width of the video track or {@link Track#UNKNOWN} if the width is unknown.
     */

    public int getWidth() {
        return width;
    }

    /**
     * Sets the width of the video track (in pixels).<br> Negative values will be treated as unknown.<br>
     * @param width The width of the video track. (in pixels)
     */

    public void setWidth(int width) {
        int nVal = Math.max(UNKNOWN, width);
        if(nVal != this.width){
            this.width = nVal;
            notifyUpdated(FIELD_WIDTH, nVal);
        }
    }

    /**
     * Returns the height of the video track (in pixels) or {@link Track#UNKNOWN} if the height is unknown.<br>
     * @return The height of the video track or {@link Track#UNKNOWN} if the height is unknown.
     */

    public int getHeight() {
        return height;
    }

    /**
     * Sets the height of the video track (in pixels).<br> Negative values will be treated as unknown.<br>
     * @param height The height of the video track. (in pixels)
     */

    public void setHeight(int height) {
        int nVal = Math.max(UNKNOWN, height);
        if(nVal != this.height){
            this.height = nVal;
            notifyUpdated(FIELD_HEIGHT, nVal);
        }
    }

    /**
     * Returns the frame rate of the video track (in frames per second) or {@link Track#UNKNOWN} if the frame rate is unknown.<br>
     * @return The frame rate of the video track or {@link Track#UNKNOWN} if the frame rate is unknown.
     */

    public float getFps() {
        return Math.max(UNKNOWN, fps);
    }

    /**
     * Sets the frame rate of the video track (in frames per second).<br> Negative values will be treated as unknown.<br>
     * @param fps The frame rate of the video track. (in frames per second)
     */

    public void setFps(float fps) {
        float nVal = Math.max(UNKNOWN, fps);
        if(nVal != this.fps){
            this.fps = nVal;
            notifyUpdated(FIELD_FPS, nVal);
        }
    }

    /**
     * Gets the ratio between the physical width and height of a single pixel.
     * Square pixels use the default value of {@code 1}.
     */
    public float getPixelAspectRatio() {
        return pixelAspectRatio;
    }

    /**
     * Sets the ratio between the physical width and height of a single pixel.
     * Invalid values fall back to square pixels.
     */
    public void setPixelAspectRatio(float pixelAspectRatio) {
        float newValue = Float.isFinite(pixelAspectRatio) && pixelAspectRatio > 0
                ? pixelAspectRatio
                : 1f;
        if (Float.compare(newValue, this.pixelAspectRatio) != 0) {
            this.pixelAspectRatio = newValue;
            notifyUpdated(FIELD_PIXEL_ASPECT_RATIO, newValue);
        }
    }

    /**
     * Gets the display aspect ratio after applying the pixel aspect ratio.
     */
    public float getDisplayAspectRatio() {
        if (width <= 0 || height <= 0) {
            return UNKNOWN;
        }
        return (float) width / height * pixelAspectRatio;
    }

}
