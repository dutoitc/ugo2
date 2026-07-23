package ch.mno.ugo2.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {

    @Test
    void redactsQueryJsonHeadersAndSignedUrls() {
        String input = "GET https://example.org/data?access_token=meta-value&key=youtube-value"
                + " Authorization: Bearer bearer-value"
                + " authorization=Basic YmFzaWMtdmFsdWU="
                + " body={\"client_secret\":\"client-value\"}"
                + " signed=https://storage.example/file?X-Amz-Signature=signed-value";

        String result = SensitiveDataRedactor.redact(input);

        assertThat(result)
                .doesNotContain("meta-value", "youtube-value", "bearer-value", "YmFzaWMtdmFsdWU=",
                        "client-value", "signed-value")
                .contains("access_token=[REDACTED]", "key=[REDACTED]", "Bearer [REDACTED]")
                .contains("X-Amz-Signature=[REDACTED]");
    }

    @Test
    void redactsPasswordEmbeddedInUrl() {
        assertThat(SensitiveDataRedactor.redact("https://user:private-value@example.org/api"))
                .isEqualTo("https://user:[REDACTED]@example.org/api");
    }

    @Test
    void keepsOrdinaryDiagnosticContext() {
        assertThat(SensitiveDataRedactor.redact("HTTP 429 on /api/v1/videos"))
                .isEqualTo("HTTP 429 on /api/v1/videos");
    }
}
