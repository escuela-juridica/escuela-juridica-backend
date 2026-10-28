package pe.edu.utp.escuela.app.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.dto.CondicionAvanceRespuesta;
import pe.edu.utp.escuela.app.dto.ReporteAcademicoRespuesta;

/** HU-043 — Exportación a Excel del reporte académico. Escribe exactamente las filas que recibe
 * (las mismas de la pantalla con los mismos filtros) y deja visibles el valor real y la meta de
 * cada condición activa; lo que el curso no exige se rotula, nunca se deja como pendiente. */
@Component
public class ReporteAcademicoExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(LIMA);

    private static final String[] ENCABEZADOS = {
            "Curso", "Modalidad", "Alumno", "Correo", "Fecha de matrícula", "Progreso", "Exámenes calificados",
            "Nota final", "Asistencia", "Respuestas por revisar", "Fecha límite de revisión",
            "Condiciones pendientes", "Situación", "Cumplimiento", "Fecha de finalización", "Certificado", "Código"
    };

    public byte[] excel(List<ReporteAcademicoRespuesta> filas) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Reporte académico");

            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(fuente);

            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = encabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            int numeroFila = 1;
            for (ReporteAcademicoRespuesta f : filas) {
                Row fila = hoja.createRow(numeroFila++);
                String[] valores = {
                        f.curso(),
                        etiquetaModalidad(f.modalidad()),
                        f.alumno(),
                        f.correo(),
                        fecha(f.fechaMatricula()),
                        progreso(f),
                        examenes(f),
                        nota(f),
                        asistencia(f),
                        String.valueOf(f.respuestasPendientes()),
                        fecha(f.fechaLimiteRevision()),
                        f.condicionesPendientes().isEmpty() ? "—" : String.join("; ", f.condicionesPendientes()),
                        etiquetaSituacion(f.situacion()),
                        "CUMPLE".equals(f.cumplimiento()) ? "Cumple" : "No cumple",
                        fecha(f.fechaFinalizacion()),
                        etiquetaCertificado(f.certificado()),
                        f.codigoCertificado() == null ? "" : f.codigoCertificado()
                };
                for (int i = 0; i < valores.length; i++) {
                    fila.createCell(i).setCellValue(valores[i]);
                }
            }

            for (int i = 0; i < ENCABEZADOS.length; i++) {
                hoja.autoSizeColumn(i);
            }
            hoja.createFreezePane(0, 1);
            hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(numeroFila - 1, 0), 0,
                    ENCABEZADOS.length - 1));

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException excepcion) {
            throw new IllegalStateException("No pudimos generar el archivo Excel.", excepcion);
        }
    }

    private static CondicionAvanceRespuesta condicion(ReporteAcademicoRespuesta f, String codigo) {
        return f.condiciones().stream().filter(c -> codigo.equals(c.codigo())).findFirst().orElse(null);
    }

    /** El progreso se muestra siempre; si no es requisito del curso se rotula así. */
    private static String progreso(ReporteAcademicoRespuesta f) {
        CondicionAvanceRespuesta c = condicion(f, "PROGRESO");
        return c != null ? c.valorActual() + " (" + c.meta() + ")" : f.progreso() + " % (no es requisito)";
    }

    private static String examenes(ReporteAcademicoRespuesta f) {
        CondicionAvanceRespuesta c = condicion(f, "EXAMENES");
        return c != null ? c.valorActual() + " (" + c.meta() + ")" : "No exigidos";
    }

    private static String nota(ReporteAcademicoRespuesta f) {
        CondicionAvanceRespuesta c = condicion(f, "NOTA");
        return c != null ? c.valorActual() + " (" + c.meta() + ")" : "No aplica";
    }

    private static String asistencia(ReporteAcademicoRespuesta f) {
        CondicionAvanceRespuesta c = condicion(f, "ASISTENCIA");
        return c != null ? c.valorActual() + " (" + c.meta() + ")" : "No aplica";
    }

    private static String fecha(Instant instante) {
        return instante == null ? "—" : FECHA.format(instante);
    }

    private static String etiquetaModalidad(String modalidad) {
        return switch (modalidad == null ? "" : modalidad) {
            case "VIRTUAL" -> "Virtual";
            case "EN_VIVO" -> "En vivo";
            case "HIBRIDO" -> "Híbrido";
            default -> modalidad == null ? "" : modalidad;
        };
    }

    private static String etiquetaSituacion(String situacion) {
        return switch (situacion == null ? "" : situacion) {
            case "EN_PROGRESO" -> "En progreso";
            case "EN_REVISION" -> "En revisión";
            case "FINALIZADO" -> "Finalizado";
            case "CANCELADA" -> "Matrícula cancelada";
            default -> situacion == null ? "" : situacion;
        };
    }

    private static String etiquetaCertificado(String certificado) {
        return switch (certificado == null ? "" : certificado) {
            case "EMITIDO" -> "Emitido";
            case "ANULADO" -> "Anulado";
            default -> "Sin emitir";
        };
    }
}
