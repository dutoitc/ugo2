package ch.mno.ugo2.cli;

import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.service.WebApiSinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "sanity:sources", description = "POST 1 source factice vers /api/v1/sources:batchUpsert pour valider l’endpoint.")
public class SanitySourcesCommand implements Callable<Integer> {

  private final WebApiSinkService sink;

  @Override
  public Integer call() {
    String id = "sanity-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID();
    SourceUpsertItem item = SourceUpsertItem.builder()
            .platform("YOUTUBE")
            .platform_source_id(id)
            .title("Sanity Test Source")
            .description("dummy")
            .permalink_url("https://example.invalid/" + id)
            .media_type("VIDEO")
            .duration_seconds(10)
            .published_at(Instant.now().toString())
            .is_teaser(0)
            .video_id(null)
            .locked(0)
            .build();
    sink.pushSources(List.of(item));
    System.out.println("OK: source envoyée id=" + id);
    return 0;
  }
}
