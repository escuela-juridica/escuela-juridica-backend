package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import pe.edu.utp.escuela.app.entity.Reclamacion;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.repository.ReclamacionRepository;

/** EP06 · HU-039 / HU-040 — Libro de reclamaciones: presentar una queja o reclamo (con o sin sesión), validaciones,
 * constancia, aviso a la Escuela y atención por parte del administrador. */
class ReclamacionHttpTests extends HttpIntegracionBase {

    @Autowired ReclamacionRepository reclamaciones;

    private Map<String, Object> datos(String tipoDocumento, String numeroDocumento) {
        return Map.ofEntries(
                Map.entry("tipo", "RECLAMO"),
                Map.entry("tipoDocumento", tipoDocumento),
                Map.entry("numeroDocumento", numeroDocumento),
                Map.entry("nombres", "María"),
                Map.entry("apellidos", "Quispe Rojas"),
                Map.entry("correo", "maria@example.com"),
                Map.entry("celular", "999 888 777"),
                Map.entry("direccion", "Av. Arequipa 1234, Lima"),
                Map.entry("montoReclamado", 320.00),
                Map.entry("descripcionBien", "Curso de Derecho Procesal Penal"),
                Map.entry("detalle", "El pago fue descontado pero no pude ingresar al curso."),
                Map.entry("pedido", "Solicito la activación de mi matrícula o la devolución."),
                Map.entry("declaracionJurada", true),
                Map.entry("autorizaRespuestaCorreo", true));
    }

    private MockMultipartHttpServletRequestBuilder formulario(String ruta, Map<String, Object> datos) throws Exception {
        MockMultipartFile parte = new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsBytes(datos));
        return multipart(ruta).file(parte);
    }

    @Test
    void cualquierPersonaPresentaUnReclamoSinSesionYRecibeSuConstancia() throws Exception {
        mvc.perform(formulario("/api/publico/reclamaciones", datos("DNI", "12345678")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("LR-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.tipo").value("RECLAMO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_RESPUESTA"))
                .andExpect(jsonPath("$.constanciaEnviada").value(true))
                .andExpect(jsonPath("$.correo").value("maria@example.com"))
                .andExpect(jsonPath("$.adjuntos.length()").value(0));

        Reclamacion guardada = reclamaciones.findAll().getFirst();
        assertNull(guardada.getUsuario());
        assertEquals("12345678", guardada.getNumeroDocumento());
        assertEquals("ENVIADO", guardada.getEstadoEnvioConstancia());
        assertTrue(guardada.getFechaLimiteRespuesta().isAfter(LocalDate.now()),
                "El plazo de respuesta (15 días hábiles) debe quedar en el futuro");
        // Una constancia para quien reclama y un aviso para la Escuela.
        verify(correo, times(2)).sendHtml(any());
    }

    @Test
    void conSesionElReclamoQuedaLigadoALaCuentaYSePrecargaSuInformacion() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/app/reclamaciones/precarga").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana"))
                .andExpect(jsonPath("$.correo").value(alumno.getCorreo()))
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"));

        mvc.perform(formulario("/api/app/reclamaciones", datos("DNI", "87654321"))
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.startsWith("LR-")));

        assertEquals(alumno.getId(), reclamaciones.findAll().getFirst().getUsuario().getId());
    }

    @Test
    void sinSesionNoSePrecargaElFormulario() throws Exception {
        mvc.perform(get("/api/app/reclamaciones/precarga")).andExpect(status().isUnauthorized());
    }

    @Test
    void unDniQueNoTieneOchoDigitosSeRechazaYNoSeRegistraElCaso() throws Exception {
        mvc.perform(formulario("/api/publico/reclamaciones", datos("DNI", "1234567")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El DNI debe tener 8 dígitos."));

        assertEquals(0, reclamaciones.count());
    }

    @Test
    void sinLaDeclaracionJuradaNiElDetalleSuficienteElFormularioSeRechazaConErroresPorCampo() throws Exception {
        Map<String, Object> incompleto = new java.util.HashMap<>(datos("DNI", "12345678"));
        incompleto.put("declaracionJurada", false);
        incompleto.put("detalle", "corto");

        mvc.perform(formulario("/api/publico/reclamaciones", incompleto).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));

        assertEquals(0, reclamaciones.count());
    }

    @Test
    void unTipoDeReclamoInvalidoSeRechaza() throws Exception {
        Map<String, Object> invalido = new java.util.HashMap<>(datos("DNI", "12345678"));
        invalido.put("tipo", "SUGERENCIA");

        mvc.perform(formulario("/api/publico/reclamaciones", invalido).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    @Test
    void siFallaElCorreoElReclamoIgualQuedaRegistradoYSeMarcaLaConstanciaConError() throws Exception {
        doThrow(new MailDeliveryException()).when(correo).sendHtml(any());

        mvc.perform(formulario("/api/publico/reclamaciones", datos("DNI", "12345678")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constanciaEnviada").value(false));

        assertEquals(1, reclamaciones.count());
        assertEquals("ERROR", reclamaciones.findAll().getFirst().getEstadoEnvioConstancia());
    }

    @Test
    void soloUnAdministradorVeLaBandejaDeReclamos() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/admin/reclamaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reclamaciones").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorVeElReclamoPendienteYLoResponde() throws Exception {
        mvc.perform(formulario("/api/publico/reclamaciones", datos("DNI", "12345678")).with(csrf()))
                .andExpect(status().isOk());
        Reclamacion pendiente = reclamaciones.findAll().getFirst();

        mvc.perform(get("/api/admin/reclamaciones").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].codigo").value(pendiente.getCodigo()))
                .andExpect(jsonPath("$.items[0].estado").value("PENDIENTE_RESPUESTA"))
                .andExpect(jsonPath("$.items[0].estadoRespuesta").value("SIN_RESPUESTA"));

        // Una respuesta demasiado corta no se acepta.
        mvc.perform(post("/api/admin/reclamaciones/" + pendiente.getId() + "/responder")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"Gracias\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));

        mvc.perform(post("/api/admin/reclamaciones/" + pendiente.getId() + "/responder")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Activamos su matrícula y verificamos el pago con Culqi.\"}"))
                .andExpect(status().isOk());

        Reclamacion respondida = reclamaciones.findById(pendiente.getId()).orElseThrow();
        assertEquals(Reclamacion.RESPONDIDO, respondida.getEstado());
        assertTrue(respondida.getRespondidoEn() != null);

        // Una vez registrada, la respuesta no se vuelve a registrar.
        mvc.perform(post("/api/admin/reclamaciones/" + pendiente.getId() + "/responder")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Otra respuesta distinta para el mismo caso.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("YA_RESPONDIDO"));
    }
}
