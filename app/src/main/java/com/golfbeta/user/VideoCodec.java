package com.golfbeta.user;

import java.util.Locale;

public enum VideoCodec {
    H264("h264"),
    HEVC("hevc");

    private final String value;

    VideoCodec(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static VideoCodec fromValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Codec value is required");
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (VideoCodec codec : values()) {
            if (codec.value.equals(normalized)) {
                return codec;
            }
        }
        throw new IllegalArgumentException("Unsupported video codec: " + raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
