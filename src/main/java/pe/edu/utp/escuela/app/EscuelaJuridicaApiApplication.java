package pe.edu.utp.escuela.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// HU-016: habilita el job diario que transiciona cursos por fecha (CicloVidaCursoServicio).
@EnableScheduling
@SpringBootApplication
public class EscuelaJuridicaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EscuelaJuridicaApiApplication.class, args);
    }

}
