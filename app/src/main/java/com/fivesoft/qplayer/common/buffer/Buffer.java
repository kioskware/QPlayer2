package com.fivesoft.qplayer.common.buffer;

import android.util.Log;

import java.util.Comparator;

public abstract class Buffer<T extends Bufferable> {

    @SuppressWarnings("ComparatorCombinators")
    private transient final Comparator<T> DEFAULT_COMPARATOR = (o1, o2) -> Long.compare(o1.getTimestamp(), o2.getTimestamp());
    private transient final Object ptLock = new Object();
    private transient volatile long lastTimestamp = -99999999;
    private transient volatile PlaybackThread playbackThread;

    private final SortedSynchronizedList<T> buffer = new SortedSynchronizedList<>(DEFAULT_COMPARATOR);
    private volatile long latency;
    private volatile boolean destroyed;

    private volatile float playbackSpeed = 1f;

    private volatile long actualLatency = 0;

    public void receive(T obj){

        if(destroyed)
            throw new IllegalStateException("Cannot receive on destroyed Buffer.");

        if(latency == 0){
            onFrame(obj);
            return;
        }

        buffer.add(obj);
        synchronized (ptLock) {
            if(playbackThread == null || playbackThread.isInterrupted() || !playbackThread.isAlive()){
                playbackThread = new PlaybackThread();
                playbackThread.start();
            }
        }

    }

    //Error after changing bitrate and fps on encoder
    //java.lang.NullPointerException: Attempt to invoke virtual method 'void com.fivesoft.qplayer.common.buffer.Buffer$PlaybackThread.interrupt()' on a null object reference
    //                                                                                                    	at com.fivesoft.qplayer.common.buffer.Buffer.interrupt(Buffer.java:45)
    //                                                                                                    	at com.fivesoft.qplayer.common.buffer.Buffer.destroy(Buffer.java:55)
    //                                                                                                    	at com.fivesoft.qplayer.core.player.QPlayerImpl$MainThread.run(QPlayerImpl.java:278)

    public void interrupt(){
        synchronized (ptLock){
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
            sleep();
        }
    }

    /**
     * Destroys this buffer object. After that, buffer will be unusable.
     */

    public void destroy(){
        interrupt();
        synchronized (ptLock) {
            destroyed = true;
        }
    }

    protected abstract void onFrame(T frame);

    protected void onSleep(){
        //This can be overridden to be notified about buffer sleep
    }

    public void setLatency(long latencyMs){
        this.latency = latencyMs;
    }

    public long getLatency(){
        return latency;
    }

    public long getActualLatency(){
        return actualLatency;
    }

    public float getPlaybackSpeed(){
        return playbackSpeed;
    }

    public boolean isDestroyed(){
        return destroyed;
    }

    /**
     * Checks if buffer is currently playing.
     * @return true if this buffer is playing now.
     */

    public boolean isPlaying(){
        synchronized (ptLock){
            return playbackThread != null && playbackThread.isAlive();
        }
    }

    /**
     * Gets current count of frames in a buffer.
     * @return frames in a buffer.
     */

    public int size(){
        return buffer.size();
    }

    private class PlaybackThread extends Thread {

        public PlaybackThread() {
            super();
        }

        @Override
        public void run() {

            synchronized (buffer) {

                try {
                    long prevTs = lastTimestamp, curTs;

                    //Play until there is sth to play and thread is not interrupted
                    while (!isInterrupted() && buffer.size() != 0) {
                        //Get current frame
                        T cFrame = buffer.getFirst();
                        T lFrame = buffer.getLast();

                        //Calculate actual latency
                        if (lFrame != null) {
                            actualLatency = Math.abs(lFrame.getTimestamp() - cFrame.getTimestamp());
                        } else {
                            actualLatency = 0;
                        }

                        //Adjust playback speed to match latency
                        if (actualLatency > latency) {
                            playbackSpeed = (float) actualLatency / (float) latency;
                        } else {
                            playbackSpeed = 1f;
                        }

                        //Get current frame timestamp
                        curTs = cFrame.getTimestamp();

                        //Wait for timestamps difference
                        try {
                            //noinspection BusyWait
                            Thread.sleep((long) Math.max(0, Math.min((curTs - prevTs) / playbackSpeed, latency)));
                        } catch (InterruptedException e) {
                            break;
                        }

                        prevTs = curTs;

                        if (buffer.modified()) {
                            Log.println(Log.ASSERT, "m", "modified");
                            continue;
                        }

                        //Remove current frame
                        buffer.remove(0);

                        //Play current frame
                        onFrame(cFrame);

                        //Check if there is something to play
                        for (int i = 0; i < latency && buffer.size() == 0; i++) {
                            //If no, wait for latency time
                            try {
                                //noinspection BusyWait
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }

                } finally {
                    Buffer.this.sleep();
                }
            }
        }
    }

    private void sleep(){
        lastTimestamp = -99999999;
        onSleep();
    }

}
