package ch.mno.ugo2.config;

import ch.mno.ugo2.api.WebApiClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ApiClientConfig.ApiProps.class)
public class ApiClientConfig {

    @Bean
    @Primary
    public WebApiClient webApiClient(ApiProps p) {
        return WebApiClient.create(p.getBaseUrl(), p.getKeyId(), p.getSecret(), p.getMaxBatch());
    }

    @Data
    @ConfigurationProperties(prefix = "ugo2.api")
    public static class ApiProps {
        private String baseUrl;
        private String keyId;
        private String secret;
        private int maxBatch = 1000;
    }

    @Bean
    public WebClient facebookGraph(@Value("${ugo2.facebook.api-version:v23.0}") String v) {
        return WebClient.builder().baseUrl("https://graph.facebook.com/" + v).build();
    }


}
