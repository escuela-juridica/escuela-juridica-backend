package pe.edu.utp.escuela.app.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;

/**
 * Genera los archivos del reporte de matriculas (HU-041) en CSV, Excel y PDF, con la misma
 * paleta de colores y etiquetas que usa la tabla del panel admin (ver badges en
 * reporte-matriculas.ts del frontend) para que el archivo descargado se vea igual de cuidado.
 */
@Component
public class ReporteMatriculaExportador {

    private static final String[] ENCABEZADOS = {
            "Alumno", "Correo", "Curso", "Modalidad", "Fecha matricula", "Fecha activacion",
            "Estado", "Origen", "Situacion academica",
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

    // --------------------------------------------------------------------------------- CSV --

    public byte[] csv(List<ReporteMatriculaRespuesta> filas) {
        StringBuilder csv = new StringBuilder(String.join(";", ENCABEZADOS)).append('\n');
        for (ReporteMatriculaRespuesta fila : filas) {
            csv.append(valorCsv(fila.alumno())).append(';').append(valorCsv(fila.correo())).append(';')
                    .append(valorCsv(fila.curso())).append(';').append(valorCsv(fila.modalidad())).append(';')
                    .append(formatoFecha(fila.fechaMatricula())).append(';').append(formatoFecha(fila.fechaActivacion())).append(';')
                    .append(valorCsv(etiquetaEstado(fila.estadoMatricula()))).append(';')
                    .append(valorCsv(etiquetaIngreso(fila.formaIngreso()))).append(';')
                    .append(valorCsv(etiquetaSituacion(fila.situacionAcademica()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String valorCsv(String valor) {
        return "\"" + (valor == null ? "" : valor.replace("\"", "\"\"")) + "\"";
    }

    // ------------------------------------------------------------------------------- Excel --

    public byte[] excel(List<ReporteMatriculaRespuesta> filas, String textoFiltro, String estadoFiltro) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Matriculas");
            Map<String, CellStyle> cacheEstilos = new HashMap<>();

            Row filaTitulo = hoja.createRow(0);
            Cell celdaTitulo = filaTitulo.createCell(0);
            celdaTitulo.setCellValue("Reporte de matrículas · Escuela Jurídica");
            celdaTitulo.setCellStyle(estiloTexto(libro, 15, true, COLOR_MARCA, null));
            hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ENCABEZADOS.length - 1));

            Row filaSubtitulo = hoja.createRow(1);
            Cell celdaSubtitulo = filaSubtitulo.createCell(0);
            celdaSubtitulo.setCellValue(subtitulo(filas.size(), textoFiltro, estadoFiltro));
            celdaSubtitulo.setCellStyle(estiloTexto(libro, 9, false, COLOR_SUTIL, null));
            hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, ENCABEZADOS.length - 1));

            int filaEncabezadoIdx = 3;
            Row filaEncabezado = hoja.createRow(filaEncabezadoIdx);
            filaEncabezado.setHeightInPoints(22f);
            CellStyle estiloEncabezado = estiloEncabezado(libro);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = filaEncabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            int numeroFila = filaEncabezadoIdx + 1;
            for (ReporteMatriculaRespuesta fila : filas) {
                boolean par = (numeroFila - filaEncabezadoIdx) % 2 == 0;
                Row filaDatos = hoja.createRow(numeroFila++);
                filaDatos.setHeightInPoints(17f);
                escribirCelda(libro, cacheEstilos, filaDatos, 0, fila.alumno(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 1, fila.correo(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 2, fila.curso(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 3, fila.modalidad(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 4, formatoFecha(fila.fechaMatricula()), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 5, formatoFecha(fila.fechaActivacion()), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 6, etiquetaEstado(fila.estadoMatricula()), par,
                        "ACTIVA".equals(fila.estadoMatricula()) ? COLOR_EXITO : COLOR_ERROR);
                escribirCelda(libro, cacheEstilos, filaDatos, 7, etiquetaIngreso(fila.formaIngreso()), par,
                        colorIngreso(fila.formaIngreso()));
                escribirCelda(libro, cacheEstilos, filaDatos, 8, etiquetaSituacion(fila.situacionAcademica()), par, null);
            }

            int[] anchos = { 7500, 8500, 9000, 4500, 6500, 6500, 4500, 6000, 6000 };
            for (int i = 0; i < anchos.length; i++) {
                hoja.setColumnWidth(i, anchos[i]);
            }
            hoja.createFreezePane(0, filaEncabezadoIdx + 1);
            hoja.setAutoFilter(new CellRangeAddress(filaEncabezadoIdx, filaEncabezadoIdx, 0, ENCABEZADOS.length - 1));

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel del reporte.", e);
        }
    }

    private void escribirCelda(XSSFWorkbook libro, Map<String, CellStyle> cache, Row fila, int indice,
            String valor, boolean par, byte[] colorFuente) {
        Cell celda = fila.createCell(indice);
        celda.setCellValue(valor == null ? "" : valor);
        celda.setCellStyle(estiloFila(libro, cache, par, colorFuente));
    }

    private CellStyle estiloEncabezado(XSSFWorkbook libro) {
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFillForegroundColor(new XSSFColor(COLOR_MARCA, null));
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setAlignment(HorizontalAlignment.LEFT);
        XSSFFont fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setColor(new XSSFColor(COLOR_BLANCO, null));
        fuente.setFontHeightInPoints((short) 10);
        estilo.setFont(fuente);
        return estilo;
    }

    private CellStyle estiloFila(XSSFWorkbook libro, Map<String, CellStyle> cache, boolean par, byte[] colorFuente) {
        String clave = par + "|" + (colorFuente == null ? "default" : new String(colorFuente));
        return cache.computeIfAbsent(clave, k -> {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.setFillForegroundColor(new XSSFColor(par ? COLOR_ZEBRA : COLOR_BLANCO, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBottomBorderColor(new XSSFColor(COLOR_BORDE, null));
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont fuente = libro.createFont();
            fuente.setFontHeightInPoints((short) 10);
            if (colorFuente != null) {
                fuente.setColor(new XSSFColor(colorFuente, null));
                fuente.setBold(true);
            }
            estilo.setFont(fuente);
            return estilo;
        });
    }

    private CellStyle estiloTexto(XSSFWorkbook libro, int tamano, boolean negrita, byte[] colorFuente, byte[] colorFondo) {
        XSSFCellStyle estilo = libro.createCellStyle();
        XSSFFont fuente = libro.createFont();
        fuente.setBold(negrita);
        fuente.setFontHeightInPoints((short) tamano);
        fuente.setColor(new XSSFColor(colorFuente, null));
        estilo.setFont(fuente);
        if (colorFondo != null) {
            estilo.setFillForegroundColor(new XSSFColor(colorFondo, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return estilo;
    }

    private byte[] colorIngreso(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> COLOR_CELESTE;
            case "ADMINISTRADOR", "EXONERADA" -> COLOR_VIOLETA;
            case "PAGO_EN_LINEA" -> COLOR_EXITO;
            default -> null;
        };
    }

    private static byte[] hexARgb(String hex) {
        return new byte[] {
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16),
        };
    }

    // --------------------------------------------------------------------------------- PDF --

    public byte[] pdf(List<ReporteMatriculaRespuesta> filas, String textoFiltro, String estadoFiltro) {
        float[] anchos = { 100, 120, 105, 62, 70, 70, 60, 80, 70 };
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
                    y = dibujarEncabezadoTabla(flujo, anchos, margen, y, altoEncabezadoTabla, anchoTotal, fuenteNegrita);
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
            String textoFiltro, String estadoFiltro, PDType1Font fuenteNegrita, PDType1Font fuenteCursiva) throws IOException {
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
                recorte(fila.modalidad(), 14), formatoFecha(fila.fechaMatricula()), formatoFecha(fila.fechaActivacion()),
                etiquetaEstado(fila.estadoMatricula()), etiquetaIngreso(fila.formaIngreso()),
                etiquetaSituacion(fila.situacionAcademica()),
        };
        Color colorEstado = "ACTIVA".equals(fila.estadoMatricula()) ? AWT_EXITO : AWT_ERROR;
        Color[] colores = { null, null, null, null, null, null, colorEstado, colorIngresoPdf(fila.formaIngreso()), null };

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
            case "ADMINISTRADOR", "EXONERADA" -> AWT_VIOLETA;
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
        if (valor == null) return "";
        return valor.length() > maximo ? valor.substring(0, maximo - 1) + "..." : valor;
    }

    // ------------------------------------------------------------------------- Compartido --

    private String formatoFecha(Instant instante) {
        if (instante == null) return "-";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima")).format(instante);
    }

    private String etiquetaEstado(String estado) {
        return "ACTIVA".equals(estado) ? "Activa" : "Cancelada";
    }

    private String etiquetaIngreso(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> "Gratuita";
            case "ADMINISTRADOR" -> "Asignacion manual";
            case "PAGO_EN_LINEA" -> "Pago en linea";
            case "EXONERADA" -> "Exonerada";
            default -> formaIngreso;
        };
    }

    private String etiquetaSituacion(String situacion) {
        return "EN_CURSO".equals(situacion) ? "En curso" : "Finalizado";
    }
}
