package com.fivesoft.qplayer.base.track;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.Sample;
import com.fivesoft.qplayer.common.ListenerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A collection of tracks where tracks are identified by their id.<br>
 * This class is thread-safe.
 */

public class Tracks extends ListenerManager<TrackListener> implements Iterable<Track> {

    private final Map<String, Track> tracks = new HashMap<>();

    //Stores references to latest audio, video and subtitle tracks for faster access
    private volatile AudioTrack audioTrack;
    private volatile VideoTrack videoTrack;
    private volatile SubtitleTrack subtitleTrack;

    private static final String LISTENER_ID = "internal_Tracks.class";

    //Listener for all tracks
    private final TrackListener trackListener = new TrackListener() {

        @Override
        public void onTrackFieldChanged(@NonNull Track track, @NonNull String field, @Nullable Object value) {
            forEachListener((k, listener) -> listener.onTrackFieldChanged(track, field, value));
        }

        @Override
        public void onSampleReceived(@NonNull Track track, @NonNull Sample sample) {
            forEachListener((k, listener) -> listener.onSampleReceived(track, sample));
        }

        @Override
        public void onFrameRendered(@NonNull Track track, long timestamp) {
            forEachListener((k, listener) -> listener.onFrameRendered(track, timestamp));
        }

        @Override
        public void onTrackAdded(@NonNull Track track) {
            forEachListener((k, listener) -> listener.onTrackAdded(track));
        }

        @Override
        public void onTrackRemoved(@NonNull Track track) {
            forEachListener((k, listener) -> listener.onTrackRemoved(track));
        }
    };

    /**
     * Creates a new empty collection of tracks.
     */

    public Tracks() {}

    /**
     * Creates a new collection of tracks from the specified array.<br>
     * If the specified array contains multiple tracks with the same id, the last track with the same id will be added to this collection.<br>
     * If the specified array contains null, a NullPointerException will be thrown.
     * @param tracks tracks to be added. Must not be null.
     * @throws NullPointerException if tracks is null.
     */

    public Tracks(@NonNull Track... tracks){
        Objects.requireNonNull(tracks);
        for(Track track : tracks){
            put(track);
        }
    }

    /**
     * Creates a new collection of tracks from the specified iterable.<br>
     * If the specified iterable contains multiple tracks with the same id, the last track with the same id will be added to this collection.<br>
     * If the specified iterable contains null, a NullPointerException will be thrown.
     * @param tracks tracks to be added. Must not be null.
     * @throws NullPointerException if tracks is null.
     */

    public Tracks(@NonNull Iterable<Track> tracks){
        Objects.requireNonNull(tracks);
        for(Track track : tracks){
            put(track);
        }
    }

    /**
     * Adds the specified track to this collection.<br>
     * If this collection already contains a track with the same id, the old track is replaced by the specified track.
     * @param track track to be added. Must not be null.
     */

    public void put(@NonNull Track track){
        synchronized (tracks) {
            Track prev = tracks.put(track.getId(), Objects.requireNonNull(track));
            track.setListener(LISTENER_ID, trackListener);
            if(track instanceof AudioTrack){
                audioTrack = (AudioTrack) track;
            } else if(track instanceof VideoTrack){
                videoTrack = (VideoTrack) track;
            } else if(track instanceof SubtitleTrack){
                subtitleTrack = (SubtitleTrack) track;
            }

            if(prev == track)
                return;

            if(prev != null){
                //Notify listeners
                trackListener.onTrackRemoved(prev);
                prev.removeListener(LISTENER_ID);
            }
            //Notify listeners
            trackListener.onTrackAdded(track);
        }
    }

    /**
     * Returns the track with the specified id, or null if there was no track with the specified id.
     * @param id track id. Must not be null.
     * @return the track with the specified id, or null if there was no track with the specified id.
     * @throws NullPointerException if id is null.
     */

    @Nullable
    public Track get(@NonNull String id){
        synchronized (tracks) {
            return tracks.get(Objects.requireNonNull(id));
        }
    }

    /**
     * Returns latest track with specified payload type, or null if there was no track with the specified payload type.
     * @param payloadType track payload type.
     */

    @Nullable
    public Track getByPayloadType(int payloadType){
        synchronized (tracks) {
            for(Track track : tracks.values()){
                if(track.getPayloadType() == payloadType){
                    return track;
                }
            }
            return null;
        }
    }

    /**
     * Removes the track with the specified id from this collection.
     * @param id track id. Must not be null.
     * @return the removed track, or null if there was no track with the specified id.
     * @throws NullPointerException if id is null.
     */

    public Track remove(@NonNull String id){
        synchronized (tracks) {
            Track prev = tracks.remove(Objects.requireNonNull(id));
            if(prev == null){
                return null;
            } else if(prev == audioTrack){
                audioTrack = null;
            } else if(prev == videoTrack){
                videoTrack = null;
            } else if(prev == subtitleTrack){
                subtitleTrack = null;
            }
            //Notify listeners
            trackListener.onTrackRemoved(prev);
            prev.removeListener(LISTENER_ID);
            return prev;
        }
    }

    /**
     * Removes all tracks that satisfy the specified predicate.
     * @param predicate predicate. Must not be null.
     * @throws NullPointerException if predicate is null.
     */
    public void removeIf(@NonNull Predicate<Track> predicate){
        Objects.requireNonNull(predicate);
        synchronized (tracks) {
            for(Track track : new ArrayList<>(tracks.values())){
                if(predicate.test(track)){
                    remove(track.getId());
                }
            }
        }
    }

    /**
     * Returns true if this collection contains a track with the specified id.
     * @param id track id. Must not be null.
     * @return true if this collection contains a track with the specified id.
     * @throws NullPointerException if id is null.
     */

    public boolean contains(@NonNull String id){
        synchronized (tracks) {
            return tracks.containsKey(Objects.requireNonNull(id));
        }
    }

    /**
     * Returns the number of tracks.
     * @return the number of tracks.
     */

    public int size(){
        synchronized (tracks) {
            return tracks.size();
        }
    }

    /**
     * Removes all tracks.
     */

    public void clear(){
        synchronized (tracks) {
            //Notify listeners
            for(Track track : tracks.values()){
                trackListener.onTrackRemoved(track);
                track.removeListener(LISTENER_ID);
            }
            tracks.clear();
            audioTrack = null;
            videoTrack = null;
            subtitleTrack = null;
        }
    }

    /**
     * Sets this track object to contain the same tracks as the specified tracks object.<br>
     * All previous tracks, which are not contained in the specified tracks object, will be removed.<br>
     * @param tracks tracks to be set. If null, all tracks will be removed.
     * @return this object.
     */
    public Tracks set(@Nullable Tracks tracks){
        synchronized (this.tracks) {
            if(tracks == null){
                clear();
                return this;
            }

            List<Track> added = new ArrayList<>();
            for(Track track : tracks.tracks.values()){
                put(track);
                added.add(track);
            }
            //Remove all tracks that are not contained in the specified tracks object
            for(Track track : new ArrayList<>(this.tracks.values())){
                if(!added.contains(track)){
                    remove(track.getId());
                }
            }
        }
        return this;
    }

    /**
     * Returns the audio track, or null if there was no audio track.<br>
     * This method is faster than calling {@link #get(String)} with the id of the audio track.<br>
     * If this collection contains multiple audio tracks, the last-modified (or added) audio track will be returned.
     */

    @Nullable
    public AudioTrack getAudioTrack(){
        return audioTrack;
    }

    /**
     * Returns the video track, or null if there was no video track.<br>
     * This method is faster than calling {@link #get(String)} with the id of the video track.<br>
     * If this collection contains multiple video tracks, the last-modified (or added) video track will be returned.
     */

    @Nullable
    public VideoTrack getVideoTrack(){
        return videoTrack;
    }

    /**
     * Returns the subtitle track, or null if there was no subtitle track.<br>
     * This method is faster than calling {@link #get(String)} with the id of the subtitle track.<br>
     * If this collection contains multiple subtitle tracks, the last-modified (or added) subtitle track will be returned.
     */

    @Nullable
    public SubtitleTrack getSubtitleTrack(){
        return subtitleTrack;
    }

    /**
     * Gets all tracks in this collection. (audio tracks, video tracks, subtitle tracks and other tracks)<br>
     * The returned array is a copy of the tracks in this collection, so modifying the returned array will not affect this collection.
     * @return all tracks in this collection.
     */

    @NonNull
    public Track[] toArray(){
        synchronized (tracks) {
            return tracks.values().toArray(new Track[0]);
        }
    }

    /**
     * Gets all audio tracks in this collection.
     * The returned array is a copy of the audio tracks in this collection, so modifying the returned array will not affect this collection.
     * @return all audio tracks in this collection.
     */

    @NonNull
    public AudioTrack[] getAudioTracks(){
        synchronized (tracks) {
            return tracks.values()
                    .stream()
                    .filter(track -> track instanceof AudioTrack)
                    .map(track -> (AudioTrack) track)
                    .toArray(AudioTrack[]::new);
        }
    }

    /**
     * Gets all video tracks in this collection.
     * The returned array is a copy of the video tracks in this collection, so modifying the returned array will not affect this collection.
     * @return all video tracks in this collection.
     */

    @NonNull
    public VideoTrack[] getVideoTracks(){
        synchronized (tracks) {
            return tracks.values()
                    .stream()
                    .filter(track -> track instanceof VideoTrack)
                    .map(track -> (VideoTrack) track)
                    .toArray(VideoTrack[]::new);
        }
    }

    /**
     * Gets all subtitle tracks in this collection.
     * The returned array is a copy of the subtitle tracks in this collection, so modifying the returned array will not affect this collection.
     * @return all subtitle tracks in this collection.
     */

    @NonNull
    public SubtitleTrack[] getSubtitleTracks(){
        synchronized (tracks) {
            return tracks.values()
                    .stream()
                    .filter(track -> track instanceof SubtitleTrack)
                    .map(track -> (SubtitleTrack) track)
                    .toArray(SubtitleTrack[]::new);
        }
    }

    /**
     * Gets all tracks in this collection that are not audio tracks or video tracks.
     * The returned array is a copy of the tracks in this collection, so modifying the returned array will not affect this collection.
     * @return all tracks in this collection that are not audio tracks or video tracks.
     */

    @NonNull
    public Track[] getOtherTracks(){
        synchronized (tracks) {
            return tracks.values()
                    .stream()
                    .filter(track -> !(track instanceof AudioTrack) && !(track instanceof VideoTrack))
                    .toArray(Track[]::new);
        }
    }

    /**
     * Returns an iterator over the tracks in this collection.
     * @return an iterator over the tracks in this collection.
     */

    @NonNull
    @Override
    public Iterator<Track> iterator() {
        return tracks.values().iterator();
    }
}
