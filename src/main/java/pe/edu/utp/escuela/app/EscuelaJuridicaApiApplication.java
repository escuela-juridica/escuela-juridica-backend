package pe.edu.utp.escuela.app;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import pe.edu.utp.escuela.app.config.ValidadorConfiguracionProduccion;

@EnableScheduling
@SpringBootApplication
public class EscuelaJuridicaApiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EscuelaJuridicaApiApplication.class);
        app.setDefaultProperties(Map.of("spring.profiles.default", ejecutandoDesdeJar() ? "prod" : "local"));
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) evento ->
                ValidadorConfiguracionProduccion.validar(evento.getEnvironment()));
        app.run(args);
    }

    private static boolean ejecutandoDesdeJar() {
        return String.valueOf(EscuelaJuridicaApiApplication.class.getResource("EscuelaJuridicaApiApplication.class"))
                .startsWith("jar:");
    }

}
