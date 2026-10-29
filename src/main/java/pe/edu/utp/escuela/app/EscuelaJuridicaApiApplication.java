package pe.edu.utp.escuela.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EscuelaJuridicaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EscuelaJuridicaApiApplication.class, args);
    }

}
