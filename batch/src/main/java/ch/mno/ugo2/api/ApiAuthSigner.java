package ch.mno.ugo2.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Signs requests with HMAC-SHA256 using headers:
 * X-API-KEY, X-API-TS, X-API-NONCE, X-API-SIG .
 * StringToSign = keyId + \n + ts + \n + nonce + \n + METHOD + \n + PATH + \n + sha256(body)
 */
@RequiredArgsConstructor
public class ApiAuthSigner {
    private final String keyId;
    private final String secret;

    public void sign(HttpHeaders h, String method, String path, byte[] body) {
        Assert.hasText(keyId, "keyId");
        Assert.hasText(secret, "secret");
        long ts = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body == null ? new byte[0] : body);
        String s2s = keyId + "\n" + ts + "\n" + nonce + "\n" + method.toUpperCase() + "\n" + path + "\n" + bodyHash;
        String sig = hmacBase64(secret, s2s);
        h.set("X-API-KEY", keyId);
        h.set("X-API-TS", String.valueOf(ts));
        h.set("X-API-NONCE", nonce);
        h.set("X-API-SIG", sig);
        h.setContentType(MediaType.APPLICATION_JSON);
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hmacBase64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
