package pe.edu.utp.escuela.app.dto;

/** El `tipo` técnico del recurso (VIDEO/ARCHIVO/ENLACE) no se pide por separado: el servicio lo
 * deriva de este origen (y del tipo de material, para SUBIDO) para evitar combinaciones
 * inválidas. */
public enum OrigenRecurso {
    SUBIDO,
    YOUTUBE,
    EXTERNO
}
