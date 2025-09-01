package ch.mno.ugo2;

import ch.mno.ugo2.cli.UgoCli;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Ugo2Application {

  public static void main(String[] args) {
    ConfigurableApplicationContext ctx = SpringApplication.run(Ugo2Application.class, args);
    ApplicationArguments a = ctx.getBean(ApplicationArguments.class);
    if (a.getSourceArgs().length == 0) {
      System.out.println("UGO2 started. No CLI args provided. Try:");
      System.out.println("  java -jar target/ugo2-0.2.0-SNAPSHOT.jar import:init");
      System.out.println("  java -jar target/ugo2-0.2.0-SNAPSHOT.jar update --profile=J0 --mode=stats");
    }
  }
}

