package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.repository.*;

/** Contexto real sin mocks: no hay bypass del anti-robot ni de HU-001. */
@SpringBootTest
@ActiveProfiles("test")
class RegistroSinIntegracionesTests {
    @Autowired RegistroService registro;
    @Autowired UsuarioRepository usuarios;
    @Autowired PersonaRepository personas;

    @Test void formularioSinEvidenciaRealNoInserta() {
        long cuentas = usuarios.count(), personasAntes = personas.count();
        var p = new RegistroPeticion("Ana", "Pérez", null, "sin-adaptador@example.com", null, null,
                "Clave123", "Clave123", true, "texto-no-verificado");
        var error = assertThrows(BusinessException.class, () -> registro.registrar(p));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("ANTIROBOT_INVALIDO", error.getCode());
        assertEquals(cuentas, usuarios.count()); assertEquals(personasAntes, personas.count());
    }

    @Test void googleSinHu001NoInserta() {
        long cuentas = usuarios.count(), personasAntes = personas.count();
        var p = new RegistroGooglePeticion("referencia-no-verificada", "Ana", "Pérez", null, null, null, true);
        var error = assertThrows(BusinessException.class, () -> registro.completarGoogle(p));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("GOOGLE_PENDIENTE", error.getCode());
        assertEquals(cuentas, usuarios.count()); assertEquals(personasAntes, personas.count());
    }
}
