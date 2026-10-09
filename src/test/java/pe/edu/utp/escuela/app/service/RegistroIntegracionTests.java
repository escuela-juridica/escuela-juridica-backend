package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.*;
import pe.edu.utp.escuela.app.mail.*;
import pe.edu.utp.escuela.app.repository.*;

@SpringBootTest @ActiveProfiles("test")
class RegistroIntegracionTests {
    @Autowired RegistroServicio servicio;
    @Autowired PersonaRepositorio personas;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired RolRepositorio roles;
    @MockitoSpyBean UsuarioRolRepositorio asignaciones;
    @Autowired CodigoVerificacionRepositorio codigos;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder sesionDecoder;
    @MockitoBean MailService mail;
    @MockitoBean VerificadorAntiRobot antiRobot;
    @MockitoBean ContextoGoogleRegistro google;
    @Autowired Clock clock;
    @MockitoSpyBean CodigoVerificacionServicio emisiones;
    @Autowired org.springframework.web.context.WebApplicationContext contexto;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach void preparar() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(contexto).apply(springSecurity()).build();
        codigos.deleteAll(); asignaciones.deleteAll(); usuarios.deleteAll(); personas.deleteAll(); roles.deleteAll();
        when(google.obtenerVerificada(anyString())).thenThrow(new InvalidTokenException());
        Rol rol = new Rol(); rol.setCodigo("ROLE_ALUMNO"); rol.setNombre("Alumno"); roles.saveAndFlush(rol);
    }
    private RegistroPeticion peticion(String correo) {
        return new RegistroPeticion("Lucía", "Caminos", " ", correo, "", " ",
                "Clave123", "Clave123", true, "token-control");
    }
    @Test void creaCuentaPendienteConOpcionalesNulosYHashSinExponerCodigo() {
        RegistroRespuesta r = servicio.registrar(peticion("LUCIA@example.com"));
        assertEquals("lucia@example.com", r.correo());
        assertTrue(r.envioAceptado());
        assertNotEquals(r.usuarioId().toString(), r.referenciaVerificacion());
        assertThrows(RuntimeException.class, () -> sesionDecoder.decode(r.referenciaVerificacion()));
        var usuario = usuarios.findById(r.usuarioId()).orElseThrow();
        assertNull(usuario.getCorreoVerificadoEn());
        assertTrue(encoder.matches("Clave123", usuario.getContrasenaHash()));
        assertEquals(1, asignaciones.count());
        var persona = personas.findAll().getFirst();
        assertNull(persona.getTelefono()); assertNull(persona.getApellidoMaterno()); assertNull(persona.getDocumentoIdentidad());
        var enviado = ArgumentCaptor.forClass(HtmlMailMessage.class);
        verify(mail).sendHtml(enviado.capture());
        assertEquals(List.of("lucia@example.com"), enviado.getValue().recipients());
        assertEquals("mail/verification-code.html", enviado.getValue().templatePath());
        String visible = (String) enviado.getValue().fields().get("codigo");
        assertTrue(visible.matches("[0-9]{6}"));
        assertTrue(encoder.matches(visible, codigos.findAll().getFirst().getCodigoHash()));
        assertEquals("ENVIADO", codigos.findAll().getFirst().getEstadoEnvio());
    }
    @Test void falloSmtpConservaCuentaEInvalidaCodigo() {
        doThrow(new MailDeliveryException()).when(mail).sendHtml(any());
        var r = servicio.registrar(peticion("smtp@example.com"));
        assertFalse(r.envioAceptado());
        assertEquals(1, usuarios.count());
        assertEquals("ERROR", codigos.findAll().getFirst().getEstadoEnvio());
        assertNotNull(codigos.findAll().getFirst().getInvalidadoEn());
    }
    @Test void falloDePersistenciaDeCodigoRevierteCuentaPersonaYRol() {
        CodigoVerificacionServicio objetivo = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(emisiones);
        doThrow(new IllegalStateException("Fallo de persistencia simulado")).when(objetivo).emitirPara(any());
        assertThrows(IllegalStateException.class, () -> servicio.registrar(peticion("rollback@example.com")));
        assertEquals(0, usuarios.count()); assertEquals(0, personas.count()); assertEquals(0, asignaciones.count());
        verifyNoInteractions(mail);
    }
    @Test void sinRolActivoNoCreaRegistrosParciales() {
        roles.deleteAll();
        assertThrows(IllegalStateException.class, () -> servicio.registrar(peticion("rol@example.com")));
        assertEquals(0, personas.count()); assertEquals(0, usuarios.count());
    }
    @Test void falloAlGuardarAsignacionReviertePersonaYUsuario() {
        doThrow(new IllegalStateException("Fallo al asignar rol")).when(asignaciones).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> servicio.registrar(peticion("fallo-rol@example.com")));
        assertEquals(0, usuarios.count()); assertEquals(0, personas.count()); assertEquals(0, codigos.count());
        verifyNoInteractions(mail);
    }
    @Test void correoDuplicadoSinDistinguirMayusculas() {
        servicio.registrar(peticion("test@example.com"));
        var e = assertThrows(CampoRegistroException.class, () -> servicio.registrar(peticion("TEST@example.com")));
        assertEquals("CORREO_DUPLICADO", e.getCode());
        assertEquals(1, usuarios.count()); assertEquals(1, personas.count());
    }
    @Test void dosSolicitudesSimultaneasCreanUnaSolaCuenta() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> tarea = () -> {
                inicio.await();
                try { servicio.registrar(peticion("carrera@example.com")); return true; }
                catch (CampoRegistroException e) { assertEquals(HttpStatus.CONFLICT, e.getStatus()); return false; }
            };
            var a = pool.submit(tarea); var c = pool.submit(tarea);
            inicio.countDown();
            assertNotEquals(a.get(20, TimeUnit.SECONDS), c.get(20, TimeUnit.SECONDS));
            assertEquals(1, usuarios.count()); assertEquals(1, personas.count()); assertEquals(1, codigos.count());
        }
    }
    @Test void validaLegalConfirmacionYAntirobotAntesDeGuardar() {
        var p = peticion("valida@example.com");
        var ilegal = new RegistroPeticion(p.nombres(), p.apellidoPaterno(), null, p.correo(), null, null,
                p.contrasena(), p.confirmarContrasena(), false, p.evidenciaAntiRobot());
        assertThrows(CampoRegistroException.class, () -> servicio.registrar(ilegal));
        var distinta = new RegistroPeticion(p.nombres(), p.apellidoPaterno(), null, p.correo(), null, null,
                p.contrasena(), "Distinta123", true, p.evidenciaAntiRobot());
        assertThrows(CampoRegistroException.class, () -> servicio.registrar(distinta));
        doThrow(new BusinessValidationException("Control inválido")).when(antiRobot).verificar(any());
        assertThrows(BusinessValidationException.class, () -> servicio.registrar(p));
        assertEquals(0, usuarios.count());
        verifyNoInteractions(mail);
    }
    @Test void googleCreaCuentaVerificadaSinPasswordNiCorreoAdicional() {
        String ref = contextoValido("referencia-hu001", "google-sub", "google@example.com");
        var contexto = servicio.contextoGoogle(ref);
        assertEquals("Caminos Quiroz", contexto.apellidos());
        var sesion = servicio.completarGoogle(new RegistroGooglePeticion(ref, "Lucía", "Caminos Quiroz", null, null, null, true));
        Long id = Long.valueOf(sesionDecoder.decode(sesion.token()).getSubject());
        var usuario = usuarios.findById(id).orElseThrow();
        assertNotNull(usuario.getCorreoVerificadoEn());
        assertNull(usuario.getContrasenaHash());
        assertEquals("google-sub", usuario.getGoogleSubject());
        assertEquals(0, codigos.count());
        verifyNoInteractions(mail, antiRobot);
        assertThrows(CampoRegistroException.class, () -> servicio.completarGoogle(
                new RegistroGooglePeticion(ref, "Lucía", "Caminos Quiroz", null, null, null, true)));
        assertEquals(1, usuarios.count());
    }
    @Test void referenciaManipuladaNoPermiteCrearCuenta() {
        assertThrows(InvalidTokenException.class, () -> servicio.contextoGoogle("referencia-manipulada"));
        assertEquals(0, usuarios.count());
    }

    private String contextoValido(String referencia, String subject, String correo) {
        // Identidad ya comprobada por HU-001 simulada únicamente en pruebas.
        doReturn(new ContextoGoogleRegistro.Identidad(subject, correo, "Lucía", "Caminos Quiroz", "",
                clock.instant().plusSeconds(300))).when(google).obtenerVerificada(referencia);
        return referencia;
    }

    @Test void contratoHttpDevuelve201SinPasswordNiCodigo() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json")
                .content(json.writeValueAsString(peticion("http@example.com"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.envioAceptado").value(true))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(jsonPath("$.referenciaVerificacion").isString());
    }

    @Test void contratoHttpDevuelveErroresDeCamposYDuplicados() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        servicio.registrar(peticion("duplicado@example.com"));
        mvc.perform(post("/api/auth/registro").contentType("application/json")
                .content(json.writeValueAsString(peticion("DUPLICADO@example.com"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CORREO_DUPLICADO"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("correo"));
    }

    @Test void googleCompletaEIniciaSesionUsandoServiciosCompartidos() throws Exception {
        String ref = contextoValido("contexto-cookie", "google-cookie", "cookie@example.com");
        String cuerpo = json.writeValueAsString(new RegistroGooglePeticion(ref, "Lucía", "Caminos", null, null, null, true));
        var respuesta = mvc.perform(post("/api/auth/registro/google")
                .contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("alumno"))
                .andExpect(jsonPath("$.email").value("cookie@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse();
        var cookie = respuesta.getCookie("ESEJUR_SESION");
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly());
        var jwt = sesionDecoder.decode(cookie.getValue());
        assertEquals("cookie@example.com", jwt.getClaimAsString("email"));
        assertEquals(List.of("ALUMNO"), jwt.getClaimAsStringList("roles"));
    }

    @Test void documentoDuplicadoYPasswordExtensaNoCreanPersonasHuerfanas() {
        var p = peticion("doc1@example.com");
        servicio.registrar(new RegistroPeticion(p.nombres(), p.apellidoPaterno(), null, p.correo(),
                null, "12345678", p.contrasena(), p.confirmarContrasena(), true, p.evidenciaAntiRobot()));
        var e = assertThrows(CampoRegistroException.class, () -> servicio.registrar(new RegistroPeticion(
                p.nombres(), p.apellidoPaterno(), null, "doc2@example.com", null, "12345678",
                p.contrasena(), p.confirmarContrasena(), true, p.evidenciaAntiRobot())));
        assertEquals("DOCUMENTO_DUPLICADO", e.getCode());
        String extensa = "Á".repeat(35) + "bc123";
        assertThrows(CampoRegistroException.class, () -> servicio.registrar(new RegistroPeticion(
                p.nombres(), p.apellidoPaterno(), null, "larga@example.com", null, null,
                extensa, extensa, true, p.evidenciaAntiRobot())));
        assertEquals(1, usuarios.count()); assertEquals(1, personas.count());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Cla1", "clave123", "CLAVE123", "Claveabc"})
    void rechazaCadaIncumplimientoDeLaPolitica(String password) {
        var p = peticion("politica@example.com");
        assertThrows(BusinessValidationException.class, () -> servicio.registrar(new RegistroPeticion(
                p.nombres(), p.apellidoPaterno(), null, p.correo(), null, null,
                password, password, true, p.evidenciaAntiRobot())));
        assertEquals(0, usuarios.count());
        verifyNoInteractions(antiRobot, mail);
    }

    @Test void evidenciaAusenteNoCreaCuenta() {
        var p = peticion("sin-evidencia@example.com");
        assertThrows(CampoRegistroException.class, () -> servicio.registrar(new RegistroPeticion(
                p.nombres(), p.apellidoPaterno(), null, p.correo(), null, null,
                p.contrasena(), p.confirmarContrasena(), true, "")));
        assertEquals(0, personas.count());
        verifyNoInteractions(antiRobot, mail);
    }

    @Test void contextoGoogleVencidoNoCreaCuenta() {
        doReturn(new ContextoGoogleRegistro.Identidad("sub-vencido", "vencido@example.com", "Ana", "Pérez", "",
                clock.instant().minusSeconds(1))).when(google).obtenerVerificada("vencido");
        assertThrows(InvalidTokenException.class, () -> servicio.completarGoogle(
                new RegistroGooglePeticion("vencido", "Ana", "Pérez", null, null, null, true)));
        assertEquals(0, personas.count()); assertEquals(0, usuarios.count());
        verifyNoInteractions(mail);
    }

    @Test void googleExigeAceptacionYNoDuplicaCorreoDeFormulario() {
        String ref = contextoValido("contexto-legal", "sub-legal", "legal@example.com");
        assertThrows(CampoRegistroException.class, () -> servicio.completarGoogle(
                new RegistroGooglePeticion(ref, "Ana", "Pérez", null, null, null, false)));
        assertEquals(0, usuarios.count());
        servicio.registrar(peticion("legal@example.com"));
        assertThrows(CampoRegistroException.class, () -> servicio.completarGoogle(
                new RegistroGooglePeticion(ref, "Ana", "Pérez", null, null, null, true)));
        assertEquals(1, usuarios.count()); assertEquals(1, personas.count());
    }

    @Test void consultarGoogleSinCompletarNoCreaRegistros() {
        String ref = contextoValido("contexto-cancelado", "sub-cancelado", "cancelado@example.com");
        servicio.contextoGoogle(ref);
        assertEquals(0, usuarios.count()); assertEquals(0, personas.count());
        verifyNoInteractions(mail, antiRobot);
    }

    @Test void controladorSoloExponeLasTresOperacionesDelMapa() {
        var mappings = contexto.getBean("requestMappingHandlerMapping",
                org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        var rutas = mappings.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getValue().getBeanType().equals(pe.edu.utp.escuela.app.controller.RegistroControlador.class))
                .flatMap(e -> e.getKey().getPatternValues().stream()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("/api/auth/registro", "/api/auth/registro/google",
                "/api/auth/registro/google/{referencia}"), rutas);
    }
}
