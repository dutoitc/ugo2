# PR3 - Batch -> Web API sink (no local DB)

This drop removes DB write paths from the Java batch and introduces a Web API sink
that pushes Sources/Metrics/Overrides into the PHP API.

## What changed
- **Removed need for JPA/Flyway** in batch (pom cleaned).
- New classes:
  - `ch.mno.ugo2.api.ApiAuthSigner` (HMAC headers).
  - `ch.mno.ugo2.api.WebApiClient` (endpoints `/api/v1/*`, chunked, retry/backoff).
  - `ch.mno.ugo2.service.WebApiSinkService` (blocking convenience).
  - DTOs in `ch.mno.ugo2.dto.*` matching API payloads.
- `batch/src/main/resources/application.properties.tmpl` contains API config.

## Wire-up (quick)
1. Construct `WebApiClient` as a Spring bean:
   ```java
   @Bean WebApiClient apiClient(AppProps p) {
     return WebApiClient.create(p.getApi().getBaseUrl(), p.getApi().getKeyId(), p.getApi().getSecret(), p.getApi().getMaxBatch());
   }
   ```
2. Inject `WebApiSinkService` where you previously saved to DB, and call `pushSources(...)` / `pushMetrics(...)`.
3. Delete or ignore any JPA repositories/entities remaining in the classpath.

## Headers (server expects)
- `X-API-KEY`, `X-API-TS`, `X-API-NONCE`, `X-API-SIG`
- `Idempotency-Key` (optional but used)

Signature string: `keyId + "\n" + ts + "\n" + nonce + "\n" + method + "\n" + path + "\n" + sha256(body)`
