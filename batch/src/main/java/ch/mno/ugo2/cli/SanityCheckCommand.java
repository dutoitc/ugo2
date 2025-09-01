package ch.mno.ugo2.cli;

import ch.mno.ugo2.api.WebApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.time.Duration;
import java.util.concurrent.Callable;

@Slf4j
@Component
@RequiredArgsConstructor
@Command(name = "sanity:check", description = "Vérifie la connectivité API (health) et l'auth HMAC (POST vide).")
public class SanityCheckCommand implements Callable<Integer> {

    private final WebApiClient api;

    @Override
    public Integer call() {
        try {
            System.out.println("[sanity] /health ...");
            api.health().block(Duration.ofSeconds(10));
            System.out.println("  OK");

            System.out.println("[sanity] auth (POST /metrics:batchUpsert []) ...");
            boolean ok = api.authNoop().block(Duration.ofSeconds(10));
            if (!ok) {
                System.err.println("  Échec auth HMAC (status non-2xx)");
                return 2;
            }
            System.out.println("  OK");
            System.out.println("Sanity check: OK");
            return 0;
        } catch (Exception e) {
            System.err.println("Sanity check: FAIL -> " + e.getMessage());
            return 1;
        }
    }
}
