package com.fivesoft.qplayer.impl.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fivesoft.qplayer.base.components.DataSource;
import com.fivesoft.qplayer.common.resolver.Creator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Objects;

public class FileDataSource extends DataSource {

    public static Creator<URI, DataSource> CREATOR = new Creator<URI, DataSource>() {
        @Override
        public int accept(URI t) {

            if(t == null)
                return 0;

            if(!"file".equals(t.getScheme()))
                return 0;

            String path = t.getPath();

            return path == null ? 0 : 1;
        }

        @Nullable
        @Override
        public DataSource create(URI t) {

            if(accept(t) == 0)
                return null;

            String path = Objects.requireNonNull(t).getPath();

            if(path == null)
                return null;

            return new FileDataSource(path);
        }
    };

    private final File file;
    private volatile FileInputStream in;
    private final Object il = new Object();

    /**
     * Creates a new FileDataSource instance with the specified path
     * @param path the path to the file
     */

    public FileDataSource(@NonNull String path) {
        this.file = new File(Objects.requireNonNull(path, "Path is null"));
    }

    /**
     * Creates a new FileDataSource instance with the specified file
     * @param file the file
     */

    public FileDataSource(@NonNull File file) {
        this.file = Objects.requireNonNull(file, "File is null");
    }

    /**
     * Returns the length of the file
     * @return the length of the file
     */

    @Override
    public long getLength() {
        return file.length();
    }

    /**
     * Opens the file and creates a new {@link FileInputStream} instance to read from the file.
     * @param timeout not used in FileDataSource
     * @throws IOException if an I/O error occurs (i.e. the file does not exist)
     */

    @Override
    public void connect(int timeout) throws IOException {
        synchronized (il) {
            if (isConnected()) {
                throw new IllegalStateException("Already connected");
            } else {
                try(FileInputStream in = new FileInputStream(file)){
                    this.in = in;
                } catch (IOException e) {
                    this.in = null;
                    throw new IOException("Failed to open file", e);
                }
            }
        }
    }

    /**
     * Returns true if the file is opened and a {@link FileInputStream} instance is available
     * @return true if the file is opened and a {@link FileInputStream} instance is available
     */

    @Override
    public boolean isConnected() {
        return in != null;
    }

    /**
     * Returns the {@link FileInputStream} instance if the file is opened. (connected)
     * @return the {@link FileInputStream} instance
     * @throws IOException if the file is not opened yet.
     * @see #isConnected()
     * @see #connect(int)
     */

    @NonNull
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream in = this.in;
        if(in == null){
            throw new IOException("Not connected");
        } else {
            return in;
        }
    }

    /**
     * Returns false because FileDataSource does not support output stream
     * @return false
     */

    @Override
    public boolean isOutSupported() {
        //FileDataSource does not support output stream
        return false;
    }

    /**
     * Returns null because FileDataSource does not support output stream
     * @return null
     */

    @Nullable
    @Override
    public OutputStream getOutputStream(){
        //FileDataSource does not support output stream
        return null;
    }

    /**
     * Closes the {@link FileInputStream} instance. (if opened)<br>
     * The data source will be disconnected after this method is called.
     * @throws IOException if an I/O error occurs
     */

    @Override
    public void close() throws IOException {
        synchronized (il) {
            InputStream in = this.in;
            if(in != null){
                this.in = null;
                in.close();
            }
        }
    }

    /**
     * Returns the file set in the constructor
     * @return the file set in the constructor
     */

    @NonNull
    public File getFile(){
        return file;
    }

    @Override
    protected void onTimeoutSet(int timeout) throws IOException {
        //Nothing to do
    }
}
