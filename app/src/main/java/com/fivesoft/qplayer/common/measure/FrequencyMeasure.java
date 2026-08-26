package com.fivesoft.qplayer.common.measure;

import java.util.Locale;

public class FrequencyMeasure extends CountMeasure {

    public FrequencyMeasure(int bufferSize, long interval) {
        super(bufferSize, interval);
    }

    /**
     * Gets the average frequency over the last interval. (in beats per second)
     * @return The average frequency, or -1 if no beats have been recorded.
     */
    public double getFrequency(){
        return getCount() / (double) getInterval() * 1000.0;
    }

    public String getReadableFrequency(){
        double frequency = getFrequency();
        if(frequency == -1) {
            return "N/A";
        }
        else {
            if(frequency < 1){
                return String.format(Locale.US, "%.2f mHz", frequency * 1000);
            } else if(frequency < 1000){
                return String.format(Locale.US, "%.2f Hz", frequency);
            } else if(frequency < 1000000){
                return String.format(Locale.US, "%.2f KHz", frequency / 1000);
            } else {
                return String.format(Locale.US, "%.2f MHz", frequency / 1000000);
            }
        }
    }

}


