package com.fivesoft.qplayer.common.measure;

import android.os.SystemClock;

import java.util.Arrays;

public class FPSMeasure {

    private final long[] buffer;
    private volatile long lastFrameTime = -1;
    private volatile int bufferIndex = 0;

    public FPSMeasure(int bufferSize) {
        if(bufferSize < 1)
            throw new IllegalArgumentException("Buffer size must be greater than 0");
        this.buffer = new long[bufferSize];
        reset();
    }

    /**
     * Resets the calculator buffer.<br>
     * After calling this method, the next call to {@link #onFrame()} or {@link #getAverageFPS()} will return -1.
     */

    public synchronized void reset(){
        lastFrameTime = -1;
        bufferIndex = 0;
        Arrays.fill(buffer, -1);
    }

    /**
     * Notifies calculator about frame. Call this method when a frame is rendered.
     * @return The time in nanoseconds since the last frame was rendered, or -1 if this is the first frame.
     */

    public synchronized long onFrame(){
        long now = SystemClock.elapsedRealtimeNanos();
        long delta = now - lastFrameTime;
        lastFrameTime = now;

        if(delta < 0){
            return -1;
        }

        if(bufferIndex >= buffer.length)
            bufferIndex = 0;

        buffer[bufferIndex] = delta;
        bufferIndex++;
        return delta;
    }

    /**
     * Returns the average FPS over the last buffer.
     * @return The average FPS, or -1 if no frames have been recorded.
     */

    public double getAverageFPS(){
        double sum = 0;
        double count = 0;
        for(long delta : buffer){
            if(delta < 0)
                continue;
            sum += delta;
            count++;
        }
        if(count == 0)
            return -1;

        return 1_000_000_000.0 / (sum / count);
    }

    /**
     * Gets size of the buffer.
     */

    public int getBufferSize() {
        return buffer.length;
    }

}
