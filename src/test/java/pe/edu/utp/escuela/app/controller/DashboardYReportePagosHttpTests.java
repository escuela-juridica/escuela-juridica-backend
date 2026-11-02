package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.repository.PagoRepository;

/** EP06 · HU-041 / HU-044 / HU-046 — Dashboard administrativo y reporte de pagos: acceso solo para administración,
 * totales que solo suman pagos confirmados, filtros validados y exportación a Excel. */
class DashboardYReportePagosHttpTests extends HttpIntegracionBase {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired PagoRepository pagos;

    private Pago pago(Matricula matricula, String estado, String importe, String medio, String pedido) {
        Pago pago = new Pago();
        pago.setMatricula(matricula);
        pago.setNumeroPedido(pedido);
        pago.setOrigen("EN_LINEA");
        pago.setEstado(estado);
        pago.setMedio(medio);
        pago.setPrecioRegularAplicado(new BigDecimal(importe));
        pago.setImporte(new BigDecimal(importe));
        pago.setIniciadoEn(Instant.now());
        pago.setResultadoEn(Instant.now());
        return pagos.saveAndFlush(pago);
    }

    /** Un curso con una venta aprobada de S/ 320 y otro alumno cuyo pago fue rechazado. */
    private Curso ventaAprobadaYRechazada() {
        Curso curso = curso("PUBLICADO", "VIRTUAL", "320.00");
        Usuario comprador = alumno("Ana");
        Matricula activa = matricula(comprador, curso, "ACTIVA");
        activa.setFechaActivacion(Instant.now());
        matriculas.saveAndFlush(activa);
        pago(activa, Pago.APROBADO, "320.00", "TARJETA", "PED-T-000001");
        Usuario fallido = alumno("Luis");
        pago(matricula(fallido, curso, "PENDIENTE_PAGO"), Pago.RECHAZADO, "320.00", "TARJETA", "PED-T-000002");
        return curso;
    }

    private String mesActualDesde() {
        return LocalDate.now(LIMA).withDayOfMonth(1).toString();
    }

    private String mesActualHasta() {
        LocalDate hoy = LocalDate.now(LIMA);
        return hoy.withDayOfMonth(hoy.lengthOfMonth()).toString();
    }

    // ------------------------------------------------------------------------------------ dashboard --

    @Test
    void elDashboardSoloEsParaAdministracion() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elDashboardContabilizaMatriculasPorCursoFormaDeIngresoYPagosConfirmados() throws Exception {
        Curso curso = ventaAprobadaYRechazada();

        mvc.perform(get("/api/admin/dashboard")
                        .param("desde", mesActualDesde()).param("hasta", mesActualHasta())
                        .with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularidad").value("DIA"))
                .andExpect(jsonPath("$.matriculasPorCurso.length()").value(1))
                .andExpect(jsonPath("$.matriculasPorCurso[0].clave").value(String.valueOf(curso.getId())))
                .andExpect(jsonPath("$.matriculasPorCurso[0].valor").value(1))
                .andExpect(jsonPath("$.formaIngreso[?(@.clave=='PAGO_EN_LINEA')].valor").value(1))
                // Solo el pago aprobado suma al gráfico de ingresos; el rechazado no.
                .andExpect(jsonPath("$.pagosPorPeriodo[?(@.monto > 0)].monto")
                        .value(org.hamcrest.Matchers.contains(320.0)));
    }

    @Test
    void unRangoMayorAUnMesAgrupaLaSeriePorMes() throws Exception {
        mvc.perform(get("/api/admin/dashboard").param("desde", "2026-01-01").param("hasta", "2026-12-31")
                        .with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularidad").value("MES"))
                .andExpect(jsonPath("$.matriculasPorPeriodo.length()").value(12));
    }

    @Test
    void elDashboardRechazaUnRangoInvertidoYUnaModalidadInvalida() throws Exception {
        mvc.perform(get("/api/admin/dashboard").param("desde", "2026-06-30").param("hasta", "2026-06-01")
                        .with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha final no puede ser anterior a la fecha inicial."));

        mvc.perform(get("/api/admin/dashboard").param("modalidad", "PRESENCIAL").with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La modalidad no es válida."));
    }

    // ------------------------------------------------------------------------------ reporte de pagos --

    @Test
    void soloAdministracionConsultaElReporteDePagos() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/admin/reportes/pagos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reportes/pagos").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void elReporteListaLosPagosYFiltraPorEstado() throws Exception {
        ventaAprobadaYRechazada();

        mvc.perform(get("/api/admin/reportes/pagos").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/admin/reportes/pagos").param("estado", "rechazado").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.items[0].suma").value(false))
                .andExpect(jsonPath("$.items[0].numeroPedido").value("PED-T-000002"));
    }

    @Test
    void elTotalSoloSumaLosPagosConfirmados() throws Exception {
        ventaAprobadaYRechazada();

        mvc.perform(get("/api/admin/reportes/pagos/total").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConfirmado").value(320.0))
                .andExpect(jsonPath("$.cantidadConfirmados").value(1))
                .andExpect(jsonPath("$.moneda").value("PEN"));
    }

    @Test
    void elReporteRechazaFiltrosInvalidos() throws Exception {
        mvc.perform(get("/api/admin/reportes/pagos").param("estado", "INVENTADO").with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El resultado del pago no es válido."));
        mvc.perform(get("/api/admin/reportes/pagos").param("origen", "TRUEQUE").with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El origen del pago no es válido."));
        mvc.perform(get("/api/admin/reportes/pagos")
                        .param("fechaDesde", "2026-06-30").param("fechaHasta", "2026-06-01")
                        .with(comoAdministrador(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elReporteSeExportaAExcelConLosMismosFiltros() throws Exception {
        ventaAprobadaYRechazada();

        byte[] excel = mvc.perform(get("/api/admin/reportes/pagos/exportar-excel").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-pagos.xlsx\""))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        // Un .xlsx es un ZIP: empieza con la firma "PK".
        assertTrue(excel.length > 100 && excel[0] == 'P' && excel[1] == 'K');
    }

    @Test
    void losMediosDePagoRegistradosAlimentanElFiltro() throws Exception {
        ventaAprobadaYRechazada();

        mvc.perform(get("/api/admin/reportes/pagos/medios").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("tarjeta"));
    }
}
