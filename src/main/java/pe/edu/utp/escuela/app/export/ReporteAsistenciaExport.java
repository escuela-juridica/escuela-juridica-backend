package pe.edu.utp.escuela.app.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import pe.edu.utp.escuela.app.dto.ReporteAsistenciaRespuesta;

/** HU-045 — Exportación a Excel del reporte de asistencia. Escribe exactamente las filas que
 * recibe: el servicio le pasa las mismas que ve la pantalla con los mismos filtros. */
@Component
public class ReporteAsistenciaExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(LIMA);

    private static final String[] ENCABEZADOS = {
            "Curso", "Modalidad", "Módulo", "Sesión", "Fecha de la sesión", "Alumno", "Correo",
            "Asistencia", "Origen", "Motivo de la corrección", "Porcentaje acumulado", "Cumplimiento del mínimo"
    };

    public byte[] excel(List<ReporteAsistenciaRespuesta> filas) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Reporte de asistencia");

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
            for (ReporteAsistenciaRespuesta f : filas) {
                Row fila = hoja.createRow(numeroFila++);
                String[] valores = {
                        f.curso(),
                        etiquetaModalidad(f.modalidad()),
                        f.modulo(),
                        f.sesion(),
                        f.fechaSesion() == null ? "" : FECHA_HORA.format(f.fechaSesion()),
                        f.alumno(),
                        f.correo(),
                        etiquetaAsistencia(f.asistencia()),
                        etiquetaOrigen(f.origen()),
                        f.motivoCorreccion() == null ? "" : f.motivoCorreccion(),
                        f.porcentajeAcumulado() == null ? "No aplica" : f.porcentajeAcumulado() + " %",
                        etiquetaCumplimiento(f.cumplimiento())
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

    private static String etiquetaModalidad(String modalidad) {
        return "EN_VIVO".equals(modalidad) ? "En vivo" : "HIBRIDO".equals(modalidad) ? "Híbrido" : modalidad;
    }

    private static String etiquetaAsistencia(String asistencia) {
        return switch (asistencia == null ? "" : asistencia) {
            case "PRESENTE" -> "Presente";
            case "AUSENTE" -> "Ausente";
            case "JUSTIFICADA" -> "Justificada";
            case "PENDIENTE" -> "Pendiente";
            default -> asistencia == null ? "" : asistencia;
        };
    }

    private static String etiquetaOrigen(String origen) {
        return switch (origen == null ? "" : origen) {
            case "AUTOMATICA" -> "Automática";
            case "CORRECCION_ADMINISTRATIVA" -> "Corrección administrativa";
            default -> "—";
        };
    }

    private static String etiquetaCumplimiento(String cumplimiento) {
        return switch (cumplimiento == null ? "" : cumplimiento) {
            case "CUMPLE" -> "Cumple";
            case "NO_CUMPLE" -> "No cumple";
            default -> "No es requisito";
        };
    }
}
