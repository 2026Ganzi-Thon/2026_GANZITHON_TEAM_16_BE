package com.mowoo.mowoo_backend.ai.solar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SolarConfigurationTest {
    @Test
    void rejectsNonProviderOrUnencryptedDestinationsWithoutEchoingConfiguration() {
        SolarProperties settings = new SolarProperties();
        for (String url : new String[]{"http://api.upstage.ai/v1", "https://example.com/v1",
                "https://api.upstage.ai/v1?key=private-config-text", "malformed private-config-text"}) {
            settings.setBaseUrl(url);
            assertThatThrownBy(() -> new SolarConfiguration().solarRestClient(settings))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private-config-text");
        }
    }

    @Test
    void keyIsNotIncludedInPropertiesToString() {
        SolarProperties settings = new SolarProperties();
        settings.setApiKey("unused-sensitive-fixture");
        assertThat(settings.toString()).doesNotContain("unused-sensitive-fixture");
    }

    @Test
    void invalidTimeoutFailsBeforeBuildingClient() {
        SolarProperties settings = new SolarProperties();
        settings.setReadTimeoutMs(0);
        assertThatThrownBy(() -> new SolarConfiguration().solarRestClient(settings))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
