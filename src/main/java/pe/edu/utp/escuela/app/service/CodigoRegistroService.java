package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.TipoCurso;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.TipoCursoRepository;

/**
 * Código de registro de un curso: {@code NN-SIGLA_AAAA}, por ejemplo {@code 02-DP_2026} para el segundo diplomado de
 * 2026. El número cuenta los cursos del mismo tipo y año; el año es el del inicio del curso o, si no tiene inicio (un
 * virtual sin fechas), el de la fecha en que se asigna. Todos los alumnos del curso comparten el mismo código.
 */
@Service
@RequiredArgsConstructor
public class CodigoRegistroService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final TipoCursoRepository tiposCurso;
    private final CursoRepository cursos;
    private final Clock clock;

    /**
     * Asigna el código si el curso aún no lo tiene. Se llama al publicar el curso (y, por si es anterior a este
     * código, al emitir su primer certificado). Bloquea la fila del tipo para que dos publicaciones simultáneas no
     * reciban el mismo número.
     */
    @Transactional
    public void asignar(Curso curso) {
        if (curso.getCodigoRegistro() != null || curso.getTipoCurso() == null) {
            return;
        }
        TipoCurso tipo = tiposCurso.bloquearPorId(curso.getTipoCurso().getId()).orElse(curso.getTipoCurso());
        LocalDate referencia = curso.getFechaInicio() != null ? curso.getFechaInicio() : LocalDate.now(clock.withZone(LIMA));
        int anio = referencia.getYear();
        int numero = cursos.maximoNumeroRegistro(tipo.getId(), anio) + 1;
        String sigla = tipo.getSiglaRegistro() == null || tipo.getSiglaRegistro().isBlank() ? "XX" : tipo.getSiglaRegistro();
        curso.setAnioRegistro(anio);
        curso.setNumeroRegistro(numero);
        curso.setCodigoRegistro(String.format("%02d-%s_%d", numero, sigla.toUpperCase(), anio));
    }
}
