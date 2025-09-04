package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// facebook/dto/GraphPage.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphPage<T>(
  List<T> data,
  Paging paging
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Paging(Cursors cursors, String next) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cursors(String after) {}
  }
}
