package com.fivesoft.qplayer.common;

public class Constants {

    /**
     * Version of QPlayer library you are using.
     */

    public static final String Q_PLAYER_VERSION_NAME = "1.0.0";

    /**
     * Default QPlayer user agent.
     */

    public static final String Q_PLAYER_USER_AGENT = "QPlayer/" + Q_PLAYER_VERSION_NAME;

    /**
     * Constant, which indicates that the value is unknown.
     */

    public static final int UNKNOWN_VALUE = -1;

    /**
     * Line separator for RTSP requests and responses.
     */

    public static final String CRLF = "\r\n";

    /**
     * Prefix of H264 NAL unit in Annex B format.
     */

    public static final byte[] H264_NAL_PREFIX = new byte[]{0, 0, 0, 1};

    /**
     * Prefix of H264 NAL unit in Annex B format.
     */

    public static final byte[] H264_NAL_PREFIX_OLD = new byte[]{0, 0, 1};

}
