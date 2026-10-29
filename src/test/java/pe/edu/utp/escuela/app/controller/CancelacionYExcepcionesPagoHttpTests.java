package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.edu.utp.escuela.app.entity.AtencionMatriculaPago;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.CheckoutCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.ResultadoCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.SolicitudCobro;
import pe.edu.utp.escuela.app.repository.AtencionMatriculaPagoRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository;

/** EP06 · HU-037 / HU-038 — Excepciones de matrícula y pago, y cancelación de un curso: avisos a los alumnos con
 * acceso, sesiones futuras canceladas, lectura conservada y pagos tardíos que quedan para atención. */
class CancelacionYExcepcionesPagoHttpTests extends HttpIntegracionBase {

    private static final String MOTIVO = "No se alcanzó el mínimo de alumnos para dictar el curso.";

    @Autowired PagoRepository pagos;
    @Autowired AtencionMatriculaPagoRepository atenciones;

    @BeforeEach
    void prepararCulqi() {
        // El catálogo real trae el estado CANCELADO; en H2 vacío hay que crearlo para poder cancelar cursos.
        estado("CANCELADO");
        when(culqi.llavePublica()).thenReturn("pk_test_demo");
        when(culqi.prepararCheckout(any())).thenAnswer(invocacion -> {
            SolicitudCobro s = invocacion.getArgument(0);
            return new CheckoutCulqi("pk_test_demo", "ord_" + s.numeroPedido(), s.montoCentimos(), "PEN", s.descripcion());
        });
    }

    private String cuerpoCancelacion(String motivo, boolean confirmo) {
        return "{\"motivo\":\"" + motivo + "\",\"confirmo\":" + confirmo + "}";
    }

    private void cancelarCurso(Curso curso) throws Exception {
        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(MOTIVO, true)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------- cancelar un curso --

    @Test
    void soloUnAdministradorConsultaElImpactoDeCancelarUnCurso() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");

        mvc.perform(get("/api/admin/cursos/" + curso.getId() + "/cancelacion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/cursos/" + curso.getId() + "/cancelacion").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elImpactoCuentaLosAlumnosConAccesoLosPendientesDePagoYLasSesionesFuturas() throws Exception {
        Usuario conAcceso = alumno("Ana");
        Usuario pendiente = alumno("Luis");
        Curso curso = curso("PUBLICADO", "EN_VIVO", "320.00");
        matricula(conAcceso, curso, "ACTIVA");
        matricula(pendiente, curso, "PENDIENTE_PAGO");
        Leccion sesion = leccion(modulo(curso, 1), 1, "EN_VIVO");
        sesion.setFechaHoraInicio(Instant.now().plusSeconds(86_400));
        sesion.setFechaHoraFin(Instant.now().plusSeconds(93_600));
        lecciones.saveAndFlush(sesion);

        mvc.perform(get("/api/admin/cursos/" + curso.getId() + "/cancelacion").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeCancelar").value(true))
                .andExpect(jsonPath("$.alumnosConAcceso").value(1))
                .andExpect(jsonPath("$.matriculasPendientePago").value(1))
                .andExpect(jsonPath("$.sesionesFuturas").value(1));
    }

    @Test
    void cancelarUnCursoLoMarcaCanceladoAvisaAlAlumnoYCancelaSusSesionesFuturas() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("PUBLICADO", "EN_VIVO", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Leccion sesion = leccion(modulo(curso, 1), 1, "EN_VIVO");
        sesion.setFechaHoraInicio(Instant.now().plusSeconds(86_400));
        sesion.setFechaHoraFin(Instant.now().plusSeconds(93_600));
        lecciones.saveAndFlush(sesion);

        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(MOTIVO, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelacion").value(MOTIVO))
                .andExpect(jsonPath("$.avisosTotal").value(1))
                .andExpect(jsonPath("$.avisosEnviados").value(1))
                .andExpect(jsonPath("$.avisosConError").value(0));

        assertEquals("CANCELADO", cursos.findById(curso.getId()).orElseThrow().getEstadoCurso().getCodigo());
        assertEquals("CANCELADA", lecciones.findById(sesion.getId()).orElseThrow().getEstado());
        verify(correo, times(1)).sendHtml(any());

        // El alumno que ya tenía acceso conserva la consulta en modo lectura.
        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursoCancelado").value(true))
                .andExpect(jsonPath("$.motivoCancelacionCurso").value(MOTIVO));
    }

    @Test
    void cancelarSinMotivoSuficienteOSinConfirmarSeRechaza() throws Exception {
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");

        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion("corto", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        assertEquals("PUBLICADO", cursos.findById(curso.getId()).orElseThrow().getEstadoCurso().getCodigo());
    }

    @Test
    void unCursoCerradoOYaCanceladoNoPuedeCancelarse() throws Exception {
        Curso cerrado = curso("CERRADO", "VIRTUAL", "320.00");
        mvc.perform(post("/api/admin/cursos/" + cerrado.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(MOTIVO, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Un curso cerrado terminó de forma normal: ya no puede cancelarse."));

        Curso publicado = curso("PUBLICADO", "VIRTUAL", "320.00");
        cancelarCurso(publicado);
        mvc.perform(post("/api/admin/cursos/" + publicado.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(MOTIVO, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este curso ya está cancelado."));
    }

    @Test
    void siFallaElCorreoElAvisoQuedaConErrorYSePuedeReenviarSinDuplicarLosEnviados() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        doThrow(new MailDeliveryException()).when(correo).sendHtml(any());

        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/cancelar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoCancelacion(MOTIVO, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosConError").value(1))
                .andExpect(jsonPath("$.avisosEnviados").value(0));

        reset(correo);
        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/reenviar-avisos")
                        .with(comoAdministrador(1L)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosConError").value(0))
                .andExpect(jsonPath("$.avisosEnviados").value(1));
        verify(correo, times(1)).sendHtml(any());

        // Reenviar otra vez no vuelve a escribirle a quien ya recibió el aviso.
        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/reenviar-avisos")
                        .with(comoAdministrador(1L)).with(csrf()))
                .andExpect(status().isOk());
        verify(correo, times(1)).sendHtml(any());
    }

    @Test
    void reenviarAvisosDeUnCursoNoCanceladoSeRechaza() throws Exception {
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");

        mvc.perform(post("/api/admin/cursos/" + curso.getId() + "/cancelacion/reenviar-avisos")
                        .with(comoAdministrador(1L)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este curso no está cancelado: no hay avisos que reenviar."));
    }

    // ------------------------------------------------------------------------ excepciones de pago --

    @Test
    void unPagoAprobadoDespuesDeCancelarElCursoSeRegistraSinAccesoYQuedaParaAtencion() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");
        var iniciado = mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320}"))
                .andExpect(status().isOk()).andReturn();
        String pedido = json.readTree(iniciado.getResponse().getContentAsString()).get("numeroPedido").asText();
        cancelarCurso(curso);
        when(culqi.consultarOrden("ord_" + pedido)).thenReturn(
                new ResultadoCulqi("APROBADO", "chr_" + pedido, "ord_" + pedido, "BILLETERA", null, null,
                        "venta_exitosa", null, pedido, Instant.parse("2026-10-01T15:00:00Z"), null));

        // El alumno consulta su pedido: Culqi ya lo había aprobado.
        mvc.perform(get("/api/app/pagos/" + pedido).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.accesoConcedido").value(false))
                .andExpect(jsonPath("$.estadoMatricula").value("CANCELADA"));

        Matricula matricula = matriculas.findByUsuario_IdAndCurso_Id(alumno.getId(), curso.getId()).orElseThrow();
        assertEquals("CANCELADA", matricula.getEstado());
        Pago pago = pagos.findByNumeroPedido(pedido).orElseThrow();
        assertTrue(pago.isRequiereAtencion());
        assertEquals(1, atenciones.count());
        assertEquals(AtencionMatriculaPago.CURSO_CANCELADO, atenciones.findAll().getFirst().getTipo());
    }

    @Test
    void elAdministradorListaYAtiendeLaExcepcionUnaSolaVez() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");
        var iniciado = mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320}"))
                .andExpect(status().isOk()).andReturn();
        String pedido = json.readTree(iniciado.getResponse().getContentAsString()).get("numeroPedido").asText();
        cancelarCurso(curso);
        when(culqi.consultarOrden("ord_" + pedido)).thenReturn(
                new ResultadoCulqi("APROBADO", "chr_" + pedido, "ord_" + pedido, "BILLETERA", null, null,
                        "venta_exitosa", null, pedido, Instant.parse("2026-10-01T15:00:00Z"), null));
        mvc.perform(get("/api/app/pagos/" + pedido).with(comoAlumno(alumno.getId()))).andExpect(status().isOk());
        Long atencionId = atenciones.findAll().getFirst().getId();

        mvc.perform(get("/api/admin/atenciones-pago").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/atenciones-pago/pendientes").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").value(1));
        mvc.perform(get("/api/admin/atenciones-pago").param("estado", "PENDIENTE").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].tipo").value("CURSO_CANCELADO"))
                .andExpect(jsonPath("$.items[0].pago.numeroPedido").value(pedido));

        mvc.perform(post("/api/admin/atenciones-pago/" + atencionId + "/atender")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"corto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));

        mvc.perform(post("/api/admin/atenciones-pago/" + atencionId + "/atender")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"Se coordinó la devolución directamente con Culqi.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDO"));
        assertEquals(AtencionMatriculaPago.ATENDIDO, atenciones.findById(atencionId).orElseThrow().getEstado());
        assertEquals(false, pagos.findByNumeroPedido(pedido).orElseThrow().isRequiereAtencion());

        mvc.perform(post("/api/admin/atenciones-pago/" + atencionId + "/atender")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"Se intenta atender de nuevo el mismo caso.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("YA_ATENDIDO"));
    }

    @Test
    void elFiltroDeExcepcionesRechazaUnTipoDesconocido() throws Exception {
        mvc.perform(get("/api/admin/atenciones-pago").param("tipo", "INVENTADO").with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El tipo de excepción no es válido."));
    }

    @Test
    void atenderUnaExcepcionInexistenteRespondeNoEncontrado() throws Exception {
        mvc.perform(get("/api/admin/atenciones-pago/999999").with(comoAdministrador(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La excepción no existe."));
    }
}
