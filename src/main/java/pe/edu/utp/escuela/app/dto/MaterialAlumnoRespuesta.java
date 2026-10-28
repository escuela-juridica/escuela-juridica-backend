package pe.edu.utp.escuela.app.dto;

import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Recurso;

/** HU-023 — Material de una lección tal como lo ve el alumno. Para origen SUBIDO, {@code verUrl}
 * y {@code descargarUrl} apuntan siempre a este mismo API (nunca a la ruta real de
 * almacenamiento bajo {@code /uploads/...}), de forma que cada visualización/descarga revalide
 * acceso. Para YOUTUBE/EXTERNO no hay nada que proxyear: {@code verUrl} es el enlace externo real
 * y {@code descargarUrl} siempre es {@code null} (no tiene sentido "descargar" un enlace). */
public record MaterialAlumnoRespuesta(
        Long id,
        String titulo,
        int orden,
        String tipoRecurso,
        String origen,
        boolean permiteDescarga,
        String verUrl,
        String descargarUrl) {

    public static MaterialAlumnoRespuesta de(MaterialLeccion material) {
        Recurso recurso = material.getRecurso();
        boolean subido = "SUBIDO".equals(recurso.getOrigen());
        String verUrl = subido
                ? "/api/app/materiales/" + material.getId() + "/contenido"
                : recurso.getReferencia();
        String descargarUrl = subido && material.isPermiteDescarga()
                ? "/api/app/materiales/" + material.getId() + "/descargar"
                : null;
        return new MaterialAlumnoRespuesta(
                material.getId(), material.getTitulo(), material.getOrden(), recurso.getTipo(),
                recurso.getOrigen(), material.isPermiteDescarga(), verUrl, descargarUrl);
    }
}
