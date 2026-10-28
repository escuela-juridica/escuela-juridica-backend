package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.http.Cookie;
import pe.edu.utp.escuela.app.dto.RegistroRequest;
import pe.edu.utp.escuela.app.entity.Rol;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.*;
import tools.jackson.databind.json.JsonMapper;

/** Backend real, anti-robot real, H2 aislado y correo simulado. No usa la BD compartida. */
@SpringBootTest @ActiveProfiles("test")
class AntiRobotRegistroHttpTests {
    @Autowired WebApplicationContext contexto;
    @Autowired JsonMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired PersonaRepository personas;
    @Autowired CodigoVerificacionRepository codigos;
    @Autowired UsuarioRolRepository asignaciones;
    @MockitoBean MailService mail;
    MockMvc mvc;

    @BeforeEach void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        codigos.deleteAll(); asignaciones.deleteAll(); usuarios.deleteAll(); personas.deleteAll(); roles.deleteAll();
        var rol = new Rol(); rol.setCodigo("ROLE_ALUMNO"); rol.setNombre("Alumno"); roles.saveAndFlush(rol);
    }

    @Test void desafioEvidenciaRegistroYReutilizacion() throws Exception {
        var inicio = mvc.perform(post("/api/auth/registro/antirobot/desafios"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.respuesta").doesNotExist()).andReturn().getResponse();
        Cookie cookie = inicio.getCookie("HU002_ANTIROBOT");
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly());
        var desafio = json.readTree(inicio.getContentAsString());
        var numeros = java.util.regex.Pattern.compile("(\\d+) \\+ (\\d+)").matcher(desafio.get("pregunta").asText());
        assertTrue(numeros.find());
        int respuesta = Integer.parseInt(numeros.group(1)) + Integer.parseInt(numeros.group(2));
        String solucion = json.writeValueAsString(java.util.Map.of("desafioId", desafio.get("desafioId").asText(), "respuesta", respuesta));
        mvc.perform(post("/api/auth/registro/antirobot/verificar").contentType("application/json").content(solucion))
                .andExpect(status().isBadRequest());
        var verificacion = mvc.perform(post("/api/auth/registro/antirobot/verificar").cookie(cookie)
                .contentType("application/json").content(solucion)).andExpect(status().isOk()).andReturn().getResponse();
        String prueba = json.readTree(verificacion.getContentAsString()).get("evidencia").asText();
        String registro = json.writeValueAsString(new RegistroRequest("Ana", "Pérez", null, "nativo@example.com",
                null, null, "Clave123", "Clave123", true, prueba));
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isBadRequest());
        assertEquals(0, usuarios.count());
        mvc.perform(post("/api/auth/registro").cookie(cookie).contentType("application/json").content(registro))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.envioAceptado").value(true));
        assertEquals(1, usuarios.count());
        verify(mail, times(1)).sendHtml(any());
        mvc.perform(post("/api/auth/registro").cookie(cookie).contentType("application/json").content(registro))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ANTIROBOT_INVALIDO"));
        assertEquals(1, usuarios.count());
    }

    @Test void rechazaCuerpoInvalidoSinCrearCuenta() throws Exception {
        mvc.perform(post("/api/auth/registro/antirobot/verificar").contentType("application/json")
                .content("{\"desafioId\":\"inventado\",\"respuesta\":-1}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, usuarios.count()); verifyNoInteractions(mail);
    }
}
