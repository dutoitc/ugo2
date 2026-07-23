package ch.mno.ugo2.util;

import java.util.regex.Pattern;

/**
 * Redacts credentials before text reaches logs, API errors or persisted health state.
 */
public final class SensitiveDataRedactor {

    private static final String SECRET_NAME =
            "access[_-]?token|api[_-]?key|apikey|key|token|secret|client[_-]?secret|password|passwd|"
                    + "authorization|x-api-key|x-api-sig|signature|sig|x-amz-signature|x-goog-signature";

    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:" + SECRET_NAME + ")=)[^&#\\s]*"
    );
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)((?:[\\\"']?(?:" + SECRET_NAME + ")[\\\"']?)\\s*[:=]\\s*)"
                    + "(?!\\[REDACTED\\])"
                    + "(?:\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*'|[^\\s,;&}\\]]+)"
    );
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\bauthorization\\s*[:=]\\s*)(?:(?:bearer|basic)\\s+)?[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(https?://[^:/@\\s]+:)[^@/\\s]+(@)");

    private SensitiveDataRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = AUTHORIZATION.matcher(value).replaceAll("$1[REDACTED]");
        redacted = BEARER.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = QUERY_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
        return URL_PASSWORD.matcher(redacted).replaceAll("$1[REDACTED]$2");
    }

    public static String redact(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        return error.getClass().getSimpleName() + ": " + redact(String.valueOf(error.getMessage()));
    }

    public static String redactAndTruncate(String value, int maxLength) {
        String redacted = redact(value);
        if (redacted == null || redacted.length() <= maxLength) {
            return redacted;
        }
        return redacted.substring(0, Math.max(0, maxLength)) + "…";
    }
}
