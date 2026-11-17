package com.mowoo.mowoo_backend.attachment;

import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import com.mowoo.mowoo_backend.global.config.AmazonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

class AmazonConfigStartupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AmazonConfig.class)
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withPropertyValues("cloud.aws.s3.enabled=true", "cloud.aws.s3.bucket=test-private-photos",
                    "cloud.aws.region=${AWS_REGION}");

    @Test
    void startsRealS3ConfigurationWithDeploymentRegionAndInstanceRoleCredentials() {
        // Beanstalk 템플릿이 AWS::Region으로 주입하는 값. 저장소 대역이나 정적 키를 사용하지 않는다.
        runner.withPropertyValues("AWS_REGION=ap-southeast-2").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(S3Client.class)
                    .hasSingleBean(S3Presigner.class).hasSingleBean(ObjectStorage.class);
            assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region())
                    .isEqualTo(Region.AP_SOUTHEAST_2);
        });
    }

    @Test
    void missingDeploymentRegionFailsAtStartupInsteadOfBeingHiddenByStorageMock() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("AWS_REGION");
        });
    }
}
