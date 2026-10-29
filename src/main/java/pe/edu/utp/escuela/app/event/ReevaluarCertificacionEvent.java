package pe.edu.utp.escuela.app.event;

/**
 * HU-032 — Algo cambió en el avance académico de una matrícula (lección completada, examen
 * calificado, asistencia corregida): la certificación debe reevaluarse. Se atiende después de que
 * termina la transacción que lo originó, de modo que un fallo aquí nunca afecta a quien lo pidió.
 */
public record ReevaluarCertificacionEvent(Long matriculaId) {
}
