package com.mowoo.mowoo_backend.attachment.storage;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    @Override
    public void put(String key, byte[] bytes, String mimeType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType)
                .cacheControl("private, no-store").build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public String signedUrl(String key, String mimeType) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentType(mimeType).responseContentDisposition("inline")
                .responseCacheControl("private, no-store").build();
        return presigner.presignGetObject(b -> b.signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(request)).url().toExternalForm();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
