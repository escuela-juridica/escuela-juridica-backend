package pe.edu.utp.escuela.app.export;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.CertificadoFirmante;

/**
 * HU-032 — PDF del certificado: nombre, curso, horas, fechas, lugar, nota y nivel congelados, las
 * firmas configuradas y un código QR que lleva a la verificación pública. Solo imprime lo que está
 * en el {@link Certificado} ya congelado; nunca consulta datos vivos del alumno ni del curso, y no
 * incluye el documento de identidad.
 */
@Component
public class CertificadoPdfExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final Color AZUL = new Color(0x10, 0x38, 0x60);
    private static final Color GRIS = new Color(0x66, 0x70, 0x85);

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont cursiva = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public byte[] generar(Certificado certificado, List<CertificadoFirmante> firmantes, String urlVerificacion) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDRectangle tamano = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage pagina = new PDPage(tamano);
            documento.addPage(pagina);
            float ancho = tamano.getWidth();
            float alto = tamano.getHeight();

            try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                marco(c, ancho, alto);

                logo(documento, c, ancho, alto);
                texto(c, negrita, 26, AZUL, "ESCUELA JURÍDICA", ancho, alto - 110);
                String subtitulo = "REFRENDADO".equals(certificado.getNivel()) && certificado.getEntidadImpresa() != null
                        ? "Certificado refrendado por " + certificado.getEntidadImpresa()
                        : "Certificado de aprobación";
                texto(c, normal, 13, GRIS, subtitulo, ancho, alto - 130);

                texto(c, normal, 13, Color.BLACK, "Se otorga el presente certificado a", ancho, alto - 160);
                float tamanoNombre = ajustar(negrita, certificado.getNombresImpresos(), 30, ancho - 160);
                texto(c, negrita, tamanoNombre, AZUL, certificado.getNombresImpresos(), ancho, alto - 200);
                linea(c, ancho / 2 - 190, alto - 210, ancho / 2 + 190, alto - 210);

                float y = alto - 245;
                texto(c, normal, 13, Color.BLACK,
                        "por haber aprobado el " + minuscula(certificado.getTipoCursoImpreso()) + ":", ancho, y);
                y -= 28;
                for (String linea : partir(negrita, 20, certificado.getTituloCursoImpreso(), ancho - 180)) {
                    texto(c, negrita, 20, Color.BLACK, linea, ancho, y);
                    y -= 25;
                }
                y -= 6;
                texto(c, normal, 12, Color.BLACK, detalleDuracion(certificado), ancho, y);
                y -= 18;
                if (certificado.getNotaImpresa() != null) {
                    texto(c, normal, 12, Color.BLACK, "Nota final: " + certificado.getNotaImpresa().toPlainString()
                            + " · Nivel " + ("REFRENDADO".equals(certificado.getNivel()) ? "refrendado" : "normal"),
                            ancho, y);
                    y -= 18;
                }
                texto(c, normal, 12, GRIS, certificado.getLugarEmision() + ", "
                        + fechaLarga(certificado.getEmitidoEn().atZone(LIMA).toLocalDate()), ancho, y - 8);

                firmas(c, firmantes, ancho);
                qr(c, urlVerificacion, 40, 38, 84);
                c.beginText();
                c.setFont(normal, 8);
                c.setNonStrokingColor(GRIS);
                c.newLineAtOffset(132, 78);
                c.showText(seguro(normal, "Código: " + certificado.getCodigo()));
                c.endText();
                c.beginText();
                c.setFont(normal, 8);
                c.setNonStrokingColor(GRIS);
                c.newLineAtOffset(132, 66);
                c.showText(seguro(normal, "Verifica la autenticidad escaneando el código QR."));
                c.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException | WriterException e) {
            throw new IllegalStateException("No pudimos generar el PDF del certificado.", e);
        }
    }

    // ----------------------------------------------------------------- dibujo --

    /** Logo institucional centrado arriba. Si el recurso no está disponible, el certificado sale igual solo con texto. */
    private void logo(PDDocument documento, PDPageContentStream c, float ancho, float alto) {
        try (var entrada = new ClassPathResource("certificado/logo-esejur.png").getInputStream()) {
            PDImageXObject imagen = PDImageXObject.createFromByteArray(documento, entrada.readAllBytes(), "logo");
            float h = 46;
            float w = imagen.getWidth() * h / imagen.getHeight();
            c.drawImage(imagen, (ancho - w) / 2, alto - 82, w, h);
        } catch (IOException | RuntimeException e) {
            // Sin logo no se pierde ninguna información del certificado.
        }
    }

    private void marco(PDPageContentStream c, float ancho, float alto) throws IOException {
        c.setStrokingColor(AZUL);
        c.setLineWidth(3);
        c.addRect(20, 20, ancho - 40, alto - 40);
        c.stroke();
        c.setLineWidth(0.8f);
        c.addRect(28, 28, ancho - 56, alto - 56);
        c.stroke();
    }

    private void linea(PDPageContentStream c, float x1, float y1, float x2, float y2) throws IOException {
        c.setStrokingColor(GRIS);
        c.setLineWidth(0.8f);
        c.moveTo(x1, y1);
        c.lineTo(x2, y2);
        c.stroke();
    }

    private void texto(PDPageContentStream c, PDFont fuente, float tamano, Color color, String valor, float ancho, float y)
            throws IOException {
        String limpio = seguro(fuente, valor);
        float w = fuente.getStringWidth(limpio) / 1000 * tamano;
        c.beginText();
        c.setFont(fuente, tamano);
        c.setNonStrokingColor(color);
        c.newLineAtOffset((ancho - w) / 2, y);
        c.showText(limpio);
        c.endText();
    }

    private void firmas(PDPageContentStream c, List<CertificadoFirmante> firmantes, float ancho) throws IOException {
        if (firmantes.isEmpty()) {
            return;
        }
        List<CertificadoFirmante> usados = firmantes.size() > 3 ? firmantes.subList(0, 3) : firmantes;
        float zona = (ancho - 320) / usados.size();
        float inicio = 200;
        for (int i = 0; i < usados.size(); i++) {
            CertificadoFirmante f = usados.get(i);
            float centro = inicio + zona * i + zona / 2;
            linea(c, centro - 95, 110, centro + 95, 110);
            centrar(c, negrita, 10, Color.BLACK, f.getNombreImpreso(), centro, 96);
            centrar(c, cursiva, 9, GRIS, f.getCargoImpreso(), centro, 83);
        }
    }

    private void centrar(PDPageContentStream c, PDFont fuente, float tamano, Color color, String valor, float centro, float y)
            throws IOException {
        String limpio = seguro(fuente, valor);
        float w = fuente.getStringWidth(limpio) / 1000 * tamano;
        c.beginText();
        c.setFont(fuente, tamano);
        c.setNonStrokingColor(color);
        c.newLineAtOffset(centro - w / 2, y);
        c.showText(limpio);
        c.endText();
    }

    /** Código QR dibujado como cuadrados vectoriales (no depende de imágenes ni de AWT). */
    private void qr(PDPageContentStream c, String contenido, float x, float y, float lado)
            throws IOException, WriterException {
        BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        float celda = lado / matriz.getWidth();
        c.setNonStrokingColor(Color.BLACK);
        for (int fila = 0; fila < matriz.getHeight(); fila++) {
            for (int col = 0; col < matriz.getWidth(); col++) {
                if (matriz.get(col, fila)) {
                    c.addRect(x + col * celda, y + lado - (fila + 1) * celda, celda + 0.2f, celda + 0.2f);
                }
            }
        }
        c.fill();
    }

    // ------------------------------------------------------------------ texto --

    private String detalleDuracion(Certificado c) {
        String horas = c.getHorasImpresas().stripTrailingZeros().toPlainString() + " horas académicas";
        if (c.getFechaInicioImpresa() != null && c.getFechaFinImpresa() != null) {
            return "Duración: " + horas + ", del " + fechaLarga(c.getFechaInicioImpresa()) + " al "
                    + fechaLarga(c.getFechaFinImpresa());
        }
        return "Duración: " + horas;
    }

    private static String fechaLarga(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    private static String minuscula(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.forLanguageTag("es-PE"));
    }

    /** Reduce el tamaño de la letra hasta que el texto quepa en el ancho disponible. */
    private float ajustar(PDFont fuente, String valor, float maximo, float disponible) throws IOException {
        float tamano = maximo;
        String limpio = seguro(fuente, valor);
        while (tamano > 12 && fuente.getStringWidth(limpio) / 1000 * tamano > disponible) {
            tamano -= 1;
        }
        return tamano;
    }

    private List<String> partir(PDFont fuente, float tamano, String valor, float disponible) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : seguro(fuente, valor).split("\\s+")) {
            String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(candidata) / 1000 * tamano > disponible && !actual.isEmpty()) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(candidata);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    /** Las fuentes estándar solo codifican WinAnsi: lo que no se pueda imprimir se reemplaza por «?». */
    private String seguro(PDFont fuente, String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder limpio = new StringBuilder();
        valor.codePoints().forEach(cp -> {
            String caracter = new String(Character.toChars(cp));
            try {
                fuente.encode(caracter);
                limpio.append(caracter);
            } catch (IOException | IllegalArgumentException e) {
                limpio.append('?');
            }
        });
        return limpio.toString();
    }
}
