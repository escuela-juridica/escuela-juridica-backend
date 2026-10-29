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
import pe.edu.utp.escuela.app.dto.ReporteCertificadoResponse;

/** HU-044 — Exportación a Excel del reporte de certificados. Escribe exactamente las filas que recibe
 * (las mismas de la pantalla con los mismos filtros) y nunca incluye documento de identidad, nota,
 * firmas ni el PDF. */
@Component
public class ReporteCertificadoExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(LIMA);

    private static final String[] ENCABEZADOS = {
            "Alumno", "Curso", "Nivel", "Entidad certificadora", "Fecha de finalización", "Fecha de emisión",
            "Estado", "Código", "Forma de emisión"
    };

    public byte[] excel(List<ReporteCertificadoResponse> filas) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Certificados");

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
            for (ReporteCertificadoResponse f : filas) {
                Row fila = hoja.createRow(numeroFila++);
                String[] valores = {
                        f.alumno(),
                        f.curso(),
                        "REFRENDADO".equals(f.nivel()) ? "Refrendado" : "Normal",
                        f.entidad() == null ? "Escuela Jurídica" : f.entidad(),
                        fecha(f.fechaFinalizacion()),
                        fecha(f.fechaEmision()),
                        "ANULADO".equals(f.estado()) ? "Anulado" : "Vigente",
                        f.codigo(),
                        forma(f.forma())
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

    private static String fecha(Instant instante) {
        return instante == null ? "—" : FECHA.format(instante);
    }

    private static String forma(String forma) {
        return switch (forma == null ? "" : forma) {
            case "AUTOMATICA" -> "Automática programada";
            case "SOLICITADA" -> "Confirmada por el alumno";
            case "MANUAL" -> "Manual administrativa";
            default -> forma == null ? "" : forma;
        };
    }
}
