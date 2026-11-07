package com.mowoo.mowoo_backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import lombok.Getter;

@Configuration
@ConditionalOnProperty(name = "cloud.aws.s3.enabled", havingValue = "true")
@Getter
public class AmazonConfig {
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String locationPath;

    public AmazonConfig(
            @Value("${cloud.aws.s3.bucket}") String bucket,
            @Value("${cloud.aws.credentials.access-key}") String accessKey,
            @Value("${cloud.aws.credentials.secret-key}") String secretKey,
            @Value("${cloud.aws.region}") String region,
            @Value("${cloud.aws.s3.path.location}") String locationPath
    ) {
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.locationPath = locationPath;
    }

    @Bean S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }
}
