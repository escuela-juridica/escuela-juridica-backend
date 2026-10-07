package pe.edu.utp.escuela.app.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;

/** HU-041 — Consultar el reporte de matrículas: formatos de exportación (Escenario 3) y
 * consistencia entre lo filtrado y lo exportado (Escenario 2). */
class ReporteMatriculaExportadorTests {

    private final ReporteMatriculaExportador exportador = new ReporteMatriculaExportador();

    private List<ReporteMatriculaRespuesta> filasDeEjemplo() {
        return List.of(
                new ReporteMatriculaRespuesta(1L, "Ana Pérez", "ana@example.com", "Derecho Registral", "VIRTUAL",
                        Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                        "ACTIVA", "GRATUITA", "EN_CURSO"),
                new ReporteMatriculaRespuesta(2L, "Luis Gómez", "luis@example.com", "Derecho Notarial", "HIBRIDO",
                        Instant.parse("2026-09-02T00:00:00Z"), null,
                        "CANCELADA", "ADMINISTRADOR", "EN_CURSO"));
    }

    @Test
    void csvIncluyeElEncabezadoYUnaFilaPorMatricula() {
        byte[] csv = exportador.csv(filasDeEjemplo());
        String contenido = new String(csv, StandardCharsets.UTF_8);
        String[] lineas = contenido.strip().split("\n");

        assertTrue(lineas[0].contains("Alumno"));
        // encabezado + 2 filas de datos.
        assertEquals(3, lineas.length);
        assertTrue(contenido.contains("Ana Pérez"));
        assertTrue(contenido.contains("Derecho Notarial"));
    }

    @Test
    void csvUsaEtiquetasLegiblesEnLugarDeLosCodigosCrudos() {
        byte[] csv = exportador.csv(filasDeEjemplo());
        String contenido = new String(csv, StandardCharsets.UTF_8);

        assertTrue(contenido.contains("Gratuita"));
        assertTrue(contenido.contains("Asignacion manual"));
        assertTrue(contenido.contains("Activa"));
        assertTrue(contenido.contains("Cancelada"));
    }

    @Test
    void excelProduceUnLibroValido() {
        byte[] excel = exportador.excel(filasDeEjemplo(), "registral", "ACTIVA");

        assertTrue(excel.length > 0);
        // Todo .xlsx es, por formato, un zip: empieza con la firma "PK".
        assertEquals('P', (char) excel[0]);
        assertEquals('K', (char) excel[1]);
    }

    @Test
    void pdfProduceUnDocumentoValido() {
        byte[] pdf = exportador.pdf(filasDeEjemplo(), "registral", "ACTIVA");

        assertTrue(pdf.length > 0);
        String cabecera = new String(pdf, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", cabecera);
    }

    @Test
    void laExportacionConservaElMismoNumeroDeFilasQueElFiltroVisible() {
        // HU-041 Escenario 2 (exportación consistente): el archivo refleja el mismo subconjunto
        // que ya filtró MatriculaServicio.reporte(texto, estado), sin perder ni agregar filas.
        List<ReporteMatriculaRespuesta> filtradas = filasDeEjemplo();

        byte[] csv = exportador.csv(filtradas);
        String[] lineas = new String(csv, StandardCharsets.UTF_8).strip().split("\n");
        assertEquals(filtradas.size(), lineas.length - 1);

        byte[] excel = exportador.excel(filtradas, "", "");
        byte[] pdf = exportador.pdf(filtradas, "", "");
        assertTrue(excel.length > 0);
        assertTrue(pdf.length > 0);
    }

    @Test
    void csvConListaVaciaSoloTieneElEncabezado() {
        byte[] csv = exportador.csv(List.of());
        String[] lineas = new String(csv, StandardCharsets.UTF_8).strip().split("\n");

        assertEquals(1, lineas.length);
    }
}
