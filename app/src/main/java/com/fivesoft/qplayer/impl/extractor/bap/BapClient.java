package com.fivesoft.qplayer.impl.extractor.bap;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BapClient {

    /**
     * Packet type id. Starts video transmission session. As a response server will send an Info Packet.
     */
    public static final byte PACKET_TYPE_INIT = -128;

    /**
     * Packet type id. Describes video parameters: frame rate, width, height and duration.
     */
    public static final byte PACKET_TYPE_INFO = -127;

    /**
     * Packet type id. Contains actual H.264 encoded video data.
     */
    public static final byte PACKET_TYPE_DATA = -126;

    /**
     * Packet type id. Stops video transmission session. As a response server will send a Close Notification Packet.
     */
    public static final byte PACKET_TYPE_CLOSE = -125;

    /**
     * Packet type id. Notifies that video transmission has been closed.
     * It may happen after sending a Close Request Packet or because the video end has been reached.
     * Session can be opened again using Init Packet.
     */
    public static final byte PACKET_TYPE_CLOSE_NOTIFICATION = -124;

    public static final byte[] PACKET_ARR_CLOSE = new byte[]{PACKET_TYPE_CLOSE, -128, -128, -128, -128};
    public static final byte[] PACKET_ARR_CLOSE_NOTIFICATION = new byte[]{PACKET_TYPE_CLOSE_NOTIFICATION, -128, -128, -128, -128};

    private volatile int width, height;
    private volatile int fps;
    private volatile long duration;

    public void initSession(String username, String password) throws IOException {
        byte[] un = username == null ? new byte[0] : username.getBytes(StandardCharsets.UTF_8);
        byte[] pw = password == null ? new byte[0] : password.getBytes(StandardCharsets.UTF_8);

        OutputStream os = getOutputStream();

        if(un.length > 255 || pw.length > 255)
            throw new IllegalArgumentException("Illegal username or password length. Must be <= 255 bytes");

        os.write(un.length - 128);
        os.write(un);
        os.write(pw.length - 128);
        os.write(pw);

        DataInputStream is = new DataInputStream(getInputStream());
        byte type = (byte) is.read();
        int len = is.read() + Integer.MIN_VALUE;

        if (len != 13)
            throw new IOException("Unexpected packet length: " + len);

        if(type != PACKET_TYPE_INFO)
            throw new IOException("Unexpected packet type: " + type);

        width = is.readShort() + Short.MIN_VALUE;
        height = is.readShort() + Short.MIN_VALUE;
        fps = is.read() + Byte.MIN_VALUE;
        duration = is.readLong() + Long.MIN_VALUE;
    }

    public void closeSession() throws IOException {
        OutputStream os = getOutputStream();
        os.write(PACKET_ARR_CLOSE);
    }

    public abstract OutputStream getOutputStream();

    public abstract InputStream getInputStream();

}
