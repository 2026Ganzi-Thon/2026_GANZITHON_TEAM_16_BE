package com.mowoo.mowoo_backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import com.mowoo.mowoo_backend.attachment.storage.S3ObjectStorage;

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
            @Value("${cloud.aws.credentials.access-key:}") String accessKey,
            @Value("${cloud.aws.credentials.secret-key:}") String secretKey,
            @Value("${cloud.aws.region}") String region,
            @Value("${cloud.aws.s3.path.location:uploads}") String locationPath
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
                .credentialsProvider(storageCredentials())
                .build();
    }

    @Bean S3Presigner s3Presigner() {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(storageCredentials()).build();
    }

    @Bean ObjectStorage objectStorage(S3Client client, S3Presigner presigner) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("S3 버킷이 필요합니다.");
        return new S3ObjectStorage(client, presigner, bucket);
    }

    private AwsCredentialsProvider storageCredentials() {
        if (accessKey.isBlank() != secretKey.isBlank()) throw new IllegalArgumentException("AWS 키를 모두 설정해 주세요.");
        // 운영에서는 EC2 역할의 임시 자격 증명을 사용한다.
        return accessKey.isBlank() ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
