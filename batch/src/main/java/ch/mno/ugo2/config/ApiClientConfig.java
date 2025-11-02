package ch.mno.ugo2.config;

import ch.mno.ugo2.api.WebApiClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(ApiClientConfig.ApiProps.class)
@Slf4j
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
    public WebClient facebookWebClient() {
        return WebClient.builder().baseUrl("https://graph.facebook.com/").build();
    }

    @Bean
    public WebClient youtubeWebClient() {
        return WebClient.builder().filter(maskedRequestLog()).filter(logResponse()).build();
    }



    protected ExchangeFilterFunction maskedRequestLog() {
        return ExchangeFilterFunction.ofRequestProcessor((ClientRequest req) -> {
            String u = req.url().toString().replaceAll("([?&]key=)[^&]+", "$1***");
            log.debug("HTTP -> {} {}", req.method(), u);
            return Mono.just(req);
        });
    }

    protected ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor((ClientResponse resp) -> {
            log.debug("HTTP <- {}", resp.statusCode().value());
            return Mono.just(resp);
        });
    }

}
