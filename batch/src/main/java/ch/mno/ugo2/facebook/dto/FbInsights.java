package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// facebook/dto/FbInsights.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record FbInsights(List<Datum> data) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Datum(
    String name,                  // e.g. "total_video_views"
    String period,                // "lifetime"
    List<Value> values
  ) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(Integer value) {}
  }

  public Integer firstValue(String metric) {
    return data == null ? null : data.stream()
      .filter(d -> metric.equals(d.name()))
      .findFirst()
      .map(Datum::values)
      .filter(v -> !v.isEmpty())
      .map(v -> v.get(0).value())
      .orElse(null);
  }
}
