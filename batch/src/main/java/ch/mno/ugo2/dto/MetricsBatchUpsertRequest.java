package ch.mno.ugo2.dto;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MetricsBatchUpsertRequest {
    @Singular("add")
    List<MetricsUpsertItem> snapshots;
}
