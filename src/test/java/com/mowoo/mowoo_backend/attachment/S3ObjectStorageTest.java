package com.mowoo.mowoo_backend.attachment;

import com.mowoo.mowoo_backend.attachment.storage.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ObjectStorageTest {
    @Test
    void signsPrivateDownloadsForFiveMinutesWithoutContactingAws() {
        S3Client client=mock(S3Client.class);
        try(var presigner=S3Presigner.builder().region(Region.AP_SOUTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key","test-secret"))).build()) {
            var storage=new S3ObjectStorage(client,presigner,"private-photos");
            String url=storage.signedUrl("uploads/owner/photo.png","image/png");
            assertThat(url).startsWith("https://private-photos.s3.ap-southeast-2.amazonaws.com/uploads/owner/photo.png?")
                    .contains("X-Amz-Expires=300").contains("response-content-type=image%2Fpng")
                    .contains("response-cache-control=private%2C%20no-store");
            verifyNoInteractions(client);
            storage.put("uploads/owner/photo.png",new byte[]{1,2,3},"image/png");
            ArgumentCaptor<PutObjectRequest> request=ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(client).putObject(request.capture(),any(software.amazon.awssdk.core.sync.RequestBody.class));
            assertThat(request.getValue().bucket()).isEqualTo("private-photos");
            assertThat(request.getValue().contentType()).isEqualTo("image/png");
            assertThat(request.getValue().cacheControl()).isEqualTo("private, no-store");
            assertThat(request.getValue().acl()).isNull();
        }
    }
}
