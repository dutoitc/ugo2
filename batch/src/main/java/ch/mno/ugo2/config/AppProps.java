package ch.mno.ugo2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProps {

  // ==== Batch (modes auto) ====
  @NotNull private Batch batch = new Batch();
  public Batch getBatch() { return batch; }
  public static class Batch { public boolean initial = false; public int rollingDays = 7; }

  // ==== Plateformes (activations + IDs) ====
  @NotNull private Platforms platforms = new Platforms();
  public Platforms getPlatforms() { return platforms; }
  public static class Platforms {
    public Facebook fb = new Facebook();
    public YouTube  yt = new YouTube();
    public Instagram ig = new Instagram();
    public WordPress wp = new WordPress();
  }
  public static class Facebook { public boolean enabled = true;  public String pageId = "";     public String accessToken = ""; }
  public static class YouTube  { public boolean enabled = true;  public String channelId = "";  public String apiKey = ""; }
  public static class Instagram{ public boolean enabled = false; public String userId = "";     public String accessToken = ""; }
  public static class WordPress{ public boolean enabled = true;  public String baseUrl = ""; }

  // ==== Budgets (soft caps) ====
  @NotNull private Budgets budgets = new Budgets();
  public Budgets getBudgets() { return budgets; }
  public static class Budgets { public int fbCalls = 5000; public int ytCalls = 5000; public int igCalls = 5000; public int wpCalls = 2000; }

  // ==== Réconciliation (fuzzy + heuristiques) ====
  @NotNull private Reconcile reconcile = new Reconcile();
  public Reconcile getReconcile() { return reconcile; }
  public static class Reconcile {
    public double titleWeight = 0.6;
    public double descWeight  = 0.3;
    public double timeBonusHours = 72.0;
    public double timeWeight  = 0.1;
    public double threshold   = 0.78;
    public String[] teaserKeywords = new String[]{"teaser","extrait","bande-annonce","short","reel","b-a","trailer"};
    public int teaserMaxSeconds = 60;
  }
}
