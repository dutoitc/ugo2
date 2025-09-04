package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Représente l'erreur Graph API au format:
 * {
 *   "error": {
 *     "message": "...",
 *     "type": "GraphMethodException",
 *     "code": 100,
 *     "error_subcode": 33,
 *     "fbtrace_id": "..."
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FbError(
        @JsonProperty("error") Detail error
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            String message,
            String type,
            Integer code,
            @JsonProperty("error_subcode") Integer errorSubcode,
            @JsonProperty("fbtrace_id") String fbtraceId
    ) {}

    // Helpers d'accès "aplatis" (facilitent l'usage dans le client/exception)
    public String message()      { return error != null ? error.message()      : null; }
    public String type()         { return error != null ? error.type()         : null; }
    public Integer code()        { return error != null ? error.code()         : null; }
    public Integer errorSubcode(){ return error != null ? error.errorSubcode() : null; }
    public String fbtrace_id()   { return error != null ? error.fbtraceId()    : null; }
}
