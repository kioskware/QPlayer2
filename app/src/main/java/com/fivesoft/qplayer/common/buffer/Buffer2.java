package com.fivesoft.qplayer.common.buffer;

import com.fivesoft.qplayer.common.CircularArrayList;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Buffer2<T extends Bufferable> {

    public static final int DEFAULT_CAPACITY = Short.MAX_VALUE;

    private final BufferQueue queue;
    private volatile long latency;
    private volatile float playbackSpeed = 1f;
    private volatile long actualLatency = 0;

    private volatile long curTs, prevTs;

    /**
     * Creates new buffer object with default capacity equal to {@link #DEFAULT_CAPACITY}.
     * @see #Buffer2(int)
     */

    public Buffer2() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates new buffer object with specified capacity.<br>
     * @param capacity buffer capacity.
     */
    public Buffer2(int capacity){
        queue = new BufferQueue();
    }

    /**
     * Puts frame to buffer. Frame will be played after {@link #getLatency()} milliseconds.<br>
     * @param frame frame to put. Ignored when null.
     * @return true if frame was put to buffer, false otherwise (when frame is null).
     */

    public boolean push(T frame){
        if(frame == null)
            return false;
        synchronized (queue) {
            queue.offer(frame);
        }
        return true;
    }

    /**
     * Waits for next frame to play and returns it.<br>
     * @return next frame to play.
     */

    public synchronized T pull() throws InterruptedException {
        T cFrame = queue.take();
        actualLatency = queue.getDuration();

        //Adjust playback speed to match latency
        if (actualLatency > latency) {
            playbackSpeed = (float) actualLatency / (float) latency;
        } else {
            playbackSpeed = 1f;
        }

        //Get current frame timestamp
        curTs = cFrame.getTimestamp();

        //Wait for timestamps difference
        Thread.sleep((long) Math.max(0, Math.min((curTs - prevTs) / playbackSpeed, latency)));

        prevTs = curTs;
        return cFrame;
    }

    /**
     * Sets buffer latency. Latency is the time between frame receiving and frame playing.<br>
     * In other words it's a time elapsed between {@link #push(Bufferable)} and {@link #pull()} calls.
     * @param latencyMs latency in milliseconds.
     */
    public void setLatency(long latencyMs){
        this.latency = latencyMs;
    }

    /**
     * Gets buffer latency set by {@link #setLatency(long)}.
     * @return latency in milliseconds.
     */

    public long getLatency(){
        return latency;
    }

    /**
     * Gets exact time elapsed between last frame receiving and frame playing.<br>
     * @return actual latency in milliseconds.
     */

    public long getActualLatency(){
        return actualLatency;
    }

    /**
     * When actual latency is different than latency set by {@link #setLatency(long)},<br>
     * buffer adjusts playback speed to match actual latency to latency set.<br>
     * This method returns current playback speed. 1f means normal speed, 2f means 2x speed, 0.5f means 0.5x speed, etc.
     * @return current playback speed.
     */

    public float getPlaybackSpeed(){
        return playbackSpeed;
    }

    /**
     * Gets size of buffer. (Count of frames in buffer)<br>
     * Currently playing frame is not included.
     * @return buffer size.
     */
    public int size(){
        synchronized (queue) {
            return queue.size();
        }
    }

    /**
     * Removes all frames from buffer.<br>
     */

    public void clear(){
        synchronized (queue) {
            prevTs = 0;
            curTs = 0;
            queue.clear();
        }
    }

    /** @noinspection unchecked*/
    private class BufferQueue {

        private final List<T> queue = new CircularArrayList<>(10);
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();

        public T take() throws InterruptedException {
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            T result;
            try {
                while ((result = dequeue()) == null)
                    notEmpty.await();
            } finally {
                lock.unlock();
            }
            return result;
        }

        public void offer(T frame) {
            synchronized (queue) {
                Objects.requireNonNull(frame, "Frame cannot be null");

                /*
                 * Keep array sorted by timestamp ascending.
                 * If timestamp are equal, keep add order.
                 */

                for (int i = 0; i < queue.size(); i++) {
                    if (frame.getTimestamp() < queue.get(i).getTimestamp()) {
                        queue.add(i, frame);
                        return;
                    }
                }

            }
        }

        public void clear() {
            synchronized (queue) {
                queue.clear();
            }
        }

        public int size() {
            return queue.size();
        }

        public long getDuration() {
            synchronized (queue) {
                int size = queue.size();
                if (size < 2)
                    return 0;

                return queue.get(size - 1).getTimestamp() -
                        queue.get(0).getTimestamp();
            }
        }

        @Nullable
        private T dequeue() {
            synchronized (queue) {
                return queue.isEmpty() ? null : queue.remove(0);
            }
        }

    }

}
