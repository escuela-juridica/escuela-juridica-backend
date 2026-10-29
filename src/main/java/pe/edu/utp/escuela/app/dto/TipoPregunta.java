package pe.edu.utp.escuela.app.dto;

/** SELECCION_UNICA/SELECCION_MULTIPLE/VERDADERO_FALSO califican automáticamente a partir de sus
 * opciones; RESPUESTA_ABIERTA nunca tiene respuesta correcta automática (HU-013). */
public enum TipoPregunta {
    SELECCION_UNICA,
    SELECCION_MULTIPLE,
    VERDADERO_FALSO,
    RESPUESTA_ABIERTA
}
