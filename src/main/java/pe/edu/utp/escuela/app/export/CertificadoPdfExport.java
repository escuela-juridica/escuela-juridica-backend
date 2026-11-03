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
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.CertificadoFirmante;

/**
 * HU-032 — PDF del certificado con el diseño institucional: una página de anverso (nombre, curso, fechas, firmas y
 * un QR que lleva a la verificación pública) y, si el certificado conserva un temario, una página de reverso con lo
 * dictado y el código de registro. Solo imprime lo que está en el {@link Certificado} ya congelado; nunca consulta
 * datos vivos del alumno ni del curso, y no incluye el documento de identidad.
 *
 * <p>Las medidas se expresan en un lienzo de 2000 × 1414 unidades (el de la plantilla de diseño) y se convierten a
 * puntos de una página A4 horizontal; así se pueden contrastar una a una con la plantilla.</p>
 */
@Component
public class CertificadoPdfExport {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "setiembre", "octubre", "noviembre", "diciembre"};
    private static final Color NEGRO = new Color(0x0D, 0x0D, 0x0D);
    private static final Color GRIS = new Color(0x55, 0x5E, 0x6B);
    private static final String INSTITUCION = "Escuela Jurídica";

    /** Ancho del lienzo de la plantilla: todas las medidas del dibujo están en estas unidades. */
    private static final float LIENZO = 2000f;

    private static final byte[] OSWALD = recurso("certificado/fuentes/Oswald-Bold.ttf");
    private static final byte[] ROBOTO = recurso("certificado/fuentes/Roboto-Regular.ttf");
    private static final byte[] ROBOTO_NEGRITA = recurso("certificado/fuentes/Roboto-Bold.ttf");
    private static final byte[] ARCHIVO_NEGRO = recurso("certificado/fuentes/ArchivoBlack-Regular.ttf");
    private static final byte[] CURSIVA = recurso("certificado/fuentes/GreatVibes-Regular.ttf");

    /** RUC de la institución que se imprime en el reverso. */
    @Value("${application.certificado.ruc:20600418204}")
    private String ruc = "20600418204";

    /** Fuentes de un documento: las embebidas se crean por documento (PDFBox las ata a quien las carga). */
    private record Fuentes(PDFont oswald, PDFont normal, PDFont negrita, PDFont archivo, PDFont cursiva, PDFont marca) {
    }

    /**
     * @param imagenes resuelve la referencia de una imagen (firma o logo) a sus bytes; devuelve {@code null}
     *                 si no está disponible, y entonces el certificado sale solo con el texto
     * @param logoEntidadUrl referencia del logo de la entidad refrendante, o {@code null}
     */
    public byte[] generar(Certificado certificado, List<CertificadoFirmante> firmantes, String urlVerificacion,
            Function<String, byte[]> imagenes, String logoEntidadUrl) {
        return generar(certificado, firmantes, urlVerificacion, imagenes, logoEntidadUrl, false);
    }

    /**
     * Igual que {@link #generar(Certificado, List, String, Function, String)}; con {@code vistaPrevia} dibuja al
     * fondo la marca «VISTA PREVIA — NO VÁLIDO» para que un borrador nunca se confunda con el certificado vigente.
     */
    public byte[] generar(Certificado certificado, List<CertificadoFirmante> firmantes, String urlVerificacion,
            Function<String, byte[]> imagenes, String logoEntidadUrl, boolean vistaPrevia) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Fuentes f = new Fuentes(
                    PDType0Font.load(documento, new java.io.ByteArrayInputStream(OSWALD)),
                    PDType0Font.load(documento, new java.io.ByteArrayInputStream(ROBOTO)),
                    PDType0Font.load(documento, new java.io.ByteArrayInputStream(ROBOTO_NEGRITA)),
                    PDType0Font.load(documento, new java.io.ByteArrayInputStream(ARCHIVO_NEGRO)),
                    PDType0Font.load(documento, new java.io.ByteArrayInputStream(CURSIVA)),
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            Lienzo l = new Lienzo(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            PDImageXObject logo = imagenDelRecurso(documento, "certificado/logo-esejurce.png");

            anverso(documento, l, f, logo, certificado, firmantes, urlVerificacion, imagenes, logoEntidadUrl, vistaPrevia);
            reverso(documento, l, f, logo, certificado, vistaPrevia);

            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException | WriterException e) {
            throw new IllegalStateException("No pudimos generar el PDF del certificado.", e);
        }
    }

    // ================================================================== anverso ==

    private void anverso(PDDocument documento, Lienzo l, Fuentes f, PDImageXObject logo, Certificado cert,
            List<CertificadoFirmante> firmantes, String urlVerificacion, Function<String, byte[]> imagenes,
            String logoEntidadUrl, boolean vistaPrevia) throws IOException, WriterException {
        PDPage pagina = new PDPage(l.tamano);
        documento.addPage(pagina);
        try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
            if (vistaPrevia) {
                marcaDeAgua(c, l, f.marca());
            }
            decorado(documento, c, l);
            if (logo != null) {
                l.imagen(c, logo, 35.6f, 41.6f, 570f, 208f);
            }

            l.letrado(c, f.oswald(), 64f, NEGRO, "CERTIFICADO", 962, 277, 3.6f);
            l.centrado(c, f.normal(), 42.2f, NEGRO, "Otorgado a", 966, 367);
            l.centradoAjustado(c, f.cursiva(), 111f, 1740f, NEGRO, cert.getNombresImpresos(), 971, 500);

            String tipo = cert.getTipoCursoImpreso() == null ? "CURSO" : cert.getTipoCursoImpreso().toUpperCase(LOCALE);
            // La misma frase para todos los tipos: «POR HABER PARTICIPADO Y APROBADO EL SEMINARIO…:», «…EL CURSO:», etc.
            String frase ="POR HABER PARTICIPADO Y APROBADO " + articulo(tipo) + " " + tipo + ":";
            l.centradoAjustado(c, f.normal(), 37.9f, 1700f, NEGRO, frase, 971, 604);

            // El título ocupa dos líneas como en la plantilla; si es muy largo se reduce la letra hasta que quepa.
            String titulo = cert.getTituloCursoImpreso().toUpperCase(LOCALE);
            float tamanoTitulo = 59.2f;
            List<String> lineasTitulo = l.partir(f.archivo(), tamanoTitulo, titulo, 1700f, 2);
            while (tamanoTitulo > 34f && excede(l, f.archivo(), tamanoTitulo, lineasTitulo, 1700f)) {
                tamanoTitulo -= 2f;
                lineasTitulo = l.partir(f.archivo(), tamanoTitulo, titulo, 1700f, 2);
            }
            float y = 704;
            for (String linea : lineasTitulo) {
                l.centrado(c, f.archivo(), tamanoTitulo, NEGRO, linea, 986, y);
                y += 82;
            }
            String periodo = lineaDePeriodo(cert);
            if (!periodo.isEmpty()) {
                l.centradoAjustado(c, f.normal(), 37.9f, 1700f, NEGRO, periodo, 985, 868);
            }

            l.derecha(c, f.normal(), 28.4f, NEGRO, ciudad(cert.getLugarEmision()) + ",  "
                    + fechaLarga(cert.getEmitidoEn().atZone(LIMA).toLocalDate()), 1766, 960);

            firmas(documento, c, l, f, firmantes, imagenes);

            // QR de verificación y, debajo, el código del certificado (también va en el reverso, en «Código de registro»).
            boolean muchas = firmantes.size() >= 4;
            float qrX = muchas ? 1105 : 975;
            float qrLado = muchas ? 200 : 260;
            l.qr(c, urlVerificacion, qrX, 982, qrLado);
            l.centrado(c, f.normal(), muchas ? 18f : 22f, GRIS, cert.getCodigo(), qrX + qrLado / 2, 982 + qrLado + 26);
            if ("REFRENDADO".equals(cert.getNivel())) {
                l.imagenBytes(documento, c, imagenes.apply(logoEntidadUrl), qrX + qrLado / 2 - 200, 982 + qrLado + 40, 400, 84);
            }
        }
    }

    private static boolean excede(Lienzo l, PDFont fuente, float tamano, List<String> lineas, float disponible) {
        try {
            for (String s : lineas) {
                if (l.ancho(fuente, tamano, s) > disponible) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /** Franjas diagonales de las esquinas (arte de la plantilla, ya recortado con transparencia). */
    private void decorado(PDDocument documento, PDPageContentStream c, Lienzo l) {
        PDImageXObject superior = imagenDelRecurso(documento, "certificado/decorado-superior.png");
        PDImageXObject inferior = imagenDelRecurso(documento, "certificado/decorado-inferior.png");
        try {
            if (superior != null) {
                l.imagen(c, superior, 1202f, 0f, 797.8f, 604.9f);
            }
            if (inferior != null) {
                l.imagen(c, inferior, 0f, 716.4f, 789.5f, 697.6f);
            }
        } catch (IOException e) {
            // Sin las franjas el certificado conserva toda su información.
        }
    }

    private void firmas(PDDocument documento, PDPageContentStream c, Lienzo l, Fuentes f, List<CertificadoFirmante> firmantes,
            Function<String, byte[]> imagenes) throws IOException {
        if (firmantes.isEmpty()) {
            return;
        }
        List<CertificadoFirmante> usados = firmantes.size() > 4 ? firmantes.subList(0, 4) : firmantes;
        int n = usados.size();
        float[] centros = centrosDeFirma(n);
        float[] anchos = anchosDeFirma(n);
        for (int i = 0; i < n; i++) {
            CertificadoFirmante firmante = usados.get(i);
            float centro = centros[i];
            float ancho = anchos[i];
            l.imagenBytes(documento, c, imagenes.apply(firmante.getImagenFirmaUrl()), centro - ancho / 2 + 20, 1030, ancho - 40, 185);
            l.linea(c, centro - ancho / 2 + 20, 1209, centro + ancho / 2 - 20, 1209, 2f);
            l.centradoAjustado(c, f.negrita(), 26f, ancho - 30, NEGRO, firmante.getNombreImpreso(), centro, 1236);
            float y = 1272;
            for (String linea : l.partir(f.normal(), 26f, firmante.getCargoImpreso(), ancho - 30, 2)) {
                l.centradoAjustado(c, f.normal(), 26f, ancho - 30, NEGRO, linea, centro, y);
                y += 35;
            }
            l.centrado(c, f.normal(), 26f, NEGRO, INSTITUCION, centro, y);
        }
    }

    /**
     * Centros de las firmas: una o dos a los lados del QR como en la plantilla; con tres o cuatro se estrechan y el QR
     * se reduce para que todas queden en la misma fila sin invadir las franjas de la esquina.
     */
    private static float[] centrosDeFirma(int n) {
        return switch (n) {
            case 1 -> new float[] {640f};
            case 2 -> new float[] {640f, 1646f};
            case 3 -> new float[] {640f, 1500f, 1800f};
            default -> new float[] {630f, 940f, 1470f, 1780f};
        };
    }

    private static float[] anchosDeFirma(int n) {
        return switch (n) {
            case 1, 2 -> new float[] {540f, 540f};
            case 3 -> new float[] {480f, 300f, 300f};
            default -> new float[] {300f, 300f, 300f, 300f};
        };
    }

    // =================================================================== reverso ==

    private void reverso(PDDocument documento, Lienzo l, Fuentes f, PDImageXObject logo, Certificado cert,
            boolean vistaPrevia) throws IOException {
        List<Bloque> bloques = bloques(cert.getTemarioImpreso());
        if (bloques.isEmpty()) {
            return;
        }
        // Se ajusta el tamaño para que el temario quepa en la caja; si aun así no cabe, continúa en otra hoja.
        float escala = 1f;
        List<List<Linea>> hojas = paginar(l, f, bloques, 1f);
        if (hojas.size() > 1) {
            for (float candidata : new float[] {0.9f, 0.8f, 0.7f}) {
                List<List<Linea>> intento = paginar(l, f, bloques, candidata);
                if (intento.size() == 1) {
                    hojas = intento;
                    escala = candidata;
                    break;
                }
            }
        }
        for (int i = 0; i < hojas.size(); i++) {
            PDPage pagina = new PDPage(l.tamano);
            documento.addPage(pagina);
            try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                if (vistaPrevia) {
                    marcaDeAgua(c, l, f.marca());
                }
                cabeceraReverso(documento, c, l, f, logo, cert);
                caja(c, l, f, hojas.get(i), escala);
            }
        }
    }

    private void cabeceraReverso(PDDocument documento, PDPageContentStream c, Lienzo l, Fuentes f, PDImageXObject logo,
            Certificado cert) throws IOException {
        float y = 141;
        for (String linea : l.partir(f.negrita(), 38f, cert.getTituloCursoImpreso().toUpperCase(LOCALE), 1260f, 3)) {
            l.centrado(c, f.negrita(), 38f, NEGRO, linea, 820, y);
            y += 52;
        }
        l.centrado(c, f.normal(), 32.8f, NEGRO, "TEMARIO", 847, Math.max(276, y + 36));
        if (logo != null) {
            l.imagen(c, logo, 1588f, 100f, 360f, 131f);
        }
        l.centrado(c, f.normal(), 28.4f, NEGRO, "RUC " + ruc, 1773, 278);
        notaYHoras(c, l, f, cert);

        // Recuadro del código de registro.
        l.rectangulo(c, 1647, 571, 1902, 745, 5f);
        l.centrado(c, f.negrita(), 30.9f, NEGRO, "CÓDIGO DE", 1775, 606);
        l.centrado(c, f.negrita(), 30.9f, NEGRO, "REGISTRO", 1775, 648);
        // Código de registro del curso («01-SM_2026»); los certificados anteriores a este código muestran el propio.
        String codigo = cert.getCodigoRegistroImpreso() != null && !cert.getCodigoRegistroImpreso().isBlank()
                ? cert.getCodigoRegistroImpreso() : cert.getCodigo();
        float maximo = 225f;
        if (l.ancho(f.normal(), 28.4f, codigo) <= maximo) {
            l.centrado(c, f.normal(), 28.4f, NEGRO, codigo, 1775, 716);
        } else {
            int corte = codigo.lastIndexOf('-');
            String arriba = corte > 0 ? codigo.substring(0, corte + 1) : codigo;
            String abajo = corte > 0 ? codigo.substring(corte + 1) : "";
            l.centradoAjustado(c, f.normal(), 26f, maximo, NEGRO, arriba, 1775, 700);
            if (!abajo.isEmpty()) {
                l.centradoAjustado(c, f.normal(), 26f, maximo, NEGRO, abajo, 1775, 732);
            }
        }
    }

    private void caja(PDPageContentStream c, Lienzo l, Fuentes f, List<Linea> lineas, float escala) throws IOException {
        l.rectangulo(c, 100, 330, 1578, 1150, 6f);
        float tamano = 28f * escala;
        float paso = 39.2f * escala;
        float y = 383f;
        for (Linea linea : lineas) {
            switch (linea.tipo) {
                case ENCABEZADO -> l.izquierda(c, f.negrita(), tamano, NEGRO, linea.texto, 142, y);
                case VINETA -> {
                    if (linea.primera) {
                        l.circulo(c, 163, y - tamano * 0.3f, tamano * 0.13f);
                    }
                    if (linea.justificar) {
                        l.justificado(c, f.normal(), tamano, NEGRO, linea.texto, 190, y, 1497 - 190);
                    } else {
                        l.izquierda(c, f.normal(), tamano, NEGRO, linea.texto, 190, y);
                    }
                }
                case TEXTO -> l.izquierda(c, f.normal(), tamano, NEGRO, linea.texto, 142, y);
                case BLANCO -> { }
            }
            y += paso;
        }
    }

    // ---- temario: texto → bloques → líneas por hoja ----

    private enum Tipo { ENCABEZADO, VINETA, TEXTO, BLANCO }

    private record Bloque(Tipo tipo, String texto) {
    }

    private static final class Linea {
        final Tipo tipo;
        final String texto;
        final boolean primera;
        boolean justificar;

        Linea(Tipo tipo, String texto, boolean primera) {
            this.tipo = tipo;
            this.texto = texto;
            this.primera = primera;
        }
    }

    /** El temario se guarda como texto: «# » abre una sesión, «- » una viñeta y una línea vacía separa. */
    private static List<Bloque> bloques(String temario) {
        List<Bloque> lista = new ArrayList<>();
        if (temario == null || temario.isBlank()) {
            return lista;
        }
        for (String cruda : temario.split("\\R")) {
            String linea = cruda.strip();
            if (linea.isEmpty()) {
                if (!lista.isEmpty() && lista.getLast().tipo() != Tipo.BLANCO) {
                    lista.add(new Bloque(Tipo.BLANCO, ""));
                }
            } else if (linea.startsWith("# ")) {
                lista.add(new Bloque(Tipo.ENCABEZADO, linea.substring(2).strip()));
            } else if (linea.startsWith("- ")) {
                lista.add(new Bloque(Tipo.VINETA, linea.substring(2).strip()));
            } else {
                lista.add(new Bloque(Tipo.TEXTO, linea));
            }
        }
        return lista;
    }

    private List<List<Linea>> paginar(Lienzo l, Fuentes f, List<Bloque> bloques, float escala) throws IOException {
        float tamano = 28f * escala;
        int capacidad = (int) Math.floor((1120f - 383f) / (39.2f * escala)) + 1;
        List<Linea> todas = new ArrayList<>();
        for (Bloque b : bloques) {
            switch (b.tipo()) {
                case ENCABEZADO -> {
                    for (String s : l.partir(f.negrita(), tamano, b.texto().toUpperCase(LOCALE), 1400f, 3)) {
                        todas.add(new Linea(Tipo.ENCABEZADO, s, true));
                    }
                }
                case VINETA -> {
                    List<String> partes = l.partir(f.normal(), tamano, b.texto(), 1497f - 190f, 12);
                    for (int i = 0; i < partes.size(); i++) {
                        Linea linea = new Linea(Tipo.VINETA, partes.get(i), i == 0);
                        linea.justificar = i < partes.size() - 1;
                        todas.add(linea);
                    }
                }
                case TEXTO -> {
                    for (String s : l.partir(f.normal(), tamano, b.texto(), 1400f, 12)) {
                        todas.add(new Linea(Tipo.TEXTO, s, true));
                    }
                }
                case BLANCO -> todas.add(new Linea(Tipo.BLANCO, "", true));
            }
        }
        List<List<Linea>> hojas = new ArrayList<>();
        for (int i = 0; i < todas.size(); i += capacidad) {
            List<Linea> hoja = new ArrayList<>(todas.subList(i, Math.min(todas.size(), i + capacidad)));
            // Una hoja no empieza con una línea en blanco.
            while (!hoja.isEmpty() && hoja.getFirst().tipo == Tipo.BLANCO) {
                hoja.removeFirst();
            }
            hojas.add(hoja);
        }
        if (hojas.isEmpty()) {
            hojas.add(new ArrayList<>());
        }
        return hojas;
    }

    // ================================================================ textos ==

    private static final Locale LOCALE = Locale.forLanguageTag("es-PE");

    /** Más de 24 horas: curso o diplomado, que se aprueban y llevan nota; hasta 24 horas es un seminario. */
    static boolean esCursoConNota(Certificado c) {
        return c.getHorasImpresas() != null && c.getHorasImpresas().compareTo(new java.math.BigDecimal("24")) > 0;
    }

    /** «EL» o «LA» según el tipo de curso: «el diplomado», «la especialización». */
    static String articulo(String tipoMayusculas) {
        String primera = tipoMayusculas.strip().split("\\s+")[0];
        boolean femenino = primera.endsWith("CIÓN") || primera.endsWith("CION") || primera.endsWith("SIÓN")
                || primera.endsWith("SION") || primera.endsWith("DAD") || primera.endsWith("ENCIA") || primera.endsWith("ADA");
        return femenino ? "LA" : "EL";
    }

    /**
     * Línea bajo el título, igual para seminarios, cursos y diplomados y sin horas: «Realizado el 23 de octubre de
     * 2026», «Realizado los días 23 y 24 de octubre de 2026» (hasta cuatro días, todos nombrados) o, desde cinco días,
     * un rango: «Realizado del 23 al 27 de octubre de 2026». Sin fechas no se imprime nada.
     */
    static String lineaDePeriodo(Certificado c) {
        if (c.getFechaInicioImpresa() == null && c.getFechaFinImpresa() == null) {
            return "";
        }
        return "Realizado " + periodo(c.getFechaInicioImpresa(), c.getFechaFinImpresa());
    }

    /**
     * El certificado solo conserva el inicio y el fin, así que los días intermedios se deducen como seguidos. Hasta
     * cuatro días se nombran uno por uno (agrupados por mes); con más, o si cruzan de año, se usa un rango.
     */
    static String periodo(LocalDate inicio, LocalDate fin) {
        // Una sola fecha (un virtual con solo inicio, o solo fin) se trata como un único día: «el 19 de octubre de 2026».
        if (inicio == null || fin == null) {
            return "el " + fechaLarga(inicio != null ? inicio : fin);
        }
        LocalDate desde = inicio;
        LocalDate hasta = fin;
        if (hasta.isBefore(desde)) {
            hasta = desde;
        }
        if (desde.equals(hasta)) {
            return "el " + fechaLarga(desde);
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        boolean mismoAnio = desde.getYear() == hasta.getYear();
        if (dias <= 4 && mismoAnio) {
            List<String> grupos = new ArrayList<>();
            List<Integer> delMes = new ArrayList<>();
            LocalDate dia = desde;
            for (int i = 0; i < dias; i++) {
                delMes.add(dia.getDayOfMonth());
                LocalDate siguiente = dia.plusDays(1);
                boolean cierraGrupo = i == dias - 1 || siguiente.getMonth() != dia.getMonth();
                if (cierraGrupo) {
                    grupos.add(unir(delMes) + " de " + MESES[dia.getMonthValue() - 1]);
                    delMes = new ArrayList<>();
                }
                dia = siguiente;
            }
            return "los días " + String.join(" y ", grupos) + " de " + hasta.getYear();
        }
        boolean mismoMes = mismoAnio && desde.getMonth() == hasta.getMonth();
        if (mismoMes) {
            return "del " + desde.getDayOfMonth() + " al " + hasta.getDayOfMonth() + " de " + MESES[hasta.getMonthValue() - 1]
                    + " de " + hasta.getYear();
        }
        if (mismoAnio) {
            return "del " + desde.getDayOfMonth() + " de " + MESES[desde.getMonthValue() - 1] + " al " + fechaLarga(hasta);
        }
        return "del " + fechaLarga(desde) + " al " + fechaLarga(hasta);
    }

    /** «23», «23 y 24», «23, 24 y 25». */
    private static String unir(List<Integer> dias) {
        if (dias.size() == 1) {
            return String.valueOf(dias.getFirst());
        }
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < dias.size(); i++) {
            if (i > 0) {
                texto.append(i == dias.size() - 1 ? " y " : ", ");
            }
            texto.append(dias.get(i));
        }
        return texto.toString();
    }

    /** En cursos y diplomados, bajo el código de registro: la nota final en grande y las horas académicas. */
    private void notaYHoras(PDPageContentStream c, Lienzo l, Fuentes f, Certificado cert) throws IOException {
        if (!esCursoConNota(cert)) {
            return;
        }
        boolean hayNota = cert.getNotaImpresa() != null;
        float fondo = hayNota ? 982 : 862;
        l.rectangulo(c, 1647, 745, 1902, fondo, 5f);
        float y = 745;
        if (hayNota) {
            l.centrado(c, f.negrita(), 115f, NEGRO, cert.getNotaImpresa().stripTrailingZeros().toPlainString(), 1775, y + 118);
            y += 140;
        }
        String horas = cert.getHorasImpresas().stripTrailingZeros().toPlainString();
        l.centrado(c, f.negrita(), 24f, NEGRO, horas + " HORAS", 1775, y + 38);
        l.centrado(c, f.negrita(), 24f, NEGRO, "ACADÉMICAS", 1775, y + 68);
    }

    /** Solo la ciudad: «Lima, Perú» se imprime como «Lima», igual que en la plantilla («Lima, 23 de octubre de 2026»). */
    static String ciudad(String lugar) {
        if (lugar == null || lugar.isBlank()) {
            return "";
        }
        int coma = lugar.indexOf(',');
        return (coma > 0 ? lugar.substring(0, coma) : lugar).strip();
    }

    private static String fechaLarga(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    // ============================================================ marca de agua ==

    /** Marca diagonal clara al fondo: queda debajo del contenido, que se dibuja después. */
    private void marcaDeAgua(PDPageContentStream c, Lienzo l, PDFont fuente) throws IOException {
        double angulo = Math.toRadians(24);
        diagonal(c, l, fuente, 78, "VISTA PREVIA", angulo, 0);
        diagonal(c, l, fuente, 24, "NO VÁLIDO · BORRADOR SIN EFECTO", angulo, 78);
    }

    private void diagonal(PDPageContentStream c, Lienzo l, PDFont fuente, float tamano, String valor, double angulo,
            float desplazamientoY) throws IOException {
        String limpio = l.seguro(fuente, valor);
        float w = fuente.getStringWidth(limpio) / 1000 * tamano;
        float x = (float) (l.ancho / 2 - (w / 2) * Math.cos(angulo) + desplazamientoY * Math.sin(angulo));
        float y = (float) (l.alto / 2 - (w / 2) * Math.sin(angulo) - desplazamientoY * Math.cos(angulo) - tamano / 3);
        c.beginText();
        c.setFont(fuente, tamano);
        c.setNonStrokingColor(new Color(0xE4, 0xE8, 0xEF));
        c.setTextMatrix(Matrix.getRotateInstance(angulo, x, y));
        c.showText(limpio);
        c.endText();
    }

    // =============================================================== recursos ==

    private static byte[] recurso(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falta el recurso del certificado: " + ruta, e);
        }
    }

    /** Imagen del diseño; si el recurso no está disponible el certificado sale igual, solo con el texto. */
    private static PDImageXObject imagenDelRecurso(PDDocument documento, String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return PDImageXObject.createFromByteArray(documento, entrada.readAllBytes(), ruta);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ================================================================ lienzo ==

    /** Dibujo sobre el lienzo de 2000 unidades de ancho: convierte unidades de la plantilla a puntos de la página. */
    private static final class Lienzo {
        final PDRectangle tamano;
        final float ancho;
        final float alto;
        final float k;

        Lienzo(PDRectangle tamano) {
            this.tamano = tamano;
            this.ancho = tamano.getWidth();
            this.alto = tamano.getHeight();
            this.k = ancho / LIENZO;
        }

        float x(float u) {
            return u * k;
        }

        /** Unidades medidas desde el borde superior de la plantilla. */
        float y(float u) {
            return alto - u * k;
        }

        float ancho(PDFont fuente, float tamanoU, String valor) throws IOException {
            return fuente.getStringWidth(seguro(fuente, valor)) / 1000 * tamanoU;
        }

        void imagen(PDPageContentStream c, PDImageXObject imagen, float x, float y, float w, float h) throws IOException {
            c.drawImage(imagen, x(x), y(y + h), w * k, h * k);
        }

        /** Dibuja bytes de imagen dentro de la caja, conservando su proporción; ignora lo que no sea una imagen válida. */
        void imagenBytes(PDDocument documento, PDPageContentStream c, byte[] bytes, float x, float y, float w, float h) {
            if (bytes == null) {
                return;
            }
            try {
                PDImageXObject imagen = PDImageXObject.createFromByteArray(documento, bytes, "imagen");
                float escala = Math.min(w / imagen.getWidth(), h / imagen.getHeight());
                float iw = imagen.getWidth() * escala;
                float ih = imagen.getHeight() * escala;
                // Pegada al borde inferior de la caja: la firma apoya sobre su línea.
                c.drawImage(imagen, x(x + (w - iw) / 2), y(y + h), iw * k, ih * k);
            } catch (IOException | RuntimeException e) {
                // Una imagen ilegible no impide emitir el certificado.
            }
        }

        void linea(PDPageContentStream c, float x1, float y1, float x2, float y2, float grosor) throws IOException {
            c.setStrokingColor(NEGRO);
            c.setLineWidth(grosor * k);
            c.moveTo(x(x1), y(y1));
            c.lineTo(x(x2), y(y2));
            c.stroke();
        }

        void rectangulo(PDPageContentStream c, float x1, float y1, float x2, float y2, float grosor) throws IOException {
            c.setStrokingColor(NEGRO);
            c.setLineWidth(grosor * k);
            c.addRect(x(x1), y(y2), (x2 - x1) * k, (y2 - y1) * k);
            c.stroke();
        }

        void circulo(PDPageContentStream c, float cx, float cy, float radio) throws IOException {
            float r = radio * k;
            float px = x(cx);
            float py = y(cy);
            float magia = 0.5523f * r;
            c.setNonStrokingColor(NEGRO);
            c.moveTo(px + r, py);
            c.curveTo(px + r, py + magia, px + magia, py + r, px, py + r);
            c.curveTo(px - magia, py + r, px - r, py + magia, px - r, py);
            c.curveTo(px - r, py - magia, px - magia, py - r, px, py - r);
            c.curveTo(px + magia, py - r, px + r, py - magia, px + r, py);
            c.fill();
        }

        void centrado(PDPageContentStream c, PDFont fuente, float tamanoU, Color color, String valor, float centroU, float lineaBaseU)
                throws IOException {
            String limpio = seguro(fuente, valor);
            float w = fuente.getStringWidth(limpio) / 1000 * tamanoU * k;
            escribir(c, fuente, tamanoU * k, color, limpio, x(centroU) - w / 2, y(lineaBaseU), 0);
        }

        void derecha(PDPageContentStream c, PDFont fuente, float tamanoU, Color color, String valor, float derechaU, float lineaBaseU)
                throws IOException {
            String limpio = seguro(fuente, valor);
            float w = fuente.getStringWidth(limpio) / 1000 * tamanoU * k;
            escribir(c, fuente, tamanoU * k, color, limpio, x(derechaU) - w, y(lineaBaseU), 0);
        }

        void izquierda(PDPageContentStream c, PDFont fuente, float tamanoU, Color color, String valor, float izquierdaU, float lineaBaseU)
                throws IOException {
            escribir(c, fuente, tamanoU * k, color, seguro(fuente, valor), x(izquierdaU), y(lineaBaseU), 0);
        }

        /** Texto con separación entre letras (como el «CERTIFICADO» de la plantilla), centrado sin contar la última. */
        void letrado(PDPageContentStream c, PDFont fuente, float tamanoU, Color color, String valor, float centroU,
                float lineaBaseU, float separacionU) throws IOException {
            String limpio = seguro(fuente, valor);
            float separacion = separacionU * k;
            float w = fuente.getStringWidth(limpio) / 1000 * tamanoU * k + separacion * (limpio.length() - 1);
            escribir(c, fuente, tamanoU * k, color, limpio, x(centroU) - w / 2, y(lineaBaseU), separacion);
        }

        /** Centrado; si no cabe en el ancho disponible, reduce la letra hasta que quepa (con un mínimo). */
        void centradoAjustado(PDPageContentStream c, PDFont fuente, float tamanoU, float disponibleU, Color color, String valor,
                float centroU, float lineaBaseU) throws IOException {
            float t = tamanoU;
            float minimo = tamanoU * 0.45f;
            String limpio = seguro(fuente, valor);
            while (t > minimo && fuente.getStringWidth(limpio) / 1000 * t > disponibleU) {
                t -= 1;
            }
            centrado(c, fuente, t, color, limpio, centroU, lineaBaseU);
        }

        /** Línea justificada: reparte el sobrante entre las palabras para llegar al ancho indicado. */
        void justificado(PDPageContentStream c, PDFont fuente, float tamanoU, Color color, String valor, float izquierdaU,
                float lineaBaseU, float anchoU) throws IOException {
            String[] palabras = seguro(fuente, valor).split(" ");
            if (palabras.length < 2) {
                izquierda(c, fuente, tamanoU, color, valor, izquierdaU, lineaBaseU);
                return;
            }
            float suma = 0;
            for (String p : palabras) {
                suma += fuente.getStringWidth(p) / 1000 * tamanoU;
            }
            float hueco = (anchoU - suma) / (palabras.length - 1);
            float xActual = izquierdaU;
            for (String p : palabras) {
                escribir(c, fuente, tamanoU * k, color, p, x(xActual), y(lineaBaseU), 0);
                xActual += fuente.getStringWidth(p) / 1000 * tamanoU + hueco;
            }
        }

        private void escribir(PDPageContentStream c, PDFont fuente, float tamanoPt, Color color, String texto, float x, float y,
                float separacionPt) throws IOException {
            c.beginText();
            c.setFont(fuente, tamanoPt);
            c.setNonStrokingColor(color);
            c.setCharacterSpacing(separacionPt);
            c.newLineAtOffset(x, y);
            c.showText(texto);
            c.endText();
        }

        /** Parte el texto en líneas de a lo sumo {@code disponibleU}; la última absorbe el resto si se pasa de {@code maximo}. */
        List<String> partir(PDFont fuente, float tamanoU, String valor, float disponibleU, int maximo) throws IOException {
            List<String> lineas = new ArrayList<>();
            StringBuilder actual = new StringBuilder();
            for (String palabra : seguro(fuente, valor).split("\\s+")) {
                if (palabra.isEmpty()) {
                    continue;
                }
                String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (fuente.getStringWidth(candidata) / 1000 * tamanoU > disponibleU && !actual.isEmpty()
                        && lineas.size() < maximo - 1) {
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

        /** Código QR dibujado como cuadrados vectoriales (no depende de imágenes ni de AWT). */
        void qr(PDPageContentStream c, String contenido, float xU, float yU, float ladoU) throws IOException, WriterException {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            float lado = ladoU * k;
            float celda = lado / matriz.getWidth();
            float x = x(xU);
            float y = y(yU + ladoU);
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

        /** Lo que la fuente no pueda dibujar se reemplaza por «?» para no abortar la emisión. */
        String seguro(PDFont fuente, String valor) {
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
}
