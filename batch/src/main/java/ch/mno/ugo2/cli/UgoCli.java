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

  public UgoCli(ApplicationArguments springArgs, BatchOrchestrator orchestrator, SanityCheckCommand sanityCmd) {
    this.springArgs = springArgs;
    this.orchestrator = orchestrator;
    this.sanityCmd = sanityCmd;
  }

  @PostConstruct
  void runIfArgsPresent() {
    if (springArgs.getSourceArgs().length == 0) return;
    int exit = new CommandLine(new Root(orchestrator, sanityCmd)).execute(springArgs.getSourceArgs());
    System.exit(exit);
  }

  @Command(name = "ugo", mixinStandardHelpOptions = true, description = "UGO2 CLI")
  static class Root implements Callable<Integer> {
    private final BatchOrchestrator orchestrator;
    private final SanityCheckCommand sanityCmd;

    Root(BatchOrchestrator o, SanityCheckCommand s) { this.orchestrator = o; this.sanityCmd = s; }

    @Command(name="batch:init", description="Initial discovery (full scan), then reconcile")
    int init() { orchestrator.run(true); return 0; }

    @Command(name="batch:run", description="Rolling discovery (last N days), then reconcile")
    int run() { orchestrator.run(false); return 0; }

    @Command(name="sanity:check", description="Vérifie /health et l’auth HMAC (POST vide)")
    int sanityCheck() {
      try {
        return sanityCmd.call();
      } catch (Exception e) {
        System.err.println("sanity:check failed: " + e.getMessage());
        return 1;
      }
    }

    @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
  }
}
