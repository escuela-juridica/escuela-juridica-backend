package pe.edu.utp.escuela.app.dto;

/** AL_AGOTAR no es válido cuando el examen tiene intentos ilimitados (no hay "agotar" posible). */
public enum MostrarRespuestas {
    AL_APROBAR,
    AL_AGOTAR,
    NUNCA
}
