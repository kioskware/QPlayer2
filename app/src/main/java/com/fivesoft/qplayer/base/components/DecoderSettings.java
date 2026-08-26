package com.fivesoft.qplayer.base.components;

public final class DecoderSettings {

    private boolean onlySyncFrames;
    private int maxDroppedNonSyncFrames;

    public DecoderSettings(boolean onlySyncFrames, int maxDroppedNonSyncFrames) {
        this.onlySyncFrames = onlySyncFrames;
        this.maxDroppedNonSyncFrames = maxDroppedNonSyncFrames;
    }

    public boolean onlySyncFrames() {
        return onlySyncFrames;
    }

    public int getMaxDroppedNonSyncFrames() {
        return maxDroppedNonSyncFrames;
    }

    public void setMaxDroppedNonSyncFrames(int val) {
        this.maxDroppedNonSyncFrames = val;
    }

    public void setOnlySyncFrames(boolean val) {
        this.onlySyncFrames = val;
    }

}