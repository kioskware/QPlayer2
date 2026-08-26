package com.fivesoft.qplayer.impl.decoder.video.h264;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.common.measure.FPSMeasure;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;

public class YUVSurfaceRenderer {

    public static final boolean USE_HARDWARE_BITMAP = false;
    public static final int FPS_CALC_BUFFER_SIZE = 100;

    private Allocation in, out;
    private ScriptIntrinsicYuvToRGB yuvConverter;

    private final Semaphore renderLock = new Semaphore(0);
    private final Semaphore prepareLock = new Semaphore(0);
    private volatile Surface surface;

    //Caching flow

    private final Bitmap[] cache = new Bitmap[2];
    private volatile byte readyFrameIndex = -1;

    //1. Write raw YUV data to cache

    private volatile byte[] lf_yuvData;
    private volatile int lf_width;
    private volatile int lf_height;

    //2. Decode frame into bitmap and store it in cache

    private volatile boolean isPreparing = false;

    //3. Pass ready prepared frame to renderer

    private volatile boolean isRendering = false;

    private volatile boolean released = false;
    private final RectF frameRect = new RectF();

    private final Context context;

    private final FPSMeasure fpsMeasure = new FPSMeasure(FPS_CALC_BUFFER_SIZE);

    public YUVSurfaceRenderer(@NonNull Context context) {
        this.context = Objects.requireNonNull(context);

        /*
         * Thread that prepares the frame for rendering.
         * Converts YUV 4:2:0 byte array to Bitmap.
         */
        Thread framePrepareThread = new Thread(rendererGroup, () -> {
            int index;
            while (!Thread.interrupted()) {
                //Wait for the frame to be prepared
                if (acquire(prepareLock)) {
                    //Convert YUV 4:2:0 byte array to Bitmap
                    isPreparing = true;
                    index = readyFrameIndex == 0 ? 1 : 0;
                    prepareFrame(lf_yuvData, index, lf_width, lf_height);
                    //Swap caches
                    readyFrameIndex = (byte) index;
                    isPreparing = false;
                    //Release the frame for rendering
                    if (!isRendering) {
                        renderLock.release();
                    }
                }
            }
        }, "FramePrepareThread");
        framePrepareThread.start();

        /*
         * Thread that renders the frame to the surface.
         */
        Thread frameRenderThread = new Thread(rendererGroup, () -> {
            int index;
            while (!Thread.interrupted()) {
                //Wait for the frame to be rendered
                if (acquire(renderLock)) {
                    //Render the frame
                    isRendering = true;
                    index = readyFrameIndex;
                    if (index >= 0 && index < cache.length) {
                        renderFrame(cache[index], frameRect, surface);
                        fpsMeasure.onFrame();
                    }
                    isRendering = false;
                }
            }
        }, "FrameRenderThread");
        frameRenderThread.start();
    }

    /**
     * Set the surface to render on. May be null to stop rendering.
     * @param surface Surface to render on or null to stop rendering
     */

    public void setSurface(@Nullable Surface surface){
        if (surface != null) {
            //Check if renderer is released only if sb wants to set a surface
            //To avoid unnecessary exceptions
            checkReleased();
        }
        this.surface = surface;
    }

    /**
     * Releases all resources used by this renderer.
     * After this method is called the renderer is no longer usable
     * and every call to this renderer will throw an {@link IllegalStateException}.
     */

    public void release(){

        //Set released flag to true
        released = true;

        //Interrupt threads
        rendererGroup.interrupt();
        try {
            rendererGroup.destroy();
        } catch (Exception e) {
            //Ignore illegal thread state exception
        }

        Surface surface = this.surface;
        try {
            surface.release();
        } catch (Exception e) {
            //In case, exceptions here are not important
        }

        //Release all resources
        for (Bitmap b: cache){
            recycleBitmapQuietly(b);
        }

        //Let GC do its job
        this.surface = null;
        Arrays.fill(cache, null);
        this.lf_yuvData = null;
    }

    /**
     * Renders a frame to the surface.
     * @param yuvData YUV data to render
     * @param width Width of the frame
     * @param height Height of the frame
     */

    public void updateFrame(byte[] yuvData, int width, int height){
        checkReleased();
        if (yuvData == null || width <= 0 || height <= 0){
            return;
        }
        synchronized (prepareLock){
            if (isPreparing){
                return;
            }
            this.lf_yuvData = yuvData;
            this.lf_width = width;
            this.lf_height = height;
            prepareLock.release();
        }
    }

    /**
     * Redraws the last frame to the surface.<br>
     * This method is useful if the surface has been invalidated and needs to be redrawn.
     */

    public void redraw(){
        checkReleased();
        synchronized (renderLock){
            if (isRendering){
                return;
            }
            renderLock.release();
        }
    }

    /**
     * Gets latest frame width. If no frame is now set, returns -1.
     * @return Frame width or -1 if no frame is set
     */

    public int getFrameWidth(){
        Bitmap frame = getPreparedFrame();
        return frame == null ? -1 : frame.getWidth();
    }

    /**
     * Gets latest frame height. If no frame is now set, returns -1.
     * @return Frame height or -1 if no frame is set
     */

    public int getFrameHeight(){
        Bitmap frame = getPreparedFrame();
        return frame == null ? -1 : frame.getHeight();
    }

    /**
     * Returns copy of the current frame. (the latest set one) If no frame is now set, returns null.
     * @return Copy of the current frame or null if no frame is set
     */

    public Bitmap getCurrentFrame(){
        //Return a copy of the current frame
        Bitmap frame = getPreparedFrame();
        if (frame == null){
            return null;
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (frame) {
            return frame.copy(frame.getConfig(), true);
        }
    }

    /**
     * Returns average FPS of the renderer. If no frames have been rendered yet, returns -1.<br>
     * The FPS is calculated using the last {@link #FPS_CALC_BUFFER_SIZE} frames.
     * @return Average FPS of the renderer or -1 if no frames have been rendered yet.
     */

    public double getFPS(){
        if(isReleased())
            return -1;
        return fpsMeasure.getAverageFPS();
    }

    /**
     * Checks if the renderer is released.
     */

    public boolean isReleased(){
        return released;
    }

    private void checkReleased(){
        if (released){
            throw new IllegalStateException("Call to released Renderer.");
        }
    }

    /**
     * Utility method to render a frame to a surface. If one or more of the parameters are invalid
     * the method will return without doing anything. Method provides synchronization on frame Bitmap object.
     * @param frame Bitmap to render
     * @param bitmapRect The rectangle that the bitmap will be scaled/translated to fit into
     * @param surface Surface to draw the bitmap on
     */
    private static void renderFrame(Bitmap frame, RectF bitmapRect, Surface surface){

        if (frame != null && !frame.isRecycled() && //Check if the bitmap is valid
                surface != null && surface.isValid() &&  //Check if the surface is valid
                bitmapRect != null){ //Check if the bitmap rect is valid

            Canvas canvas;
            try {
                canvas = surface.lockHardwareCanvas();
            } catch (Exception e) {
                //In case if the surface is destroyed while we are trying to lock it
                return;
            }

            //Draw bitmap to fill the view (centered) and match aspect ratio of the bitmap
            try {

                int viewWidth = canvas.getWidth();
                int viewHeight = canvas.getHeight();

                int bitmapWidth = frame.getWidth();
                int bitmapHeight = frame.getHeight();

                float scale = Math.min((float) viewWidth / bitmapWidth, (float) viewHeight / bitmapHeight);

                int scaledWidth = (int) (bitmapWidth * scale);
                int scaledHeight = (int) (bitmapHeight * scale);

                int left = (viewWidth - scaledWidth) / 2;
                int top = (viewHeight - scaledHeight) / 2;

                bitmapRect.set(left, top, left + scaledWidth, top + scaledHeight);

                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (frame) {
                    try {
                        canvas.drawBitmap(frame, null, bitmapRect, null);
                    } catch (Throwable e) {
                        //Just in case if the bitmap recycles while we are drawing it
                        //or if the surface is destroyed while we are drawing on it
                    }
                }

            } finally {
                unlockCanvasAndPostQuietly(surface, canvas);
            }
        } else if(surface != null && surface.isValid()){
            //Clear the surface
            Canvas canvas = null;
            try {
                canvas = surface.lockHardwareCanvas();
            } catch (Throwable e){
                //In case if the surface is destroyed while we are trying to lock it
            } finally {
                unlockCanvasAndPostQuietly(surface, canvas);
            }
        }

    }

    private static void unlockCanvasAndPostQuietly(Surface surface, Canvas canvas){
        try {
            if (canvas != null && surface != null && surface.isValid()){
                surface.unlockCanvasAndPost(canvas);
            }
        } catch (Throwable e) {
            //Ignore
        }
    }

    private final ThreadGroup rendererGroup = new ThreadGroup("YUVSurfaceRenderer" + hashCode());

    /**
     * Convenience method to acquire a semaphore without throwing an exception.
     * @param semaphore Semaphore to acquire
     * @return True if the semaphore was acquired, false if the thread was interrupted
     */

    private static boolean acquire(Semaphore semaphore){
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    private int validateYUV(byte[] yuv, int width, int height){
        if(yuv == null){
            return 101; //Null data
        }
        if (yuv.length != getYUVByteSize(width, height)){
            return 1; //Invalid data
        }
        return 0;
    }

    public static int getYUVByteSize(int width, int height){
        return width * height * 2;
    }

    @Nullable
    private Bitmap getPreparedFrame(){
        checkReleased();
        if (readyFrameIndex >= 0 && readyFrameIndex < cache.length) {
            return cache[readyFrameIndex];
        }
        return null;
    }

    private void initRenderScript(Context context, int width, int height) {

        //Destroy old
        destroyRenderScript();

        //Create RenderScript
        RenderScript rs = RenderScript.create(context);
        yuvConverter = ScriptIntrinsicYuvToRGB.create(
                rs, Element.U8_4(rs));

        //Create allocations
        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs))
                .setX(getYUVByteSize(width, height));

        in = Allocation.createTyped(
                rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(width)
                .setY(height);

        out = Allocation.createTyped(
                rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
    }

    private int prepareFrame(byte[] yuv, int index, int width, int height){
        int dV = validateYUV(yuv, width, height);
        if (dV != 0){
            return dV;
        }
        int dB = ensureBitmap(cache, 0, width, height, USE_HARDWARE_BITMAP);
        if (dB != 0){
            return dB;
        }
        int dB2 = ensureBitmap(cache, 1, width, height, USE_HARDWARE_BITMAP);
        if (dB2 != 0){
            return dB;
        }

        return decodeFrame(cache[index], yuv);
    }

    private int ensureBitmap(Bitmap[] frameArr, int pos, int width, int height, boolean hardware){
        Bitmap frame = frameArr[pos];
        //Get desired bitmap config
        Bitmap.Config config = hardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                Bitmap.Config.HARDWARE :
                Bitmap.Config.ARGB_8888;

        if (frame == null || frame.getWidth() != width || frame.getHeight() != height || //Check if bitmaps are the same size
                frame.getConfig() != config){ //Check if bitmaps are the same type
            try {
                frameArr[pos] = Bitmap.createBitmap(width, height, config);
                //Init RenderScript
                initRenderScript(context, width, height);
            } catch (Exception e) {
                //Bad size
                return 11;
            } catch (OutOfMemoryError e) {
                //Not enough memory
                return 101;
            }
        }
        return 0;
    }

    private int decodeFrame(Bitmap bitmap, byte[] yuvByteArray) {

        ScriptIntrinsicYuvToRGB script = this.yuvConverter;
        Allocation in = this.in;
        Allocation out = this.out;

        if (in == null || out == null || bitmap == null || script == null){
            return 102; //Not initialized
        }

        in.copyFrom(yuvByteArray);
        script.setInput(in);
        script.forEach(out);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (bitmap) {
            out.copyTo(bitmap);
        }

        return 0;
    }

    private void destroyRenderScript(){
        if (yuvConverter != null){
            try {
                yuvConverter.destroy();
            } catch (Throwable ignore) {}
            yuvConverter = null;
        }
        if (in != null){
            try {
                in.destroy();
            } catch (Throwable ignore) {}
            in = null;
        }
        if (out != null){
            try {
                out.destroy();
            } catch (Throwable ignore) {}
            out = null;
        }
    }

    private static void recycleBitmapQuietly(Bitmap bitmap){
        try {
            if (bitmap != null && !bitmap.isRecycled()){
                bitmap.recycle();
            }
        } catch (Exception e) {
            //In case, exceptions here are not important
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        release();
    }
}
