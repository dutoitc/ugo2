package ch.mno.ugo2.cli;

import ch.mno.ugo2.service.BatchOrchestrator;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
public class UgoCli {

  private final ApplicationArguments springArgs;
  private final BatchOrchestrator orchestrator;
  private final SanityCheckCommand sanityCmd;
  private final SanitySourcesCommand sanitySourcesCmd;

  public UgoCli(ApplicationArguments springArgs,
                BatchOrchestrator orchestrator,
                SanityCheckCommand sanityCmd,
                SanitySourcesCommand sanitySourcesCmd) {
    this.springArgs = springArgs;
    this.orchestrator = orchestrator;
    this.sanityCmd = sanityCmd;
    this.sanitySourcesCmd = sanitySourcesCmd;
  }

  @PostConstruct
  void runIfArgsPresent() {
    if (springArgs.getSourceArgs().length == 0) return;
    int exit = new CommandLine(new Root(orchestrator, sanityCmd, sanitySourcesCmd))
            .execute(springArgs.getSourceArgs());
    System.exit(exit);
  }

  @Command(name = "ugo", mixinStandardHelpOptions = true, description = "UGO2 CLI")
  static class Root implements Callable<Integer> {
    private final BatchOrchestrator orchestrator;
    private final SanityCheckCommand sanityCmd;
    private final SanitySourcesCommand sanitySourcesCmd;

    Root(BatchOrchestrator o, SanityCheckCommand s, SanitySourcesCommand ss) {
      this.orchestrator = o; this.sanityCmd = s; this.sanitySourcesCmd = ss;
    }

    @Command(name="batch:run", description="Rolling discovery (last N days), then reconcile")
    int run() {
      orchestrator.run();
      return 0;
    }

    @Command(name="sanity:check", description="Vérifie /health + auth HMAC")
    int sanityCheck() {
      try { return sanityCmd.call(); }
      catch (Exception e) { System.err.println(e.getMessage()); return 1; }
    }

    @Command(name="sanity:sources", description="POST 1 source factice vers /sources:batchUpsert")
    int sanitySources() {
      try { return sanitySourcesCmd.call(); }
      catch (Exception e) { System.err.println(e.getMessage()); return 1; }
    }

    @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
  }
}
