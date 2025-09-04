package ch.mno.ugo2.facebook;

import lombok.Getter;

/**
 * Exception dédiée aux erreurs Graph API.
 * Contient les champs usuels + le corps brut (pour debug).
 */
@Getter
public class FacebookApiException extends RuntimeException {

  private final Integer code;
  private final String type;
  private final String fbtraceId;
  private final String rawBody;

  public FacebookApiException(String message, Integer code, String type, String fbtraceId, String rawBody) {
    super(message);
    this.code = code;
    this.type = type;
    this.fbtraceId = fbtraceId;
    this.rawBody = rawBody;
  }

  @Override
  public String toString() {
    return "FacebookApiException{" +
      "message='" + getMessage() + '\'' +
      ", code=" + code +
      ", type='" + type + '\'' +
      ", fbtraceId='" + fbtraceId + '\'' +
      '}';
  }
}
