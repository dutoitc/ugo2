package ch.mno.ugo2.cli;

import ch.mno.ugo2.service.BatchOrchestrator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
public class UgoCli {

  private final ApplicationArguments springArgs;
  private final BatchOrchestrator orchestrator;

  @Autowired
  public UgoCli(ApplicationArguments springArgs, BatchOrchestrator orchestrator) {
    this.springArgs = springArgs;
    this.orchestrator = orchestrator;
  }

  @PostConstruct
  void runIfArgsPresent() {
    if (springArgs.getSourceArgs().length == 0) return;
    int exit = new CommandLine(new Root(orchestrator)).execute(springArgs.getSourceArgs());
    System.exit(exit);
  }

  @Command(name = "ugo", mixinStandardHelpOptions = true, description = "UGO2 CLI")
  static class Root implements Callable<Integer> {
    private final BatchOrchestrator orchestrator;
    Root(BatchOrchestrator o) { this.orchestrator = o; }

    @Command(name="batch:init", description="Initial discovery (full scan), then reconcile")
    int init() { orchestrator.run(true); return 0; }

    @Command(name="batch:run", description="Rolling discovery (last N days), then reconcile")
    int run() { orchestrator.run(false); return 0; }

    @Override public Integer call() { CommandLine.usage(this, System.out); return 0; }
  }
}
