package pe.edu.utp.escuela.app.export;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.awt.Color;

@Component
public class ReporteMatriculaExport {
    private static final String[] ENCABEZADOS = {
            "Alumno", "Correo", "Curso", "Modalidad",
            "Fecha matrícula", "Fecha activación",
            "Estado", "Origen", "Situación académica",
            "Estado del certificado"
    };

    private static final byte[] COLOR_MARCA = hexARgb("103860");
    private static final byte[] COLOR_BLANCO = hexARgb("FFFFFF");
    private static final byte[] COLOR_ZEBRA = hexARgb("F8FAFC");
    private static final byte[] COLOR_BORDE = hexARgb("EAECF0");
    private static final byte[] COLOR_SUTIL = hexARgb("667085");
    private static final byte[] COLOR_EXITO = hexARgb("027A48");
    private static final byte[] COLOR_ERROR = hexARgb("B42318");
    private static final byte[] COLOR_CELESTE = hexARgb("026AA2");
    private static final byte[] COLOR_VIOLETA = hexARgb("6941C6");

    private static final Color AWT_MARCA = new Color(0x10, 0x38, 0x60);
    private static final Color AWT_ZEBRA = new Color(0xF8, 0xFA, 0xFC);
    private static final Color AWT_BORDE = new Color(0xEA, 0xEC, 0xF0);
    private static final Color AWT_SUTIL = new Color(0x66, 0x70, 0x85);
    private static final Color AWT_EXITO = new Color(0x02, 0x7A, 0x48);
    private static final Color AWT_ERROR = new Color(0xB4, 0x23, 0x18);
    private static final Color AWT_CELESTE = new Color(0x02, 0x6A, 0xA2);
    private static final Color AWT_VIOLETA = new Color(0x69, 0x41, 0xC6);

    private static byte[] hexARgb(String hex) {
        return new byte[] {
                (byte) 0xFF,
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    public byte[] csv(List<ReporteMatriculaRespuesta> filas) {
        StringBuilder csv = new StringBuilder(String.join(";", ENCABEZADOS)).append('\n');
        for (ReporteMatriculaRespuesta fila : filas) {
            csv.append(valorCsv(fila.alumno())).append(';').append(valorCsv(fila.correo())).append(';')
                    .append(valorCsv(fila.curso())).append(';').append(valorCsv(fila.modalidad())).append(';')
                    .append(formatoFecha(fila.fechaMatricula())).append(';')
                    .append(formatoFecha(fila.fechaActivacion())).append(';')
                    .append(valorCsv(etiquetaEstado(fila.estadoMatricula()))).append(';')
                    .append(valorCsv(etiquetaIngreso(fila.formaIngreso()))).append(';')
                    .append(valorCsv(etiquetaSituacion(fila.situacionAcademica()))).append(';')
                    .append(valorCsv(etiquetaCertificado(fila.estadoCertificado()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String valorCsv(String valor) {
        return "\"" + (valor == null ? "" : valor.replace("\"", "\"\"")) + "\"";
    }

    private String formatoFecha(Instant instante) {
        if (instante == null)
            return "-";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("America/Lima"))
                .format(instante);
    }

    private String etiquetaEstado(String estado) {
        return switch (estado) {
            case "ACTIVA" -> "Activa";
            case "CANCELADA" -> "Cancelada";
            case "VENCIDA" -> "Vencida";
            case "PENDIENTE_PAGO" -> "Pendiente de pago";
            case "FINALIZADA" -> "Finalizada";
            default -> estado == null ? "Sin estado" : estado;
        };
    }

    private String etiquetaIngreso(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> "Gratuita";
            case "ADMINISTRADOR" -> "Asignacion manual";
            case "MANUAL", "REGISTRADO_MANUAL" -> "Pago manual registrado";
            case "PAGO_EN_LINEA" -> "Pago en linea";
            case "EXONERADA", "EXONERADO" -> "Exonerada";
            default -> formaIngreso;
        };
    }

    private String etiquetaSituacion(String situacion) {
        return "EN_CURSO".equals(situacion) ? "En curso" : "Finalizado";
    }

    private String etiquetaCertificado(String estado) {
        return switch (estado == null ? "NO_EMITIDO" : estado) {
            case "VIGENTE" -> "Vigente";
            case "ANULADO" -> "Anulado";
            case "NO_EMITIDO" -> "No emitido";
            default -> estado;
        };
    }

    public byte[] excel(List<ReporteMatriculaRespuesta> filas) {
        try (Workbook libro = new XSSFWorkbook();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = libro.createSheet("Reporte de matrículas");

            // Estilo de encabezados
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFillForegroundColor(
                    IndexedColors.DARK_BLUE.getIndex());
            estiloEncabezado.setFillPattern(
                    org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            Font fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(fuente);

            // Crear encabezados
            Row encabezado = hoja.createRow(0);

            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = encabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            // Agregar las matrículas
            int numeroFila = 1;

            for (ReporteMatriculaRespuesta fila : filas) {
                Row filaExcel = hoja.createRow(numeroFila++);

                String[] valores = {
                        fila.alumno(),
                        fila.correo(),
                        fila.curso(),
                        fila.modalidad(),
                        formatoFecha(fila.fechaMatricula()),
                        formatoFecha(fila.fechaActivacion()),
                        etiquetaEstado(fila.estadoMatricula()),
                        etiquetaIngreso(fila.formaIngreso()),
                        etiquetaSituacion(fila.situacionAcademica()),
                        etiquetaCertificado(fila.estadoCertificado())
                };

                for (int i = 0; i < valores.length; i++) {
                    filaExcel.createCell(i).setCellValue(
                            valores[i] == null ? "" : valores[i]);
                }
            }

            // Ajustar el ancho de las columnas
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                hoja.autoSizeColumn(i);
            }

            libro.write(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el reporte Excel", e);
        }
    }

    public byte[] pdf(List<ReporteMatriculaRespuesta> filas, String textoFiltro, String estadoFiltro) {
        float[] anchos = { 90, 105, 105, 52, 62, 62, 55, 72, 68, 65 };
        float margen = 25f;
        float altoFila = 17f;
        float altoEncabezadoTabla = 20f;
        float anchoTotal = sumaAnchos(anchos);
        PDRectangle tamano = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        PDType1Font fuenteNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fuenteNegrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fuenteCursiva = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            int numeroPagina = 1;
            PDPage pagina = new PDPage(tamano);
            documento.addPage(pagina);
            PDPageContentStream flujo = new PDPageContentStream(documento, pagina);
            float y = tamano.getHeight() - margen;
            y = dibujarTitulo(flujo, margen, y, filas.size(), textoFiltro, estadoFiltro, fuenteNegrita, fuenteCursiva);
            y = dibujarEncabezadoTabla(flujo, anchos, margen, y, altoEncabezadoTabla, anchoTotal, fuenteNegrita);
            dibujarPie(flujo, margen, tamano.getWidth(), numeroPagina, fuenteNormal);

            int indiceFila = 0;
            for (ReporteMatriculaRespuesta fila : filas) {
                if (y - altoFila < margen) {
                    flujo.close();
                    numeroPagina++;
                    pagina = new PDPage(tamano);
                    documento.addPage(pagina);
                    flujo = new PDPageContentStream(documento, pagina);
                    y = tamano.getHeight() - margen;
                    y = dibujarEncabezadoTabla(flujo, anchos, margen, y, altoEncabezadoTabla, anchoTotal,
                            fuenteNegrita);
                    dibujarPie(flujo, margen, tamano.getWidth(), numeroPagina, fuenteNormal);
                    indiceFila = 0;
                }
                y = dibujarFila(flujo, fila, anchos, margen, y, altoFila, anchoTotal, indiceFila % 2 == 0,
                        fuenteNormal, fuenteNegrita);
                indiceFila++;
            }
            flujo.close();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el PDF del reporte.", e);
        }
    }

    private float dibujarTitulo(PDPageContentStream flujo, float margen, float y, int totalFilas,
            String textoFiltro, String estadoFiltro, PDType1Font fuenteNegrita, PDType1Font fuenteCursiva)
            throws IOException {
        flujo.setNonStrokingColor(AWT_MARCA);
        flujo.beginText();
        flujo.setFont(fuenteNegrita, 14);
        flujo.newLineAtOffset(margen, y - 12);
        flujo.showText("Reporte de matriculas - Escuela Juridica");
        flujo.endText();

        flujo.setNonStrokingColor(AWT_SUTIL);
        flujo.beginText();
        flujo.setFont(fuenteCursiva, 8);
        flujo.newLineAtOffset(margen, y - 27);
        flujo.showText(subtitulo(totalFilas, textoFiltro, estadoFiltro));
        flujo.endText();

        return y - 42;
    }

    private String subtitulo(int totalFilas, String textoFiltro, String estadoFiltro) {
        String filtroTexto = (textoFiltro == null || textoFiltro.isBlank()) ? "todos" : textoFiltro;
        String filtroEstado = (estadoFiltro == null || estadoFiltro.isBlank()) ? "todos" : estadoFiltro;
        return "Generado el " + formatoFecha(Instant.now()) + "  |  Busqueda: " + filtroTexto
                + "  |  Estado: " + filtroEstado + "  |  " + totalFilas + " matricula(s)";
    }

    private float dibujarEncabezadoTabla(PDPageContentStream flujo, float[] anchos, float margen, float y,
            float altoEncabezado, float anchoTotal, PDType1Font fuenteNegrita) throws IOException {
        flujo.setNonStrokingColor(AWT_MARCA);
        flujo.addRect(margen, y - altoEncabezado, anchoTotal, altoEncabezado);
        flujo.fill();

        flujo.setNonStrokingColor(Color.WHITE);
        float x = margen;
        for (int i = 0; i < ENCABEZADOS.length; i++) {
            flujo.beginText();
            flujo.setFont(fuenteNegrita, 9);
            flujo.newLineAtOffset(x + 4, y - altoEncabezado + 7);
            flujo.showText(ENCABEZADOS[i]);
            flujo.endText();
            x += anchos[i];
        }
        return y - altoEncabezado;
    }

    private float dibujarFila(PDPageContentStream flujo, ReporteMatriculaRespuesta fila, float[] anchos,
            float margen, float y, float altoFila, float anchoTotal, boolean par,
            PDType1Font fuenteNormal, PDType1Font fuenteNegrita) throws IOException {
        float yInferior = y - altoFila;

        flujo.setNonStrokingColor(par ? Color.WHITE : AWT_ZEBRA);
        flujo.addRect(margen, yInferior, anchoTotal, altoFila);
        flujo.fill();
        flujo.setStrokingColor(AWT_BORDE);
        flujo.setLineWidth(0.5f);
        flujo.moveTo(margen, yInferior);
        flujo.lineTo(margen + anchoTotal, yInferior);
        flujo.stroke();

        String[] valores = {
                recorte(fila.alumno(), 42), recorte(fila.correo(), 48), recorte(fila.curso(), 44),
                recorte(fila.modalidad(), 14), formatoFecha(fila.fechaMatricula()),
                formatoFecha(fila.fechaActivacion()),
                etiquetaEstado(fila.estadoMatricula()), etiquetaIngreso(fila.formaIngreso()),
                etiquetaSituacion(fila.situacionAcademica()), etiquetaCertificado(fila.estadoCertificado()),
        };
        Color colorEstado = "ACTIVA".equals(fila.estadoMatricula()) ? AWT_EXITO : AWT_ERROR;
        Color[] colores = { null, null, null, null, null, null, colorEstado,
                colorIngresoPdf(fila.formaIngreso()), null, null };

        float x = margen;
        for (int i = 0; i < valores.length; i++) {
            flujo.setNonStrokingColor(colores[i] != null ? colores[i] : Color.BLACK);
            flujo.beginText();
            flujo.setFont(colores[i] != null ? fuenteNegrita : fuenteNormal, 8);
            flujo.newLineAtOffset(x + 4, yInferior + 6);
            flujo.showText(valores[i] == null ? "" : valores[i]);
            flujo.endText();
            x += anchos[i];
        }
        return yInferior;
    }

    private void dibujarPie(PDPageContentStream flujo, float margen, float anchoPagina, int numeroPagina,
            PDType1Font fuenteNormal) throws IOException {
        flujo.setNonStrokingColor(AWT_SUTIL);
        flujo.beginText();
        flujo.setFont(fuenteNormal, 8);
        flujo.newLineAtOffset(anchoPagina - margen - 45, 14);
        flujo.showText("Pagina " + numeroPagina);
        flujo.endText();
    }

    private Color colorIngresoPdf(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> AWT_CELESTE;
            case "ADMINISTRADOR", "EXONERADA", "EXONERADO" -> AWT_VIOLETA;
            case "MANUAL", "REGISTRADO_MANUAL" -> AWT_CELESTE;
            case "PAGO_EN_LINEA" -> AWT_EXITO;
            default -> null;
        };
    }

    private float sumaAnchos(float[] anchos) {
        float total = 0;
        for (float ancho : anchos) {
            total += ancho;
        }
        return total;
    }

    private String recorte(String valor, int maximo) {
        if (valor == null)
            return "";
        return valor.length() > maximo ? valor.substring(0, maximo - 1) + "..." : valor;
    }

}