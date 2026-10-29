package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utp.escuela.app.dto.ReclamacionBandejaResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionDetalleResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionDetalleResponse.Adjunto;
import pe.edu.utp.escuela.app.dto.ReclamacionDetalleResponse.Intento;
import pe.edu.utp.escuela.app.dto.ReclamacionDetalleResponse.Respuesta;
import pe.edu.utp.escuela.app.entity.IntentoEnvioRespuesta;
import pe.edu.utp.escuela.app.entity.Reclamacion;
import pe.edu.utp.escuela.app.entity.ReclamacionAdjunto;
import pe.edu.utp.escuela.app.entity.RespuestaReclamacion;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.IntentoEnvioRespuestaRepository;
import pe.edu.utp.escuela.app.repository.ReclamacionAdjuntoRepository;
import pe.edu.utp.escuela.app.repository.ReclamacionRepository;
import pe.edu.utp.escuela.app.repository.RespuestaReclamacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;

/**
 * HU-040 — Atención de quejas y reclamos por la administración. La respuesta se conserva <em>antes</em> de intentar el
 * correo y nunca se edita; solo un correo aceptado pasa el caso de PENDIENTE_RESPUESTA a RESPONDIDO. Un fallo deja el
 * caso pendiente con la misma respuesta disponible para reenviar (opcionalmente a un correo corregido), y cada intento
 * queda registrado. El vencimiento del plazo se resalta pero nunca cierra ni bloquea el caso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReclamacionAdminService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-PE"));
    private static final Set<String> ESTADOS = Set.of(Reclamacion.PENDIENTE_RESPUESTA, Reclamacion.RESPONDIDO);
    private static final Set<String> TIPOS = Set.of(Reclamacion.QUEJA, Reclamacion.RECLAMO);
    /** Con tres días hábiles o menos para el límite, el caso se resalta como próximo a vencer. */
    private static final int DIAS_POR_VENCER = 3;

    private final ReclamacionRepository reclamaciones;
    private final ReclamacionAdjuntoRepository adjuntos;
    private final RespuestaReclamacionRepository respuestas;
    private final IntentoEnvioRespuestaRepository intentos;
    private final UsuarioRepository usuarios;
    private final ArchivoAlmacenamientoService archivos;
    private final DiasHabilesService diasHabiles;
    private final MailService mailService;
    private final CurrentUserService actual;
    private final PlatformTransactionManager transacciones;
    private final Clock clock;

    // -------------------------------------------------------------------------------------------- consulta --

    @Transactional(readOnly = true)
    public Page<ReclamacionBandejaResponse> bandeja(String estado, String tipo, String texto, Pageable pageable) {
        exigirAdministrador();
        String e = mayusculas(estado);
        String t = mayusculas(tipo);
        if (!e.isEmpty() && !ESTADOS.contains(e)) {
            throw new BusinessValidationException("El estado no es válido.");
        }
        if (!t.isEmpty() && !TIPOS.contains(t)) {
            throw new BusinessValidationException("El tipo no es válido.");
        }
        Page<Reclamacion> pagina = reclamaciones.buscar(texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT), e, t,
                pageable);
        List<Long> ids = pagina.getContent().stream().map(Reclamacion::getId).toList();
        Map<Long, Long> imagenes = adjuntos.contarImagenes(ids);
        Map<Long, RespuestaReclamacion> porCaso = ids.isEmpty() ? Map.of()
                : respuestas.findByReclamacion_IdIn(ids).stream()
                        .collect(Collectors.toMap(r -> r.getReclamacion().getId(), Function.identity()));
        LocalDate hoy = clock.instant().atZone(LIMA).toLocalDate();
        return pagina.map(r -> {
            int restantes = restantes(r, hoy);
            RespuestaReclamacion respuesta = porCaso.get(r.getId());
            return new ReclamacionBandejaResponse(r.getId(), r.getCodigo(), r.getTipo(), r.getEstado(),
                    r.getNombres() + " " + r.getApellidos(), r.getTipoDocumento() + " " + r.getNumeroDocumento(),
                    r.getCorreoContacto(), r.getRegistradoEn(), r.getFechaLimiteRespuesta(), plazo(r, hoy, restantes),
                    restantes, imagenes.getOrDefault(r.getId(), 0L),
                    respuesta == null ? "SIN_RESPUESTA" : respuesta.getEstadoEnvio());
        });
    }

    @Transactional(readOnly = true)
    public long pendientes() {
        exigirAdministrador();
        return reclamaciones.countByEstado(Reclamacion.PENDIENTE_RESPUESTA);
    }

    @Transactional(readOnly = true)
    public ReclamacionDetalleResponse detalle(Long id) {
        exigirAdministrador();
        return construirDetalle(cargar(id));
    }

    /** Imagen adjunta del caso: privada, solo la ve la administración. */
    public record Descarga(Resource archivo, String nombre, String tipoMime) {
    }

    @Transactional(readOnly = true)
    public Descarga adjunto(Long reclamacionId, Long adjuntoId) {
        exigirAdministrador();
        ReclamacionAdjunto a = adjuntos.findByIdAndReclamacion_Id(adjuntoId, reclamacionId)
                .orElseThrow(() -> new ResourceNotFoundException("La imagen no existe."));
        return new Descarga(archivos.cargar(a.getReferencia()), a.getNombreArchivo(), a.getTipoMime());
    }

    // ------------------------------------------------------------------------------------------ respuesta --

    /**
     * Registra la respuesta y la envía. Primero se conserva (confirmando la transacción) y después se intenta el correo,
     * de modo que un fallo del envío nunca la pierde ni duplica el caso.
     */
    public ReclamacionDetalleResponse responder(Long reclamacionId, String texto) {
        exigirAdministrador();
        Long adminId = actual.get().userId();
        try {
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
                Reclamacion r = reclamaciones.findById(reclamacionId)
                        .orElseThrow(() -> new ResourceNotFoundException("La queja o reclamo no existe."));
                if (!Reclamacion.PENDIENTE_RESPUESTA.equals(r.getEstado()) || respuestas.findByReclamacion_Id(r.getId()).isPresent()) {
                    throw new BusinessException(HttpStatus.CONFLICT, "YA_RESPONDIDO",
                            "Este caso ya tiene una respuesta registrada. Usa el reenvío para volver a enviarla.");
                }
                RespuestaReclamacion respuesta = new RespuestaReclamacion();
                respuesta.setReclamacion(r);
                respuesta.setRespuesta(texto.strip());
                respuesta.setRespondidoPorUsuarioId(adminId);
                respuesta.setEstadoEnvio(RespuestaReclamacion.PENDIENTE);
                respuesta.setCreadoEn(clock.instant());
                respuestas.saveAndFlush(respuesta);
            });
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "YA_RESPONDIDO",
                    "Este caso ya tiene una respuesta registrada. Usa el reenvío para volver a enviarla.");
        }
        enviar(reclamacionId, null, adminId);
        return detalle(reclamacionId);
    }

    /**
     * Reenvía la misma respuesta con su fecha original. Mientras el caso siga pendiente se puede indicar un correo
     * corregido; el destino usado queda en el historial de intentos.
     */
    public ReclamacionDetalleResponse reenviar(Long reclamacionId, String correo) {
        exigirAdministrador();
        enviar(reclamacionId, correo == null || correo.isBlank() ? null : correo.strip().toLowerCase(Locale.ROOT),
                actual.get().userId());
        return detalle(reclamacionId);
    }

    private record Preparado(Long respuestaId, String destino, String asunto, Map<String, String> campos) {
    }

    private void enviar(Long reclamacionId, String correoNuevo, Long adminId) {
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        Preparado preparado = tx.execute(estado -> {
            Reclamacion r = reclamaciones.findById(reclamacionId)
                    .orElseThrow(() -> new ResourceNotFoundException("La queja o reclamo no existe."));
            RespuestaReclamacion respuesta = respuestas.findByReclamacion_Id(r.getId())
                    .orElseThrow(() -> new BusinessValidationException("Primero registra la respuesta del caso."));
            if (correoNuevo != null && !Reclamacion.PENDIENTE_RESPUESTA.equals(r.getEstado())) {
                throw new BusinessValidationException(
                        "El caso ya fue respondido: solo se puede reenviar al mismo correo.");
            }
            String destino = correoNuevo != null ? correoNuevo
                    : intentos.findFirstByRespuesta_IdOrderByIdDesc(respuesta.getId())
                            .map(IntentoEnvioRespuesta::getCorreoDestino).orElse(r.getCorreoContacto());
            return new Preparado(respuesta.getId(), destino,
                    "Respuesta a tu " + r.getTipo().toLowerCase(Locale.forLanguageTag("es-PE")) + " " + r.getCodigo(),
                    Map.of("codigo", r.getCodigo(),
                            "tipo", Reclamacion.QUEJA.equals(r.getTipo()) ? "queja" : "reclamo",
                            "nombre", r.getNombres() + " " + r.getApellidos(),
                            "fecha", FECHA.format(respuesta.getCreadoEn().atZone(LIMA)),
                            "pedido", r.getPedido(),
                            "respuesta", respuesta.getRespuesta()));
        });

        boolean enviado = true;
        String error = null;
        try {
            mailService.sendHtml(HtmlMailMessage.to(preparado.destino(), preparado.asunto(),
                    "mail/reclamacion-respuesta.html", preparado.campos()));
        } catch (MailDeliveryException e) {
            enviado = false;
            error = e.getMessage();
            log.warn("No se pudo enviar la respuesta de la reclamación {}", reclamacionId, e);
        }

        boolean resultado = enviado;
        String detalleError = error;
        tx.executeWithoutResult(estado -> {
            Instant ahora = clock.instant();
            RespuestaReclamacion respuesta = respuestas.findById(preparado.respuestaId()).orElseThrow();
            IntentoEnvioRespuesta intento = new IntentoEnvioRespuesta();
            intento.setRespuesta(respuesta);
            intento.setCorreoDestino(preparado.destino());
            intento.setResultado(resultado ? IntentoEnvioRespuesta.ENVIADO : IntentoEnvioRespuesta.ERROR);
            intento.setDetalleError(detalleError);
            intento.setRealizadoPorUsuarioId(adminId);
            intento.setRealizadoEn(ahora);
            intentos.save(intento);
            if (respuesta.getEnviadoEn() != null) {
                return; // Ya estaba enviada: un reenvío solo suma un intento; el caso y su fecha no cambian.
            }
            if (resultado) {
                respuesta.setEstadoEnvio(RespuestaReclamacion.ENVIADO);
                respuesta.setEnviadoEn(ahora);
                Reclamacion r = respuesta.getReclamacion();
                r.setEstado(Reclamacion.RESPONDIDO);
                r.setRespondidoEn(ahora);
            } else {
                respuesta.setEstadoEnvio(RespuestaReclamacion.ERROR);
            }
        });
    }

    // ----------------------------------------------------------------------------------------- utilidades --

    private ReclamacionDetalleResponse construirDetalle(Reclamacion r) {
        LocalDate hoy = clock.instant().atZone(LIMA).toLocalDate();
        int restantes = restantes(r, hoy);
        List<Adjunto> imagenes = adjuntos.findByReclamacion_IdOrderByIdAsc(r.getId()).stream()
                .map(a -> new Adjunto(a.getId(), a.getNombreArchivo(), a.getTipoMime(), a.getTamanoBytes())).toList();
        RespuestaReclamacion respuesta = respuestas.findByReclamacion_Id(r.getId()).orElse(null);
        List<Intento> historial = respuesta == null ? List.of()
                : intentos.findByRespuesta_IdOrderByRealizadoEnDescIdDesc(respuesta.getId()).stream()
                        .map(i -> new Intento(i.getId(), i.getCorreoDestino(), i.getResultado(), i.getDetalleError(),
                                nombre(i.getRealizadoPorUsuarioId()), i.getRealizadoEn()))
                        .toList();
        Respuesta dto = respuesta == null ? null
                : new Respuesta(respuesta.getRespuesta(), nombre(respuesta.getRespondidoPorUsuarioId()),
                        respuesta.getCreadoEn(), respuesta.getEnviadoEn(), respuesta.getEstadoEnvio(),
                        historial.isEmpty() ? r.getCorreoContacto() : historial.get(0).correoDestino());
        return new ReclamacionDetalleResponse(r.getId(), r.getCodigo(), r.getTipo(), r.getEstado(), plazo(r, hoy, restantes),
                restantes, r.getFechaLimiteRespuesta(), r.getRegistradoEn(), r.getRespondidoEn(), r.getNombres(),
                r.getApellidos(), r.getTipoDocumento(), r.getNumeroDocumento(), r.getCorreoContacto(),
                r.getTelefonoContacto(), r.getDireccion(), r.getMontoReclamado(), r.getDescripcionBien(), r.getDetalle(),
                r.getPedido(), r.getUsuario() != null, imagenes, dto, historial);
    }

    private int restantes(Reclamacion r, LocalDate hoy) {
        return Reclamacion.PENDIENTE_RESPUESTA.equals(r.getEstado())
                ? diasHabiles.diasHabilesHasta(hoy, r.getFechaLimiteRespuesta()) : 0;
    }

    private static String plazo(Reclamacion r, LocalDate hoy, int restantes) {
        if (!Reclamacion.PENDIENTE_RESPUESTA.equals(r.getEstado())) {
            return null;
        }
        if (hoy.isAfter(r.getFechaLimiteRespuesta())) {
            return "VENCIDO";
        }
        return restantes <= DIAS_POR_VENCER ? "POR_VENCER" : "EN_PLAZO";
    }

    private String nombre(Long usuarioId) {
        return usuarios.findWithPersonaById(usuarioId).map(u -> u.getPersona().nombreCompleto()).orElse("—");
    }

    private Reclamacion cargar(Long id) {
        return reclamaciones.findById(id).orElseThrow(() -> new ResourceNotFoundException("La queja o reclamo no existe."));
    }

    private void exigirAdministrador() {
        if (!actual.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private static String mayusculas(String valor) {
        return valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
    }
}
