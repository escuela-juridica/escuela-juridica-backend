package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.CheckoutCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.ResultadoCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.SolicitudCobro;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.repository.EventoPagoRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository;

/** EP06 · HU-047 — Matrícula pagada con Culqi por HTTP: inicio del pago, token, resultados aprobado, rechazado,
 * error y pendiente, reintento, consulta de órdenes y webhook. Culqi se reemplaza por un doble: no hay red. */
class PagoHttpTests extends HttpIntegracionBase {

    private static final String USUARIO_WEBHOOK = "culqi-webhook";
    private static final String CLAVE_WEBHOOK = "clave-de-prueba";

    @Autowired PagoRepository pagos;
    @Autowired EventoPagoRepository eventosPago;
    @Autowired CulqiWebhookController webhook;

    @BeforeEach
    void prepararCulqi() {
        when(culqi.llavePublica()).thenReturn("pk_test_demo");
        when(culqi.prepararCheckout(any())).thenAnswer(invocacion -> {
            SolicitudCobro s = invocacion.getArgument(0);
            return new CheckoutCulqi("pk_test_demo", "ord_" + s.numeroPedido(), s.montoCentimos(), "PEN", s.descripcion());
        });
        ReflectionTestUtils.setField(webhook, "usuario", USUARIO_WEBHOOK);
        ReflectionTestUtils.setField(webhook, "clave", CLAVE_WEBHOOK);
    }

    @AfterEach
    void restaurarWebhook() {
        ReflectionTestUtils.setField(webhook, "usuario", "");
        ReflectionTestUtils.setField(webhook, "clave", "");
    }

    private static ResultadoCulqi resultado(String estado, String codigo, String mensaje, String numeroPedido) {
        return new ResultadoCulqi(estado, "chr_" + numeroPedido, "ord_" + numeroPedido, "TARJETA", "1111", 1, codigo,
                mensaje, numeroPedido, Instant.parse("2026-10-01T15:00:00Z"), null);
    }

    private Curso cursoPagado() {
        return curso("PUBLICADO", "VIRTUAL", "320.00");
    }

    private String iniciarPago(Usuario alumno, Curso curso) throws Exception {
        MvcResult iniciado = mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320.00}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(iniciado.getResponse().getContentAsString()).get("numeroPedido").asText();
    }

    private void enviarToken(Usuario alumno, String pedido) throws Exception {
        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isOk());
    }

    private Matricula matriculaDe(Usuario alumno, Curso curso) {
        return matriculas.findByUsuario_IdAndCurso_Id(alumno.getId(), curso.getId()).orElseThrow();
    }

    private static String autorizacionWebhook(String usuario, String clave) {
        return "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------------------------ iniciar --

    @Test
    void sinSesionNoSeIniciaUnPago() throws Exception {
        mvc.perform(post("/api/app/cursos/1/pagos/iniciar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void iniciarPagoCreaUnIntentoPendienteYUnaMatriculaPendienteDePagoSinAcceso() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value(org.hamcrest.Matchers.matchesPattern("PED-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.importe").value(320.0))
                .andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.reanudado").value(false))
                .andExpect(jsonPath("$.llavePublica").value("pk_test_demo"))
                .andExpect(jsonPath("$.montoCentimos").value(32000));

        ArgumentCaptor<SolicitudCobro> solicitud = ArgumentCaptor.forClass(SolicitudCobro.class);
        verify(culqi).prepararCheckout(solicitud.capture());
        assertEquals(32000L, solicitud.getValue().montoCentimos());
        assertEquals(alumno.getCorreo(), solicitud.getValue().correo());
        assertEquals(1, pagos.count());
        assertEquals(Pago.PENDIENTE, pagos.findAll().getFirst().getEstado());
        assertEquals("PENDIENTE_PAGO", matriculaDe(alumno, curso).getEstado());
    }

    @Test
    void siElAlumnoConfirmaUnImporteDistintoDelVigenteElPagoNoSeInicia() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":250.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        assertEquals(0, pagos.count());
    }

    @Test
    void sinImporteConfirmadoLaPeticionSeRechazaPorValidacion() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    @Test
    void unCursoGratuitoNoRequierePago() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso gratuito = curso("PUBLICADO", "VIRTUAL", "0.00");

        mvc.perform(post("/api/app/cursos/" + gratuito.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este curso es gratuito: no requiere pago."));
    }

    @Test
    void unCursoCanceladoYaNoAdmitePagos() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso cancelado = curso("CANCELADO", "VIRTUAL", "320.00");

        mvc.perform(post("/api/app/cursos/" + cancelado.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este curso fue cancelado: ya no admite pagos."));
        assertEquals(0, pagos.count());
    }

    @Test
    void conUnIntentoPendienteIniciarDeNuevoLoReanudaSinCrearOtro() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String primero = iniciarPago(alumno, curso);

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reanudado").value(true))
                .andExpect(jsonPath("$.numeroPedido").value(primero));
        assertEquals(1, pagos.count());
    }

    @Test
    void unaMatriculaYaActivaNoPuedePagarOtraVez() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        matricula(alumno, curso, "ACTIVA");

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/pagos/iniciar")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importeConfirmado\":320}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya tienes una matrícula en este curso."));
        assertEquals(0, pagos.count());
    }

    // ---------------------------------------------------------------------------------- resultados --

    @Test
    void unPagoAprobadoActivaLaMatriculaEmiteConstanciaYEnviaElCorreo() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.cobrar(any())).thenReturn(resultado("APROBADO", "venta_exitosa", null, pedido));

        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.estadoMatricula").value("ACTIVA"))
                .andExpect(jsonPath("$.accesoConcedido").value(true))
                .andExpect(jsonPath("$.puedeReintentar").value(false))
                .andExpect(jsonPath("$.medio").value("Tarjeta terminada en 1111"))
                .andExpect(jsonPath("$.constanciaNumero").value(org.hamcrest.Matchers.startsWith("CONST-")))
                .andExpect(jsonPath("$.estadoCorreo").value("ENVIADO"));

        Pago guardado = pagos.findByNumeroPedido(pedido).orElseThrow();
        assertEquals(Pago.APROBADO, guardado.getEstado());
        assertNotNull(guardado.getConstanciaNumero());
        assertEquals("chr_" + pedido, guardado.getOperacionProveedor());
        assertEquals("ACTIVA", matriculaDe(alumno, curso).getEstado());
        ArgumentCaptor<HtmlMailMessage> correoEnviado = ArgumentCaptor.forClass(HtmlMailMessage.class);
        verify(correo).sendHtml(correoEnviado.capture());
        assertEquals("mail/pago-aprobado.html", correoEnviado.getValue().templatePath());

        mvc.perform(get("/api/app/pagos/" + pedido + "/constancia").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constanciaNumero").value(guardado.getConstanciaNumero()))
                .andExpect(jsonPath("$.importe").value(320.0))
                .andExpect(jsonPath("$.curso").value(curso.getTitulo()));
    }

    @Test
    void unPagoRechazadoNoActivaLaMatriculaYPermiteReintentarConUnIntentoNuevo() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.cobrar(any())).thenReturn(
                resultado("RECHAZADO", "insufficient_funds", "La tarjeta no tiene fondos suficientes", pedido));

        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.estadoMatricula").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.accesoConcedido").value(false))
                .andExpect(jsonPath("$.puedeReintentar").value(true))
                .andExpect(jsonPath("$.constanciaNumero").doesNotExist())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("insufficient_funds")));

        Pago rechazado = pagos.findByNumeroPedido(pedido).orElseThrow();
        assertNull(rechazado.getConstanciaNumero());
        assertEquals("PENDIENTE_PAGO", matriculaDe(alumno, curso).getEstado());

        mvc.perform(get("/api/app/pagos/" + pedido + "/constancia").with(comoAlumno(alumno.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "La constancia solo existe para un pago aprobado por Culqi.")));

        String reintento = iniciarPago(alumno, curso);
        assertTrue(!reintento.equals(pedido));
        assertEquals(2, pagos.count());
        mvc.perform(get("/api/app/cursos/" + curso.getId() + "/pagos").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(reintento))
                .andExpect(jsonPath("$.intentos.length()").value(2));
    }

    @Test
    void unPagoConErrorTampocoActivaLaMatricula() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.cobrar(any())).thenReturn(resultado("ERROR", "processing_error", "Error al procesar", pedido));

        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.accesoConcedido").value(false))
                .andExpect(jsonPath("$.puedeReintentar").value(true));
        assertEquals("PENDIENTE_PAGO", matriculaDe(alumno, curso).getEstado());
    }

    @Test
    void unPagoPendienteNoConcedeAccesoNiReservaCupo() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.cobrar(any())).thenReturn(resultado("PENDIENTE", null, null, pedido));

        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.accesoConcedido").value(false))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith(
                        "Culqi todavía no comunica el resultado final")));
        assertEquals("PENDIENTE_PAGO", matriculaDe(alumno, curso).getEstado());
    }

    @Test
    void repetirElTokenDeUnPagoYaResueltoNoCobraDeNuevoNiDuplicaLaConstancia() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.cobrar(any())).thenReturn(resultado("APROBADO", "venta_exitosa", null, pedido));
        enviarToken(alumno, pedido);
        String constancia = pagos.findByNumeroPedido(pedido).orElseThrow().getConstanciaNumero();

        enviarToken(alumno, pedido);

        verify(culqi, times(1)).cobrar(any());
        assertEquals(constancia, pagos.findByNumeroPedido(pedido).orElseThrow().getConstanciaNumero());
        assertEquals(1, pagos.count());
    }

    @Test
    void elPagoDeUnAlumnoNoEsVisibleParaOtro() throws Exception {
        Usuario alumno = alumno("Ana");
        Usuario otro = alumno("Otro");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);

        mvc.perform(get("/api/app/pagos/" + pedido).with(comoAlumno(otro.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El pago no existe."));
    }

    @Test
    void siElCursoSeCancelaAntesDeCobrarElTokenSeRechaza() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        curso.setEstadoCurso(estado("CANCELADO"));
        cursos.saveAndFlush(curso);

        mvc.perform(post("/api/app/pagos/" + pedido + "/token")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenId\":\"tkn_test_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este curso fue cancelado: ya no se puede completar el pago."));
        verify(culqi, times(0)).cobrar(any());
    }

    // ------------------------------------------------------------------- consulta de órdenes / webhook --

    @Test
    void consultarUnPagoPendienteReflejaLaOrdenPagadaEnCulqi() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.consultarOrden("ord_" + pedido)).thenReturn(
                new ResultadoCulqi("APROBADO", "chr_" + pedido, "ord_" + pedido, "YAPE", null, null, "venta_exitosa", null,
                        pedido, Instant.parse("2026-10-01T15:00:00Z"), null));

        mvc.perform(get("/api/app/pagos/" + pedido).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.medio").value("Yape"))
                .andExpect(jsonPath("$.estadoMatricula").value("ACTIVA"));
    }

    @Test
    void elWebhookSinCredencialesOConClaveIncorrectaSeRechaza() throws Exception {
        String evento = "{\"id\":\"evt_1\",\"type\":\"order.status.changed\",\"data\":\"{\\\"id\\\":\\\"ord_X\\\"}\"}";

        mvc.perform(post("/api/publico/culqi/webhook").contentType(MediaType.APPLICATION_JSON).content(evento))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publico/culqi/webhook").contentType(MediaType.APPLICATION_JSON).content(evento)
                        .header("Authorization", autorizacionWebhook(USUARIO_WEBHOOK, "otra-clave")))
                .andExpect(status().isUnauthorized());
        verify(culqi, times(0)).consultarOrden(any());
    }

    @Test
    void unEventoDelWebhookVerificadoContraCulqiAplicaElResultadoUnaSolaVez() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoPagado();
        String pedido = iniciarPago(alumno, curso);
        when(culqi.consultarOrden("ord_" + pedido)).thenReturn(
                new ResultadoCulqi("APROBADO", null, "ord_" + pedido, "BILLETERA", null, null, "venta_exitosa", null,
                        pedido, Instant.parse("2026-10-01T15:00:00Z"), null));
        String evento = "{\"id\":\"evt_77\",\"type\":\"order.status.changed\",\"data\":\"{\\\"id\\\":\\\"ord_" + pedido + "\\\"}\"}";

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/publico/culqi/webhook").contentType(MediaType.APPLICATION_JSON).content(evento)
                            .header("Authorization", autorizacionWebhook(USUARIO_WEBHOOK, CLAVE_WEBHOOK)))
                    .andExpect(status().isOk());
        }

        Pago guardado = pagos.findByNumeroPedido(pedido).orElseThrow();
        assertEquals(Pago.APROBADO, guardado.getEstado());
        assertEquals("ACTIVA", matriculaDe(alumno, curso).getEstado());
        assertEquals(1, eventosPago.count());
        verify(correo, times(1)).sendHtml(any());
    }

    @Test
    void unEventoDeUnaOrdenQueNoEsNuestraSeIgnora() throws Exception {
        when(culqi.consultarOrden("ord_ajena")).thenReturn(
                new ResultadoCulqi("APROBADO", null, "ord_ajena", "BILLETERA", null, null, "venta_exitosa", null, null,
                        Instant.parse("2026-10-01T15:00:00Z"), null));
        String evento = "{\"id\":\"evt_88\",\"type\":\"order.status.changed\",\"data\":\"{\\\"id\\\":\\\"ord_ajena\\\"}\"}";

        mvc.perform(post("/api/publico/culqi/webhook").contentType(MediaType.APPLICATION_JSON).content(evento)
                        .header("Authorization", autorizacionWebhook(USUARIO_WEBHOOK, CLAVE_WEBHOOK)))
                .andExpect(status().isOk());

        assertEquals(0, pagos.count());
        assertEquals(0, eventosPago.count());
        assertEquals(0, matriculas.count());
    }
}
