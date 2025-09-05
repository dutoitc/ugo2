package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightMetric(
        String name,
        String period,
        List<InsightValue> values,
        String title,
        String description,
        String id
) {}