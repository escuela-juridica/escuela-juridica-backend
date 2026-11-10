package pe.edu.utp.escuela.app.util;

import java.util.Locale;
import java.util.Set;

public final class NombrePropio {

    private static final Locale LOCALE = Locale.forLanguageTag("es-PE");
    private static final Set<String> PARTICULAS = Set.of(
            "de", "del", "la", "las", "los", "y", "e", "da", "das", "do", "dos", "van", "von", "der", "di", "du", "le");

    private NombrePropio() {
    }

    public static String formatear(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return nombre;
        }
        String[] palabras = nombre.strip().split("\\s+");
        StringBuilder resultado = new StringBuilder(nombre.length());
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) {
                resultado.append(' ');
            }
            resultado.append(formatearPalabra(palabras[i], i == 0));
        }
        return resultado.toString();
    }

    private static String formatearPalabra(String palabra, boolean primera) {
        boolean mayusculas = palabra.chars().anyMatch(Character::isUpperCase);
        boolean minusculas = palabra.chars().anyMatch(Character::isLowerCase);
        if (mayusculas && minusculas) {
            return palabra;
        }
        String minuscula = palabra.toLowerCase(LOCALE);
        if (!primera && PARTICULAS.contains(minuscula)) {
            return minuscula;
        }
        return capitalizar(minuscula);
    }

    private static String capitalizar(String palabra) {
        StringBuilder resultado = new StringBuilder(palabra.length());
        boolean mayuscula = true;
        for (int i = 0; i < palabra.length(); i++) {
            char caracter = palabra.charAt(i);
            resultado.append(mayuscula ? String.valueOf(caracter).toUpperCase(LOCALE) : String.valueOf(caracter));
            mayuscula = caracter == '-' || caracter == '\'' || caracter == '\u2019';
        }
        return resultado.toString();
    }
}
