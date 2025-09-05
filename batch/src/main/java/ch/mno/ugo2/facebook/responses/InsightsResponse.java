package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsightsResponse(List<InsightMetric> data) {}

