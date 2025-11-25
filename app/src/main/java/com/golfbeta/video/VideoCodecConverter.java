package com.golfbeta.video;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class VideoCodecConverter implements Converter<String, VideoCodec> {

    @Override
    public VideoCodec convert(String source) {
        return VideoCodec.fromValue(source);
    }
}
