package com.fivesoft.qplayer.common.measure;

import java.util.Locale;

public class BitrateMeasure extends FrequencyMeasure {

    public BitrateMeasure(int bufferSize, long interval) {
        super(bufferSize, interval);
    }

    public void countBit(){
        count(1);
    }

    public void countByte(){
        count(8);
    }

    public void countBits(long bits){
        count(bits);
    }

    public void countBytes(long bytes){
        countBits(bytes * 8);
    }

    public double getBitrate(){
        return getFrequency();
    }

    /**
     * Gets the average bitrate over the last interval in a human readable format.<br>
     * Example: 123.45 kbps
     * @return The average bitrate, or -1 if no bits have been recorded.
     */

    public String getReadableBitrate(){
        return toReadableBitrate(getBitrate());
    }

    public static String toReadableBitrate(double bitrate){
        if(bitrate == -1) {
            return "N/A";
        }
        else {
            if(bitrate < 1000){
                return String.format(Locale.US, "%.2f b/s", bitrate);
            } else if(bitrate < 1000000){
                return String.format(Locale.US, "%.2f Kb/s", bitrate / 1000);
            } else {
                return String.format(Locale.US, "%.2f Mb/s", bitrate / 1000000);
            }
        }
    }

}
