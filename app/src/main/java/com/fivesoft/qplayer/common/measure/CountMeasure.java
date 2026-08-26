package com.fivesoft.qplayer.common.measure;

import java.util.Arrays;

public class CountMeasure {

    private final long[] buffer;
    private final double probeDuration;
    private final long interval;
    private volatile long lastProbeTime = -1;
    private int currentProbeIndex = 0;

    public CountMeasure(int bufferSize, long interval) {
        this.buffer = new long[bufferSize];
        //Calculate time for one probe.
        this.probeDuration = (interval / (double) buffer.length);
        this.interval = interval;
        Arrays.fill(buffer, -1);
    }

    public void count(long units){
        //Initial probe
        if(lastProbeTime == -1){
            lastProbeTime = System.currentTimeMillis();
            buffer[0] = units;
            currentProbeIndex = 0;
            return;
        }

        //Time since last probe. Last probe has index = bufferIndex
        long dif = System.currentTimeMillis() - lastProbeTime;

        if(dif < probeDuration){
            //Add to current probe
            buffer[currentProbeIndex] = Math.max(buffer[currentProbeIndex], 0) + units;
        } else {
            //Position indicates how many probes we have to skip
            int pos = (int) Math.floor(dif / probeDuration);
            //Fill skipped probes with -1
            for (int i = 1; i < pos; i++) {
                currentProbeIndex = (currentProbeIndex + 1) % buffer.length;
                buffer[currentProbeIndex] = -1;
            }
            currentProbeIndex += pos;
            currentProbeIndex %= buffer.length;
            //Add to new probe
            buffer[currentProbeIndex] = units;
            lastProbeTime = System.currentTimeMillis();
        }
    }

    public void count(){
        count(1);
    }

    /**
     * Gets count of the last interval.
     */

    public long getCount(){
        count(0);
        boolean hasProbe = false;
        long sum = 0;
        for (long l : buffer) {
            if(l != -1) {
                sum += l;
                hasProbe = true;
            }
        }
        return hasProbe ? sum : -1;
    }

    public void reset(){
        lastProbeTime = -1;
        currentProbeIndex = 0;
        Arrays.fill(buffer, -1);
    }

    /**
     * Gets size of the buffer.
     */

    public int getBufferSize() {
        return buffer.length;
    }

    /**
     * Gets the interval of the measure. (in milliseconds)
     */
    public long getInterval(){
        return interval;
    }

}
