package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.CertificadoGestionResponse;
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse;
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse.CertificadoResumen;
import pe.edu.utp.escuela.app.dto.EmitirCertificadoManualRequest;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.HistorialCertificado;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.HistorialCertificadoRepository;
import pe.edu.utp.escuela.app.repository.LogroCertificacionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;

/**
 * HU-035 — Gestión administrativa de certificados: lista matrículas con la situación de su certificado y
 * emite excepcionalmente uno con motivo. Mantiene todas las garantías de un certificado normal: un solo
 * documento por alumno y curso, nombres confirmados por el alumno, congelamiento de nota, nivel y datos,
 * descarga privada, QR público y cierre de nuevos intentos calificados. Deja evidencia de la excepción.
 */
@Service
@RequiredArgsConstructor
public class CertificadoAdminService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepository matriculas;
    private final LogroCertificacionRepository logros;
    private final CertificadoRepository certificados;
    private final HistorialCertificadoRepository historial;
    private final ExcepcionAcademicaRepository excepciones;
    private final AvanceAcademicoService avanceAcademico;
    private final CertificacionService certificacion;
    private final CurrentUserService actual;
    private final Clock clock;

    // ---------------------------------------------------------------------------------------- listado --

    @Transactional(readOnly = true)
    public Page<CertificadoGestionResponse> listar(String texto, String estadoCertificado, Pageable pageable) {
        exigirAdministrador();
        String estado = estadoCertificado == null ? "" : estadoCertificado.strip().toUpperCase(Locale.ROOT);
        if (!List.of("", "SIN_CERTIFICADO", "VIGENTE", "ANULADO").contains(estado)) {
            throw new BusinessValidationException("El estado del certificado no es válido.");
        }
        Page<Matricula> pagina = matriculas.buscarGestionCertificados(
                texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT), estado, pageable);
        Map<Long, Certificado> porMatricula = pagina.isEmpty() ? Map.of()
                : certificados.findByLogro_Matricula_IdIn(pagina.getContent().stream().map(Matricula::getId).toList())
                        .stream().collect(Collectors.toMap(c -> c.getLogro().getMatricula().getId(), Function.identity()));
        return pagina.map(m -> fila(m, porMatricula.get(m.getId())));
    }

    private CertificadoGestionResponse fila(Matricula m, Certificado c) {
        return new CertificadoGestionResponse(m.getId(), m.getUsuario().getPersona().nombreCompleto(),
                m.getUsuario().getCorreo(), m.getCurso().getId(), m.getCurso().getTitulo(), m.getEstado(),
                c == null ? "SIN_CERTIFICADO" : c.getEstado(), c == null ? null : c.getCodigo(),
                c == null ? null : c.getNivel(), c == null ? null : c.getTipoEmision(),
                c == null ? null : c.getEmitidoEn(), c == null);
    }

    // ----------------------------------------------------------------------------------- emisión manual --

    @Transactional(readOnly = true)
    public EmisionManualSituacionResponse situacion(Long matriculaId) {
        exigirAdministrador();
        return construir(matricula(matriculaId));
    }

    @Transactional
    public EmisionManualSituacionResponse emitir(Long matriculaId, EmitirCertificadoManualRequest p) {
        exigirAdministrador();
        Matricula matricula = matricula(matriculaId);
        EmisionManualSituacionResponse situacion = construir(matricula);
        if (situacion.bloqueo() != null) {
            throw new BusinessValidationException(situacion.bloqueo());
        }
        LogroCertificacion logro = logros.findByMatricula_Id(matriculaId).orElse(null);
        Instant finalizacion = logro != null && logro.getFechaFinalizacion() != null ? logro.getFechaFinalizacion()
                : fechaFinalizacion(matricula, p.fechaFinalizacion(), situacion);
        String motivo = p.motivo().strip();
        Long administrador = actual.get().userId();

        Certificado emitido = certificacion.emitirExcepcional(matricula, motivo, administrador, finalizacion);
        registrarEvidencia(matricula, emitido, motivo, administrador, situacion);
        return construir(matricula);
    }

    /** La fecha de finalización que falta: la indicada dentro de límites, o la de hoy si no se indica. */
    private Instant fechaFinalizacion(Matricula matricula, LocalDate indicada, EmisionManualSituacionResponse s) {
        Instant ahora = clock.instant();
        if (indicada == null) {
            return ahora;
        }
        if (indicada.isBefore(s.fechaMinimaFinalizacion()) || indicada.isAfter(s.fechaMaximaFinalizacion())) {
            throw new BusinessValidationException("La fecha de finalización debe estar entre la matrícula ("
                    + s.fechaMinimaFinalizacion() + ") y hoy (" + s.fechaMaximaFinalizacion() + ").");
        }
        // Mediodía de Lima: sin riesgo de caer en el día anterior ni en el futuro al convertir a instante.
        Instant instante = indicada.atTime(12, 0).atZone(LIMA).toInstant();
        return instante.isAfter(ahora) ? ahora : instante;
    }

    private void registrarEvidencia(Matricula matricula, Certificado emitido, String motivo, Long administrador,
            EmisionManualSituacionResponse antes) {
        Instant ahora = clock.instant();
        HistorialCertificado h = new HistorialCertificado();
        h.setCertificado(emitido);
        h.setAccion(HistorialCertificado.EMISION_MANUAL);
        h.setDatosAnteriores("Sin certificado");
        h.setDatosNuevos("Certificado " + emitido.getCodigo() + " · nivel " + emitido.getNivel()
                + (emitido.getNotaImpresa() == null ? " · sin nota" : " · nota " + emitido.getNotaImpresa().toPlainString()));
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(administrador);
        h.setRealizadoEn(ahora);
        historial.save(h);

        ExcepcionAcademica e = new ExcepcionAcademica();
        e.setMatricula(matricula);
        e.setTipo(ExcepcionAcademica.EMITIR_CERTIFICADO);
        e.setReferenciaTipo("CERTIFICADO");
        e.setReferenciaId(emitido.getId());
        e.setValorAnterior(antes.cumpleTodo() ? "Cumplía los requisitos" : antes.frase());
        e.setValorNuevo("Certificado " + emitido.getCodigo() + " emitido manualmente");
        e.setMotivo(motivo);
        e.setRealizadoPorUsuarioId(administrador);
        e.setRealizadoEn(ahora);
        excepciones.save(e);
    }

    // ---------------------------------------------------------------------------------------- situación --

    private EmisionManualSituacionResponse construir(Matricula m) {
        AvanceAcademico avance = avanceAcademico.calcular(m);
        LogroCertificacion logro = logros.findByMatricula_Id(m.getId()).orElse(null);
        Certificado certificado = certificados.findByLogro_Matricula_Id(m.getId()).orElse(null);
        boolean datos = logro != null && logro.getDatosConfirmadosEn() != null && logro.getNombresConfirmados() != null;
        Instant finalizacion = logro == null ? null : logro.getFechaFinalizacion();
        String nivel = certificado != null ? certificado.getNivel() : nivelProyectado(m, avance);
        String entidad = certificado != null ? certificado.getEntidadImpresa()
                : "REFRENDADO".equals(nivel) && m.getCurso().getEntidadCertificadora() != null
                        ? m.getCurso().getEntidadCertificadora().getNombre() : null;
        return new EmisionManualSituacionResponse(m.getId(), m.getUsuario().getPersona().nombreCompleto(),
                m.getUsuario().getCorreo(), m.getCurso().getTitulo(), m.getCurso().getModalidad(), m.getEstado(),
                m.getFechaMatricula(), finalizacion, m.getFechaMatricula().atZone(LIMA).toLocalDate(),
                LocalDate.now(clock.withZone(LIMA)), datos, datos ? logro.getNombresConfirmados() : null,
                certificado != null ? certificado.getNotaImpresa() : avance.notaFinal(), nivel, entidad,
                avance.condiciones(), avance.cumpleTodo(), avance.frase(),
                certificado == null ? null : new CertificadoResumen(certificado.getCodigo(), certificado.getEstado(),
                        certificado.getNivel(), certificado.getTipoEmision(), certificado.getEmitidoEn(),
                        certificado.getMotivoEmisionManual()),
                bloqueo(m, avance, certificado, datos));
    }

    /** Refrendado solo con nota definitiva que alcanza el umbral del curso y entidad; en cualquier otro caso, Normal. */
    private static String nivelProyectado(Matricula m, AvanceAcademico avance) {
        var regla = avance.ruta().regla();
        boolean refrendado = regla.isRequiereExamenes() && avance.notaFinal() != null
                && m.getCurso().getEntidadCertificadora() != null
                && avance.notaFinal().compareTo(regla.getNotaRefrendado()) >= 0;
        return refrendado ? "REFRENDADO" : "NORMAL";
    }

    private static String bloqueo(Matricula m, AvanceAcademico avance, Certificado certificado, boolean datos) {
        if (certificado != null) {
            return "Este alumno ya tiene un certificado " + certificado.getEstado().toLowerCase(Locale.ROOT)
                    + " en este curso: solo se emite uno por alumno y curso.";
        }
        if ("PENDIENTE_PAGO".equals(m.getEstado())) {
            return "La matrícula está pendiente de pago: el alumno todavía no tiene acceso al curso.";
        }
        if (avance.bloqueadoPorRevision()) {
            return "Hay un examen con respuestas abiertas pendiente de revisión. Publica primero la calificación definitiva.";
        }
        if (!datos) {
            return "El alumno todavía no confirmó los nombres y apellidos que se imprimirán. Debe hacerlo desde su aula.";
        }
        return null;
    }

    private Matricula matricula(Long id) {
        return matriculas.findWithDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }
}
