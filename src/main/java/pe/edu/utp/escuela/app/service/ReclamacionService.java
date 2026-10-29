package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.escuela.app.dto.ArchivoGuardado;
import pe.edu.utp.escuela.app.dto.PresentarReclamacionRequest;
import pe.edu.utp.escuela.app.dto.ReclamacionConstanciaResponse;
import pe.edu.utp.escuela.app.dto.ReclamacionPrecargaResponse;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Reclamacion;
import pe.edu.utp.escuela.app.entity.ReclamacionAdjunto;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.ReclamacionAdjuntoRepository;
import pe.edu.utp.escuela.app.repository.ReclamacionRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;

/**
 * HU-039 — Libro de Reclamaciones: registra una queja o reclamo una sola vez, con número correlativo,
 * plazo de quince días hábiles y sus imágenes, y envía la constancia por correo. Un fallo del correo
 * no revierte ni duplica el caso: queda registrado y el expediente permanece.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReclamacionService {

    static final int PLAZO_DIAS_HABILES = 15;
    private static final int MAXIMO_IMAGENES = 10;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-PE"));
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm",
            Locale.forLanguageTag("es-PE"));

    private final ReclamacionRepository reclamaciones;
    private final ReclamacionAdjuntoRepository adjuntos;
    private final UsuarioRepository usuarios;
    private final ArchivoAlmacenamientoService archivos;
    private final DiasHabilesService diasHabiles;
    private final MailService mailService;
    private final PlatformTransactionManager transacciones;
    private final Clock clock;

    @Value("${esejur.reclamaciones.correo-escuela:informes@esejur.pe}")
    private String correoEscuela;

    /** Datos conocidos de la persona con sesión; ella puede revisarlos y corregirlos antes de enviar. */
    public ReclamacionPrecargaResponse precarga(Long usuarioId) {
        Usuario usuario = usuarios.findWithPersonaById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe."));
        Persona p = usuario.getPersona();
        String apellidos = (p.getApellidoPaterno() + " " + (p.getApellidoMaterno() == null ? "" : p.getApellidoMaterno())).strip();
        String documento = p.getDocumentoIdentidad() == null ? "" : p.getDocumentoIdentidad().strip();
        return new ReclamacionPrecargaResponse(p.getNombres(), apellidos, usuario.getCorreo(),
                p.getTelefono() == null ? "" : p.getTelefono(), "DNI", documento);
    }

    /** Registra el caso. {@code usuarioId} es nulo cuando quien reclama no inició sesión. */
    public ReclamacionConstanciaResponse presentar(Long usuarioId, PresentarReclamacionRequest p, List<MultipartFile> imagenes) {
        validarDocumento(p.tipoDocumento(), p.numeroDocumento().strip());
        List<MultipartFile> lista = imagenes == null ? List.of()
                : imagenes.stream().filter(i -> i != null && !i.isEmpty()).toList();
        if (lista.size() > MAXIMO_IMAGENES) {
            throw new BusinessValidationException("Puedes adjuntar hasta " + MAXIMO_IMAGENES + " imágenes.");
        }
        List<byte[]> contenidos = new ArrayList<>();
        for (MultipartFile imagen : lista) {
            contenidos.add(archivos.validarAdjuntoReclamacion(imagen));
        }

        Reclamacion creada = new TransactionTemplate(transacciones).execute(estado -> {
            Instant ahora = clock.instant();
            LocalDate hoy = ahora.atZone(LIMA).toLocalDate();
            Reclamacion r = new Reclamacion();
            r.setCodigo("TMP-" + UUID.randomUUID());
            r.setUsuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId));
            r.setTipo(p.tipo());
            r.setTipoDocumento(p.tipoDocumento());
            r.setNumeroDocumento(p.numeroDocumento().strip().toUpperCase(Locale.ROOT));
            r.setNombres(p.nombres().strip());
            r.setApellidos(p.apellidos().strip());
            r.setCorreoContacto(p.correo().strip().toLowerCase(Locale.ROOT));
            r.setTelefonoContacto(p.celular().strip());
            r.setDireccion(p.direccion().strip());
            r.setMontoReclamado(p.montoReclamado());
            r.setDescripcionBien(p.descripcionBien().strip());
            r.setDetalle(p.detalle().strip());
            r.setPedido(p.pedido().strip());
            r.setDeclaracionJurada(true);
            r.setAutorizaRespuestaCorreo(true);
            r.setEstado(Reclamacion.PENDIENTE_RESPUESTA);
            r.setFechaLimiteRespuesta(diasHabiles.sumarDiasHabiles(hoy, PLAZO_DIAS_HABILES));
            r.setRegistradoEn(ahora);
            reclamaciones.saveAndFlush(r);
            r.setCodigo(String.format("LR-%d-%06d", hoy.getYear(), r.getId()));
            for (int i = 0; i < lista.size(); i++) {
                ArchivoGuardado guardado = archivos.guardarAdjuntoReclamacion(r.getId(), lista.get(i), contenidos.get(i));
                ReclamacionAdjunto a = new ReclamacionAdjunto();
                a.setReclamacion(r);
                a.setNombreArchivo(guardado.nombreOriginal().length() > 255
                        ? guardado.nombreOriginal().substring(0, 255) : guardado.nombreOriginal());
                a.setReferencia(guardado.referencia());
                a.setTipoMime(guardado.tipoMime());
                a.setTamanoBytes(guardado.tamanoBytes());
                a.setCreadoEn(ahora);
                adjuntos.save(a);
            }
            return r;
        });

        // El caso ya existe: el correo se envía aparte y su fallo solo se registra.
        boolean enviada = enviarConstancia(creada, lista.size());
        enviarAvisoEscuela(creada, lista.size());
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Reclamacion r = reclamaciones.findById(creada.getId()).orElseThrow();
            r.setEstadoEnvioConstancia(enviada ? "ENVIADO" : "ERROR");
            r.setUltimoErrorConstancia(enviada ? null : creada.getUltimoErrorConstancia());
        });
        return constancia(creada, lista.stream().map(MultipartFile::getOriginalFilename).toList(), enviada);
    }

    // ------------------------------------------------------------------------------------------ correos --

    private boolean enviarConstancia(Reclamacion r, int imagenes) {
        try {
            mailService.sendHtml(HtmlMailMessage.to(r.getCorreoContacto(), "Constancia de tu " + minuscula(r.getTipo())
                    + " " + r.getCodigo(), "mail/reclamacion-constancia.html", campos(r, imagenes)));
            return true;
        } catch (MailDeliveryException e) {
            r.setUltimoErrorConstancia(e.getMessage());
            log.warn("No se pudo enviar la constancia de la reclamación {}", r.getCodigo(), e);
            return false;
        }
    }

    private void enviarAvisoEscuela(Reclamacion r, int imagenes) {
        try {
            mailService.sendHtml(HtmlMailMessage.to(correoEscuela, "Nueva " + minuscula(r.getTipo()) + " pendiente: "
                    + r.getCodigo(), "mail/reclamacion-aviso.html", campos(r, imagenes)));
        } catch (MailDeliveryException e) {
            log.warn("No se pudo avisar a la Escuela de la reclamación {}", r.getCodigo(), e);
        }
    }

    private Map<String, String> campos(Reclamacion r, int imagenes) {
        return Map.ofEntries(
                Map.entry("codigo", r.getCodigo()),
                Map.entry("tipo", r.getTipo().equals(Reclamacion.QUEJA) ? "Queja" : "Reclamo"),
                Map.entry("registrado", FECHA_HORA.format(r.getRegistradoEn().atZone(LIMA))),
                Map.entry("limite", FECHA.format(r.getFechaLimiteRespuesta())),
                Map.entry("nombre", r.getNombres() + " " + r.getApellidos()),
                Map.entry("documento", r.getTipoDocumento() + " " + r.getNumeroDocumento()),
                Map.entry("correo", r.getCorreoContacto()),
                Map.entry("celular", r.getTelefonoContacto()),
                Map.entry("direccion", r.getDireccion()),
                Map.entry("monto", "S/ " + r.getMontoReclamado().toPlainString()),
                Map.entry("bien", r.getDescripcionBien()),
                Map.entry("detalle", r.getDetalle()),
                Map.entry("pedido", r.getPedido()),
                Map.entry("adjuntos", imagenes == 0 ? "Sin imágenes" : imagenes + (imagenes == 1 ? " imagen" : " imágenes")));
    }

    // ---------------------------------------------------------------------------------------- validación --

    private static void validarDocumento(String tipo, String numero) {
        boolean valido = switch (tipo) {
            case "DNI" -> numero.matches("\\d{8}");
            case "RUC" -> numero.matches("\\d{11}");
            case "CE" -> numero.matches("[A-Za-z0-9]{9,12}");
            case "PASAPORTE" -> numero.matches("[A-Za-z0-9]{6,12}");
            default -> false;
        };
        if (!valido) {
            throw new BusinessValidationException(switch (tipo) {
                case "DNI" -> "El DNI debe tener 8 dígitos.";
                case "RUC" -> "El RUC debe tener 11 dígitos.";
                case "CE" -> "El carné de extranjería debe tener entre 9 y 12 caracteres.";
                default -> "El número de pasaporte debe tener entre 6 y 12 caracteres.";
            });
        }
    }

    private ReclamacionConstanciaResponse constancia(Reclamacion r, List<String> nombresImagenes, boolean enviada) {
        return new ReclamacionConstanciaResponse(r.getCodigo(), r.getTipo(), r.getEstado(), r.getRegistradoEn(),
                r.getFechaLimiteRespuesta(), r.getNombres(), r.getApellidos(), r.getTipoDocumento(),
                r.getNumeroDocumento(), r.getCorreoContacto(), r.getTelefonoContacto(), r.getDireccion(),
                r.getMontoReclamado(), r.getDescripcionBien(), r.getDetalle(), r.getPedido(),
                nombresImagenes.stream().map(n -> n == null ? "imagen" : n).toList(), enviada);
    }

    private static String minuscula(String valor) {
        return valor.toLowerCase(Locale.forLanguageTag("es-PE"));
    }
}
