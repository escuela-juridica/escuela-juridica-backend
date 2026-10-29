package pe.edu.utp.escuela.app.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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
import pe.edu.utp.escuela.app.dto.ReportePagoResponse;

/** HU-042 — Exportación a Excel del reporte de pagos. Escribe exactamente las filas que recibe (las mismas de la
 * pantalla con los mismos filtros) y cierra con el total de pagos confirmados. Nunca incluye tokens, llaves ni
 * datos de tarjeta. */
@Component
public class ReportePagoExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(LIMA);

    private static final String[] ENCABEZADOS = {
            "Fecha", "Alumno", "Curso", "Monto (S/)", "Medio", "Resultado", "Referencia", "N.º de pedido", "Origen",
            "Motivo", "Suma al total"
    };

    public byte[] excel(List<ReportePagoResponse> filas) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Pagos");

            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(fuente);

            CellStyle estiloMonto = libro.createCellStyle();
            estiloMonto.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            CellStyle estiloTotal = libro.createCellStyle();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            estiloTotal.setFont(negrita);
            estiloTotal.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = encabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            int numeroFila = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (ReportePagoResponse f : filas) {
                Row fila = hoja.createRow(numeroFila++);
                fila.createCell(0).setCellValue(fecha(f.fecha()));
                fila.createCell(1).setCellValue(f.alumno());
                fila.createCell(2).setCellValue(f.curso());
                Cell monto = fila.createCell(3);
                monto.setCellValue(f.monto().doubleValue());
                monto.setCellStyle(estiloMonto);
                fila.createCell(4).setCellValue(medio(f.medio()));
                fila.createCell(5).setCellValue(estado(f.estado()));
                fila.createCell(6).setCellValue(f.referencia() == null ? "—" : f.referencia());
                fila.createCell(7).setCellValue(f.numeroPedido());
                fila.createCell(8).setCellValue(origen(f.origen()));
                fila.createCell(9).setCellValue(f.motivo() == null ? "" : f.motivo());
                fila.createCell(10).setCellValue(f.suma() ? "Sí" : "No");
                if (f.suma()) {
                    total = total.add(f.monto());
                }
            }

            Row pie = hoja.createRow(numeroFila + 1);
            Cell etiqueta = pie.createCell(2);
            etiqueta.setCellValue("Total de pagos confirmados (APROBADO y REGISTRADO_MANUAL)");
            etiqueta.setCellStyle(estiloTotal);
            Cell suma = pie.createCell(3);
            suma.setCellValue(total.doubleValue());
            suma.setCellStyle(estiloTotal);

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

    private static String medio(String medio) {
        return switch (medio == null ? "" : medio.toUpperCase()) {
            case "" -> "—";
            case "TARJETA" -> "Tarjeta";
            case "YAPE" -> "Yape";
            case "BILLETERA" -> "Billetera móvil";
            case "TRANSFERENCIA" -> "Transferencia";
            default -> medio;
        };
    }

    private static String estado(String estado) {
        return switch (estado) {
            case "PENDIENTE" -> "Pendiente";
            case "APROBADO" -> "Aprobado";
            case "RECHAZADO" -> "Rechazado";
            case "ERROR" -> "Error";
            case "EXPIRADO" -> "Expirado";
            case "REGISTRADO_MANUAL" -> "Registrado manual";
            case "EXONERADO" -> "Exonerado";
            default -> estado;
        };
    }

    private static String origen(String origen) {
        return switch (origen) {
            case "EN_LINEA" -> "En línea (Culqi)";
            case "MANUAL" -> "Administrativo · pago manual";
            case "EXONERADO" -> "Administrativo · exoneración";
            default -> origen;
        };
    }
}
