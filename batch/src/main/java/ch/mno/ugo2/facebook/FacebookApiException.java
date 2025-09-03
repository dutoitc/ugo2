package ch.mno.ugo2.facebook;

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

  public Integer getCode() { return code; }
  public String getType() { return type; }
  public String getFbtraceId() { return fbtraceId; }
  public String getRawBody() { return rawBody; }
}
