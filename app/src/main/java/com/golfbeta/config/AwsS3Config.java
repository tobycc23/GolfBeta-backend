package com.golfbeta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsS3Config {

    private static final Logger log = LoggerFactory.getLogger(AwsS3Config.class);

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        log.info("Creating S3Presigner for region {}", region);
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
