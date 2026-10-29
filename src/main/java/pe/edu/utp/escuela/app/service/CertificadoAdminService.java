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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.AnularCertificadoRequest;
import pe.edu.utp.escuela.app.dto.CertificadoDetalleResponse;
import pe.edu.utp.escuela.app.dto.CertificadoDetalleResponse.Cambio;
import pe.edu.utp.escuela.app.dto.CertificadoDetalleResponse.FirmanteDisponible;
import pe.edu.utp.escuela.app.dto.CertificadoDetalleResponse.FirmanteImpreso;
import pe.edu.utp.escuela.app.dto.CorregirCertificadoRequest;
import pe.edu.utp.escuela.app.dto.CertificadoGestionResponse;
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse;
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse.CertificadoResumen;
import pe.edu.utp.escuela.app.dto.EmitirCertificadoManualRequest;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.CertificadoFirmante;
import pe.edu.utp.escuela.app.entity.ExcepcionAcademica;
import pe.edu.utp.escuela.app.entity.Firmante;
import pe.edu.utp.escuela.app.entity.HistorialCertificado;
import pe.edu.utp.escuela.app.entity.LogroCertificacion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.export.CertificadoPdfExport;
import pe.edu.utp.escuela.app.repository.CertificadoFirmanteRepository;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.FirmanteRepository;
import pe.edu.utp.escuela.app.repository.HistorialCertificadoRepository;
import pe.edu.utp.escuela.app.repository.LogroCertificacionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
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
    private final CertificadoFirmanteRepository certificadoFirmantes;
    private final FirmanteRepository firmantes;
    private final UsuarioRepository usuarios;
    private final ArchivoAlmacenamientoService archivos;
    private final CertificadoPdfExport pdf;
    private final ImagenCertificadoService imagenes;
    private final CurrentUserService actual;
    private final Clock clock;

    @Value("${application.frontend-base-url}")
    private String frontendBaseUrl;

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
        return new CertificadoGestionResponse(m.getId(), c == null ? null : c.getId(),
                m.getUsuario().getPersona().nombreCompleto(),
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

    // -------------------------------------------------------------------------- HU-036 corregir y anular --

    @Transactional(readOnly = true)
    public CertificadoDetalleResponse detalle(Long certificadoId) {
        exigirAdministrador();
        return detalleDe(certificado(certificadoId));
    }

    /**
     * Corrige datos documentales (nombres, título, horas, entidad, firmantes) de un certificado VIGENTE. Conserva el
     * código y el estado, regenera el PDF (la versión anterior deja de descargarse) y guarda en el historial los datos
     * reemplazados con motivo, responsable y fecha. La nota y el nivel congelados no se tocan.
     */
    @Transactional
    public CertificadoDetalleResponse corregir(Long certificadoId, CorregirCertificadoRequest p) {
        exigirAdministrador();
        Certificado c = certificado(certificadoId);
        exigirVigente(c, "corregir");
        if (p.nivel() != null && !p.nivel().strip().equalsIgnoreCase(c.getNivel())) {
            throw new BusinessValidationException("El nivel está congelado: no se puede cambiar al corregir.");
        }
        if (p.nota() != null && (c.getNotaImpresa() == null || p.nota().compareTo(c.getNotaImpresa()) != 0)) {
            throw new BusinessValidationException("La nota está congelada: no se puede cambiar al corregir.");
        }
        String nombres = p.nombres().strip();
        String titulo = p.titulo().strip();
        String entidadNueva = p.entidad() == null || p.entidad().isBlank() ? null : p.entidad().strip();
        boolean refrendado = "REFRENDADO".equals(c.getNivel());
        if (refrendado && entidadNueva == null) {
            throw new BusinessValidationException("Un certificado refrendado debe indicar la entidad que lo refrenda.");
        }
        if (!refrendado && entidadNueva != null) {
            throw new BusinessValidationException("Un certificado de nivel Normal no lleva entidad.");
        }

        List<CertificadoFirmante> actuales = certificadoFirmantes.findByCertificado_IdOrderByOrdenAsc(c.getId());
        List<Long> idsNuevos = p.firmanteIds().stream().distinct().toList();
        if (idsNuevos.size() != p.firmanteIds().size()) {
            throw new BusinessValidationException("No repitas el mismo firmante.");
        }
        Map<Long, CertificadoFirmante> impresos = actuales.stream()
                .collect(Collectors.toMap(f -> f.getFirmante().getId(), Function.identity()));
        List<CertificadoFirmante> nuevos = new java.util.ArrayList<>();
        int orden = 1;
        for (Long id : idsNuevos) {
            CertificadoFirmante existente = impresos.get(id);
            CertificadoFirmante f = new CertificadoFirmante();
            f.setCertificado(c);
            f.setOrden(orden++);
            if (existente != null) {
                // Un firmante que ya estaba impreso conserva lo congelado al emitir.
                f.setFirmante(existente.getFirmante());
                f.setNombreImpreso(existente.getNombreImpreso());
                f.setCargoImpreso(existente.getCargoImpreso());
                f.setImagenFirmaUrl(existente.getImagenFirmaUrl());
            } else {
                Firmante origen = firmantes.findById(id)
                        .filter(Firmante::isActivo)
                        .orElseThrow(() -> new BusinessValidationException("Algún firmante elegido no está disponible."));
                f.setFirmante(origen);
                f.setNombreImpreso(origen.getPersona().nombreCompleto());
                f.setCargoImpreso(origen.getCargoFirma());
                f.setImagenFirmaUrl(origen.getImagenFirmaUrl());
            }
            nuevos.add(f);
        }

        String antes = instantanea(c, actuales);
        String despues = instantanea(nombres, titulo, p.horas(), entidadNueva, nuevos);
        if (antes.equals(despues)) {
            throw new BusinessValidationException("No hay cambios que registrar: los datos son iguales a los vigentes.");
        }

        c.setNombresImpresos(nombres);
        c.setTituloCursoImpreso(titulo);
        c.setHorasImpresas(p.horas());
        c.setEntidadImpresa(entidadNueva);
        certificados.save(c);
        certificadoFirmantes.deleteAll(actuales);
        certificadoFirmantes.flush();
        certificadoFirmantes.saveAll(nuevos);

        String verificacion = frontendBaseUrl + "/verificar-certificado?codigo=" + c.getCodigo();
        var entidadCurso = c.getLogro().getMatricula().getCurso().getEntidadCertificadora();
        // La referencia queda en el certificado: así el PDF regenerado es el que se descarga.
        c.setArchivoPdfUrl(archivos.guardarBytes("certificados", c.getCodigo() + ".pdf",
                pdf.generar(c, nuevos, verificacion, imagenes::cargar, entidadCurso == null ? null : entidadCurso.getLogoUrl())));
        certificados.save(c);

        guardarCambio(c, HistorialCertificado.CORRECCION, antes, despues, p.motivo().strip());
        return detalleDe(c);
    }

    /**
     * Anula de forma irreversible un certificado VIGENTE: retira el PDF, conserva el certificado, su historial y la
     * finalización, y deja el código activo para que la verificación pública informe «anulado». Un certificado
     * anulado nunca vuelve a ser VIGENTE.
     */
    @Transactional
    public CertificadoDetalleResponse anular(Long certificadoId, AnularCertificadoRequest p) {
        exigirAdministrador();
        Certificado c = certificado(certificadoId);
        exigirVigente(c, "anular");
        Instant ahora = clock.instant();
        c.setEstado(Certificado.ANULADO);
        c.setAnuladoEn(ahora);
        c.setMotivoAnulacion(p.motivo().strip());
        certificados.save(c);
        archivos.eliminar(c.getArchivoPdfUrl());
        guardarCambio(c, HistorialCertificado.ANULACION, "Vigente", "Anulado el " + ahora, p.motivo().strip());
        return detalleDe(c);
    }

    private void exigirVigente(Certificado c, String accion) {
        if (Certificado.ANULADO.equals(c.getEstado())) {
            throw new BusinessException(HttpStatus.CONFLICT, "CERTIFICADO_ANULADO",
                    "Un certificado anulado no se puede " + accion + ": la anulación es irreversible.");
        }
    }

    private void guardarCambio(Certificado c, String accion, String antes, String despues, String motivo) {
        HistorialCertificado h = new HistorialCertificado();
        h.setCertificado(c);
        h.setAccion(accion);
        h.setDatosAnteriores(antes);
        h.setDatosNuevos(despues);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(actual.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.save(h);
    }

    private static String instantanea(Certificado c, List<CertificadoFirmante> firmantesImpresos) {
        return instantanea(c.getNombresImpresos(), c.getTituloCursoImpreso(), c.getHorasImpresas(), c.getEntidadImpresa(),
                firmantesImpresos);
    }

    private static String instantanea(String nombres, String titulo, java.math.BigDecimal horas, String entidad,
            List<CertificadoFirmante> firmantesImpresos) {
        return "Nombres: " + nombres + "\nTítulo: " + titulo + "\nHoras: " + horas.stripTrailingZeros().toPlainString()
                + "\nEntidad: " + (entidad == null ? "—" : entidad) + "\nFirmantes: "
                + firmantesImpresos.stream().map(f -> f.getNombreImpreso() + " (" + f.getCargoImpreso() + ")")
                        .collect(Collectors.joining("; "));
    }

    private CertificadoDetalleResponse detalleDe(Certificado c) {
        Matricula m = c.getLogro().getMatricula();
        List<CertificadoFirmante> impresos = certificadoFirmantes.findByCertificado_IdOrderByOrdenAsc(c.getId());
        List<FirmanteImpreso> firmantesImpresos = impresos.stream()
                .map(f -> new FirmanteImpreso(f.getFirmante().getId(), f.getNombreImpreso(), f.getCargoImpreso())).toList();
        Map<Long, FirmanteDisponible> disponibles = new java.util.LinkedHashMap<>();
        firmantes.findByActivoTrueOrderByIdAsc(PageRequest.of(0, 100)).forEach(f -> disponibles.put(f.getId(),
                new FirmanteDisponible(f.getId(), f.getPersona().nombreCompleto(), f.getCargoFirma())));
        impresos.forEach(f -> disponibles.putIfAbsent(f.getFirmante().getId(),
                new FirmanteDisponible(f.getFirmante().getId(), f.getNombreImpreso(), f.getCargoImpreso())));
        List<Cambio> cambios = historial.findByCertificado_IdOrderByRealizadoEnDescIdDesc(c.getId()).stream()
                .map(h -> new Cambio(h.getId(), h.getAccion(), h.getDatosAnteriores(), h.getDatosNuevos(), h.getMotivo(),
                        usuarios.findWithPersonaById(h.getRealizadoPorUsuarioId())
                                .map(u -> u.getPersona().nombreCompleto()).orElse("—"),
                        h.getRealizadoEn()))
                .toList();
        return new CertificadoDetalleResponse(c.getId(), c.getCodigo(), c.getEstado(), c.getTipoEmision(), c.getNivel(),
                c.getNotaImpresa(), m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(),
                m.getCurso().getId(), c.getNombresImpresos(), c.getTituloCursoImpreso(), c.getTipoCursoImpreso(),
                c.getHorasImpresas(), c.getEntidadImpresa(), c.getEmitidoEn(), c.getAnuladoEn(),
                c.getMotivoAnulacion(), Certificado.VIGENTE.equals(c.getEstado()), firmantesImpresos,
                List.copyOf(disponibles.values()), cambios);
    }

    private Certificado certificado(Long id) {
        return certificados.findById(id).orElseThrow(() -> new ResourceNotFoundException("El certificado no existe."));
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
