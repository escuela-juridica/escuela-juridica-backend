package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
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
import pe.edu.utp.escuela.app.dto.EmisionManualSituacionResponse.FirmanteVista;
import pe.edu.utp.escuela.app.dto.EmitirCertificadoManualRequest;
import pe.edu.utp.escuela.app.dto.VistaPreviaCertificadoRequest;
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
import pe.edu.utp.escuela.app.repository.CursoFirmanteRepository;
import pe.edu.utp.escuela.app.repository.ExcepcionAcademicaRepository;
import pe.edu.utp.escuela.app.repository.FirmanteRepository;
import pe.edu.utp.escuela.app.repository.HistorialCertificadoRepository;
import pe.edu.utp.escuela.app.repository.LogroCertificacionRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.AvanceAcademicoService.AvanceAcademico;

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
    private final CursoFirmanteRepository cursoFirmantes;
    private final FirmanteRepository firmantes;
    private final UsuarioRepository usuarios;
    private final ArchivoAlmacenamientoService archivos;
    private final CertificadoPdfExport pdf;
    private final ImagenCertificadoService imagenes;
    private final CurrentUserService actual;
    private final Clock clock;

    @Value("${application.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public Page<CertificadoGestionResponse> listar(Long usuarioId, Long cursoId, String estadoCertificado, Pageable pageable) {
        exigirAdministrador();
        String estado = estadoCertificado == null ? "" : estadoCertificado.strip().toUpperCase(Locale.ROOT);
        if (!List.of("", "SIN_CERTIFICADO", "VIGENTE", "ANULADO").contains(estado)) {
            throw new BusinessValidationException("El estado del certificado no es válido.");
        }
        Page<Matricula> pagina = matriculas.buscarGestionCertificados(usuarioId, cursoId, estado, pageable);
        Map<Long, Certificado> porMatricula = pagina.isEmpty() ? Map.of()
                : certificados.findByLogro_Matricula_IdIn(pagina.getContent().stream().map(Matricula::getId).toList())
                        .stream().collect(Collectors.toMap(c -> c.getLogro().getMatricula().getId(), Function.identity(),
                                Certificado::actualEntre));
        return pagina.map(m -> fila(m, porMatricula.get(m.getId())));
    }

    private CertificadoGestionResponse fila(Matricula m, Certificado c) {
        AvanceAcademico avance = avanceAcademico.calcular(m);
        return new CertificadoGestionResponse(m.getId(), c == null ? null : c.getId(),
                m.getUsuario().getPersona().nombreCompleto(),
                m.getUsuario().getCorreo(), m.getCurso().getId(), m.getCurso().getTitulo(), m.getEstado(),
                c == null ? "SIN_CERTIFICADO" : c.getEstado(), c == null ? null : c.getCodigo(),
                c == null ? null : c.getNivel(), c == null ? null : c.getTipoEmision(),
                c == null ? null : c.getEmitidoEn(), (c == null || Certificado.ANULADO.equals(c.getEstado())) && !"CANCELADA".equals(m.getEstado()),
                avance.porcentaje(), avance.cumpleTodo(), m.getFechaMatricula());
    }

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
        Certificado reemplazado = certificados.findByLogro_Matricula_Id(matriculaId).orElse(null);
        boolean reemision = reemplazado != null;
        String nombresPorAdministracion = null;
        if (!situacion.datosConfirmados() || (reemision && p.nombres() != null && !p.nombres().isBlank())) {
            nombresPorAdministracion = p.nombres() == null || p.nombres().isBlank()
                    ? matricula.getUsuario().getPersona().nombreCompleto()
                    : p.nombres().strip().replaceAll("\\s+", " ");
            certificacion.confirmarNombresAdministrativos(matricula, nombresPorAdministracion);
        }
        LogroCertificacion logro = logros.findByMatricula_Id(matriculaId).orElse(null);
        Instant finalizacion = logro != null && logro.getFechaFinalizacion() != null ? logro.getFechaFinalizacion()
                : fechaFinalizacion(matricula, p.fechaFinalizacion(), situacion);
        String motivo = p.motivo().strip();
        Long administrador = actual.get().userId();

        Certificado emitido = certificacion.emitirExcepcional(matricula, motivo, administrador, finalizacion);
        registrarEvidencia(matricula, emitido, motivo, administrador, situacion, nombresPorAdministracion, reemplazado);
        return construir(matricula);
    }

    private Instant fechaFinalizacion(Matricula matricula, LocalDate indicada, EmisionManualSituacionResponse s) {
        Instant ahora = clock.instant();
        if (indicada == null) {
            return ahora;
        }
        if (indicada.isBefore(s.fechaMinimaFinalizacion()) || indicada.isAfter(s.fechaMaximaFinalizacion())) {
            throw new BusinessValidationException("La fecha de finalización debe estar entre la matrícula ("
                    + s.fechaMinimaFinalizacion() + ") y hoy (" + s.fechaMaximaFinalizacion() + ").");
        }
        Instant instante = indicada.atTime(12, 0).atZone(LIMA).toInstant();
        return instante.isAfter(ahora) ? ahora : instante;
    }

    private void registrarEvidencia(Matricula matricula, Certificado emitido, String motivo, Long administrador,
            EmisionManualSituacionResponse antes, String nombresPorAdministracion, Certificado reemplazado) {
        String nombresNota = nombresPorAdministracion == null ? ""
                : " · nombres impresos confirmados por la administración: " + nombresPorAdministracion;
        Instant ahora = clock.instant();
        HistorialCertificado h = new HistorialCertificado();
        h.setCertificado(emitido);
        h.setAccion(HistorialCertificado.EMISION_MANUAL);
        h.setDatosAnteriores(reemplazado == null ? "Sin certificado"
                : "Reemplaza al certificado " + reemplazado.getCodigo() + " (anulado)");
        h.setDatosNuevos("Certificado " + emitido.getCodigo() + " · nivel " + emitido.getNivel()
                + (emitido.getNotaImpresa() == null ? " · sin nota" : " · nota " + emitido.getNotaImpresa().toPlainString())
                + nombresNota);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(administrador);
        h.setRealizadoEn(ahora);
        historial.save(h);

        ExcepcionAcademica e = new ExcepcionAcademica();
        e.setMatricula(matricula);
        e.setTipo(ExcepcionAcademica.EMITIR_CERTIFICADO);
        e.setReferenciaTipo("CERTIFICADO");
        e.setReferenciaId(emitido.getId());
        e.setValorAnterior(reemplazado != null ? "Certificado " + reemplazado.getCodigo() + " anulado"
                : antes.cumpleTodo() ? "Cumplía los requisitos" : antes.frase());
        e.setValorNuevo("Certificado " + emitido.getCodigo() + " emitido manualmente" + nombresNota);
        e.setMotivo(motivo);
        e.setRealizadoPorUsuarioId(administrador);
        e.setRealizadoEn(ahora);
        excepciones.save(e);
    }

    @Transactional(readOnly = true)
    public CertificadoDetalleResponse detalle(Long certificadoId) {
        exigirAdministrador();
        return detalleDe(certificado(certificadoId));
    }

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
        String entidadNueva = entidadValida(c, p.entidad());

        List<CertificadoFirmante> actuales = certificadoFirmantes.findByCertificado_IdOrderByOrdenAsc(c.getId());
        List<CertificadoFirmante> nuevos = firmantesResultantes(c, actuales, p.firmanteIds());

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
        c.setArchivoPdfUrl(archivos.guardarBytes("certificados", c.getCodigo() + ".pdf",
                pdf.generar(c, nuevos, verificacion, imagenes::cargar, entidadCurso == null ? null : entidadCurso.getLogoUrl())));
        certificados.save(c);

        guardarCambio(c, HistorialCertificado.CORRECCION, antes, despues, p.motivo().strip());
        return detalleDe(c);
    }

    @Transactional(readOnly = true)
    public byte[] vistaPrevia(Long certificadoId, VistaPreviaCertificadoRequest p) {
        exigirAdministrador();
        Certificado c = certificado(certificadoId);
        exigirVigente(c, "previsualizar");
        String entidadNueva = entidadValida(c, p.entidad());

        Certificado copia = new Certificado();
        copia.setLogro(c.getLogro());
        copia.setCodigo(c.getCodigo());
        copia.setEstado(c.getEstado());
        copia.setTipoEmision(c.getTipoEmision());
        copia.setNivel(c.getNivel());
        copia.setNombresImpresos(p.nombres().strip());
        copia.setTituloCursoImpreso(p.titulo().strip());
        copia.setTipoCursoImpreso(c.getTipoCursoImpreso());
        copia.setHorasImpresas(p.horas());
        copia.setFechaInicioImpresa(c.getFechaInicioImpresa());
        copia.setFechaFinImpresa(c.getFechaFinImpresa());
        copia.setLugarEmision(c.getLugarEmision());
        copia.setTemarioImpreso(c.getTemarioImpreso());
        copia.setCodigoRegistroImpreso(c.getCodigoRegistroImpreso());
        copia.setEntidadImpresa(entidadNueva);
        copia.setNotaImpresa(c.getNotaImpresa());
        copia.setEmitidoEn(c.getEmitidoEn());

        List<CertificadoFirmante> actuales = certificadoFirmantes.findByCertificado_IdOrderByOrdenAsc(c.getId());
        List<CertificadoFirmante> firmantesVista = firmantesResultantes(copia, actuales, p.firmanteIds());
        String verificacion = frontendBaseUrl + "/verificar-certificado?codigo=" + c.getCodigo();
        var entidadCurso = c.getLogro().getMatricula().getCurso().getEntidadCertificadora();
        return pdf.generar(copia, firmantesVista, verificacion, imagenes::cargar,
                entidadCurso == null ? null : entidadCurso.getLogoUrl(), true);
    }

    private static String entidadValida(Certificado c, String entidad) {
        String nueva = entidad == null || entidad.isBlank() ? null : entidad.strip();
        boolean refrendado = "REFRENDADO".equals(c.getNivel());
        if (refrendado && nueva == null) {
            throw new BusinessValidationException("Un certificado refrendado debe indicar la entidad que lo refrenda.");
        }
        if (!refrendado && nueva != null) {
            throw new BusinessValidationException("Un certificado de nivel Normal no lleva entidad.");
        }
        return nueva;
    }

    private List<CertificadoFirmante> firmantesResultantes(Certificado destino, List<CertificadoFirmante> actuales,
            List<Long> idsSolicitados) {
        List<Long> idsNuevos = idsSolicitados.stream().distinct().toList();
        if (idsNuevos.size() != idsSolicitados.size()) {
            throw new BusinessValidationException("No repitas el mismo firmante.");
        }
        Map<Long, CertificadoFirmante> impresos = actuales.stream()
                .collect(Collectors.toMap(f -> f.getFirmante().getId(), Function.identity()));
        List<CertificadoFirmante> nuevos = new java.util.ArrayList<>();
        int orden = 1;
        for (Long id : idsNuevos) {
            CertificadoFirmante existente = impresos.get(id);
            CertificadoFirmante f = new CertificadoFirmante();
            f.setCertificado(destino);
            f.setOrden(orden++);
            if (existente != null) {
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
        return nuevos;
    }
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
                    "Un certificado anulado no se puede " + accion + ": la anulación no se deshace. Si corresponde, emite uno nuevo para el alumno.");
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
                m.getCurso().getId(), m.getId(), c.getNombresImpresos(), c.getTituloCursoImpreso(), c.getTipoCursoImpreso(),
                c.getHorasImpresas(), c.getEntidadImpresa(), c.getEmitidoEn(), c.getAnuladoEn(),
                c.getMotivoAnulacion(), Certificado.VIGENTE.equals(c.getEstado()), firmantesImpresos,
                List.copyOf(disponibles.values()), cambios);
    }

    private Certificado certificado(Long id) {
        return certificados.findById(id).orElseThrow(() -> new ResourceNotFoundException("El certificado no existe."));
    }

    private EmisionManualSituacionResponse construir(Matricula m) {
        AvanceAcademico avance = avanceAcademico.calcular(m);
        LogroCertificacion logro = logros.findByMatricula_Id(m.getId()).orElse(null);
        Certificado certificado = certificados.findByLogro_Matricula_Id(m.getId()).orElse(null);
        pe.edu.utp.escuela.app.entity.Persona persona = m.getUsuario().getPersona();
        boolean datosDelLogro = logro != null && logro.getDatosConfirmadosEn() != null && logro.getNombresConfirmados() != null;
        boolean datos = datosDelLogro || !persona.isCertificadoNombresPendientes();
        String nombresConfirmados = datosDelLogro ? logro.getNombresConfirmados()
                : datos ? persona.nombreCompleto() : null;
        Instant finalizacion = logro == null ? null : logro.getFechaFinalizacion();
        String nivel = certificado != null ? certificado.getNivel() : nivelProyectado(m, avance);
        Certificado vigente = certificado != null && Certificado.VIGENTE.equals(certificado.getEstado()) ? certificado : null;
        String entidad = vigente != null ? vigente.getEntidadImpresa()
                : "REFRENDADO".equals(nivel) && m.getCurso().getEntidadCertificadora() != null
                        ? m.getCurso().getEntidadCertificadora().getNombre() : null;
        return new EmisionManualSituacionResponse(m.getId(), m.getUsuario().getPersona().nombreCompleto(),
                m.getUsuario().getCorreo(), m.getCurso().getTitulo(), m.getCurso().getModalidad(), m.getEstado(),
                m.getFechaMatricula(), finalizacion, m.getFechaMatricula().atZone(LIMA).toLocalDate(),
                LocalDate.now(clock.withZone(LIMA)), datos, nombresConfirmados,
                certificado != null ? certificado.getNotaImpresa() : avance.notaFinal(), nivel, entidad,
                horasDelCurso(m, vigente), tipoCurso(m, vigente), fechaInicio(m, vigente),
                vigente != null ? vigente.getFechaFinImpresa() : fechaFinDelCurso(m), firmantesImpresos(m, vigente),
                avance.condiciones(), avance.cumpleTodo(), SeguimientoAcademicoService.enTerceraPersona(avance.frase()),
                certificado == null ? null : new CertificadoResumen(certificado.getCodigo(), certificado.getEstado(),
                        certificado.getNivel(), certificado.getTipoEmision(), certificado.getEmitidoEn(),
                        certificado.getMotivoEmisionManual()),
                bloqueo(m, avance, certificado));
    }


    private static BigDecimal horasDelCurso(Matricula m, Certificado c) {
        if (c != null) {
            return c.getHorasImpresas();
        }
        return m.getCurso().getHorasAcademicas() != null ? m.getCurso().getHorasAcademicas() : BigDecimal.ZERO;
    }

    private static String tipoCurso(Matricula m, Certificado c) {
        if (c != null) {
            return c.getTipoCursoImpreso();
        }
        return m.getCurso().getTipoCurso() != null ? m.getCurso().getTipoCurso().getNombre() : "Curso";
    }

    private static LocalDate fechaInicio(Matricula m, Certificado c) {
        return c != null ? c.getFechaInicioImpresa() : m.getCurso().getFechaInicio();
    }

    private static LocalDate fechaFinDelCurso(Matricula m) {
        return m.getCurso().getFechaFin();
    }

    private List<FirmanteVista> firmantesImpresos(Matricula m, Certificado c) {
        if (c != null) {
            return certificadoFirmantes.findByCertificado_IdOrderByOrdenAsc(c.getId()).stream()
                    .map(f -> new FirmanteVista(f.getNombreImpreso(), f.getCargoImpreso())).toList();
        }
        return cursoFirmantes.findByCurso_IdOrderByOrdenAsc(m.getCurso().getId()).stream()
                .map(f -> new FirmanteVista(f.getFirmante().getPersona().nombreCompleto(), f.getFirmante().getCargoFirma())).toList();
    }

    private static String nivelProyectado(Matricula m, AvanceAcademico avance) {
        var regla = avance.ruta().regla();
        boolean refrendado = regla.isRequiereExamenes() && avance.notaFinal() != null
                && m.getCurso().getEntidadCertificadora() != null
                && avance.notaFinal().compareTo(regla.getNotaRefrendado()) >= 0;
        return refrendado ? "REFRENDADO" : "NORMAL";
    }

    private static String bloqueo(Matricula m, AvanceAcademico avance, Certificado certificado) {
        if (certificado != null && Certificado.VIGENTE.equals(certificado.getEstado())) {
            return "Este alumno ya tiene un certificado vigente en este curso. Para cambiar sus datos usa «Corregir»; "
                    + "si lo anulas, podrás emitirle uno nuevo.";
        }
        if ("CANCELADA".equals(m.getEstado())) {
            return "La matrícula está cancelada: el alumno ya no tiene acceso al curso. Reactívala primero si corresponde emitir su certificado.";
        }
        if ("PENDIENTE_PAGO".equals(m.getEstado())) {
            return "La matrícula está pendiente de pago: el alumno todavía no tiene acceso al curso.";
        }
        if (avance.bloqueadoPorRevision()) {
            return "Hay un examen con respuestas abiertas pendiente de revisión. Publica primero la calificación definitiva.";
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
