package com.demo.itemintegration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.demo.itemintegration.config.ExternalApiProperties;

@SpringBootTest(properties = {
        "external.api.url=https://hosted.example.com/api/items",
        "external.api.graph-url=https://hosted.example.com/graph/match/execute",
        "external.api.object-url=https://hosted.example.com/objects/partial",
        "external.api.token=test-token"
})
class ItemIntegrationApplicationTests {

    @Autowired
    private ExternalApiProperties properties;

    @Test
    void contextLoadsWithEnvironmentConfiguration() {
        assertThat(properties.url()).isEqualTo("https://hosted.example.com/api/items");
        assertThat(properties.connectTimeout()).hasSeconds(5);
    }
}
