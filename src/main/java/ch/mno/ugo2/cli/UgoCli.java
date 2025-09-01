package ch.mno.ugo2.cli;

import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.config.Enums.IngestMode;
import ch.mno.ugo2.config.Enums.UpdateProfile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Batch-first CLI (squelette).
 * Profiles: J0, J1TOJ7, POSTJ7, ARCHIVE.
 * Modes: stats | discover | both.
 */
@Component
public class UgoCli {

  private final ApplicationArguments springArgs;
  private final AppProps props;

  @Autowired
  public UgoCli(ApplicationArguments springArgs, AppProps props) {
    this.springArgs = springArgs;
    this.props = props;
  }

  @PostConstruct
  void runIfArgsPresent() {
    if (springArgs.getSourceArgs().length == 0) return;
    int exit = new CommandLine(new Root(props))
        .setCaseInsensitiveEnumValuesAllowed(true)
        .execute(springArgs.getSourceArgs());
    System.exit(exit);
  }

  @Command(name = "ugo", mixinStandardHelpOptions = true, description = "UGO2 CLI")
  static class Root implements Callable<Integer> {
    private final AppProps props;

    Root(AppProps p) { this.props = p; }

    @Command(name="import:init", description="Initial import (resume when quotas hit)", mixinStandardHelpOptions = true)
    int importInit(@Option(names="--modes", split=",", defaultValue="both") IngestMode[] modes) {
      System.out.printf("[import:init] tenant=%s modes=%s%n",
          props.getTenant().id(), Arrays.toString(modes));
      // TODO: discovery + stats initial, resume via checkpoints
      return 0;
    }

    @Command(name="update", description="Incremental update", mixinStandardHelpOptions = true)
    int update(@Option(names="--profile", required=true) UpdateProfile profile,
               @Option(names="--mode", defaultValue="stats") IngestMode mode,
               @Option(names="--since") String sinceIsoDate) {
      LocalDate since = sinceIsoDate!=null ? LocalDate.parse(sinceIsoDate) : null;
      System.out.printf("[update] tenant=%s profile=%s mode=%s since=%s%n",
          props.getTenant().id(), profile, mode, since);
      // TODO: schedule windows & cadence per profile; apply ETag/If-None-Match
      return 0;
    }

    @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
  }
}

