package pe.edu.utp.escuela.app.dto;

import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.ProgresoMaterial;
import pe.edu.utp.escuela.app.entity.Recurso;

public record MaterialAlumnoResponse(
        Long id,
        String titulo,
        int orden,
        String tipoRecurso,
        String origen,
        boolean permiteDescarga,
        String verUrl,
        String descargarUrl,
        String nombreArchivo,
        String tipoMime,
        Long tamanoBytes,
        Integer duracionSegundos,
        Integer posicionSegundos,
        Integer porcentajeVisto) {

    public static MaterialAlumnoResponse de(MaterialLeccion material, ProgresoMaterial progreso) {
        Recurso recurso = material.getRecurso();
        boolean subido = "SUBIDO".equals(recurso.getOrigen());
        String verUrl = subido
                ? "/api/app/materiales/" + material.getId() + "/contenido"
                : recurso.getReferencia();
        String descargarUrl = subido && material.isPermiteDescarga()
                ? "/api/app/materiales/" + material.getId() + "/descargar"
                : null;
        return new MaterialAlumnoResponse(
                material.getId(), material.getTitulo(), material.getOrden(), recurso.getTipo(),
                recurso.getOrigen(), material.isPermiteDescarga(), verUrl, descargarUrl,
                recurso.getNombreArchivo(), recurso.getTipoMime(), recurso.getTamanoBytes(),
                recurso.getDuracionSegundos(),
                progreso == null ? null : progreso.getUltimaPosicionSegundos(),
                progreso == null ? null : progreso.getPorcentajeVideo().intValue());
    }
}
