package pe.edu.utp.escuela.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utp.escuela.app.dto.ConstanciaPagoResponse;
import pe.edu.utp.escuela.app.dto.PagoCheckoutResponse;
import pe.edu.utp.escuela.app.dto.PagoDetalleResponse;
import pe.edu.utp.escuela.app.dto.PagosCursoResponse;
import pe.edu.utp.escuela.app.entity.AtencionMatriculaPago;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.EventoPago;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Pago;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.exception.BusinessException;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.integration.CulqiIntegration;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.CheckoutCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.CobroConToken;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.ResultadoCulqi;
import pe.edu.utp.escuela.app.integration.CulqiIntegration.SolicitudCobro;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.AtencionMatriculaPagoRepository;
import pe.edu.utp.escuela.app.repository.CursoRepository;
import pe.edu.utp.escuela.app.repository.EventoPagoRepository;
import pe.edu.utp.escuela.app.repository.MatriculaRepository;
import pe.edu.utp.escuela.app.repository.NotificacionRepository;
import pe.edu.utp.escuela.app.repository.PagoRepository;
import pe.edu.utp.escuela.app.repository.UsuarioRepository;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialData;
import pe.edu.utp.escuela.app.service.CourseCommercialStatusService.CourseCommercialStatus;
import pe.edu.utp.escuela.app.service.MatriculaService.ActivacionPago;

/**
 * HU-047 — Matrícula pagada con Culqi. Culqi procesa el dinero y decide el resultado; ESEJUR solo inicia la
 * operación, registra lo que Culqi informa y aplica su consecuencia, sin revisión humana:
 * <ul>
 *   <li>Solo el primer APROBADO activa la matrícula y ocupa cupo; PENDIENTE y los resultados no aprobados no.</li>
 *   <li>Cada intento conserva el importe que el alumno aceptó; un reintento vuelve a pedir el precio vigente.</li>
 *   <li>Nunca hay dos intentos PENDIENTE a la vez: se reanuda el que existe.</li>
 *   <li>Toda confirmación se identifica por un evento único: repetirla no duplica pago, acceso, cupo ni constancia.</li>
 *   <li>Un APROBADO tardío se respeta salvo que el curso o la matrícula se hayan cancelado; entonces se registra una
 *       sola vez y queda para atención externa. Nunca se devuelve dinero automáticamente.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoEnLineaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PagoRepository pagos;
    private final MatriculaRepository matriculas;
    private final CursoRepository cursos;
    private final UsuarioRepository usuarios;
    private final EventoPagoRepository eventos;
    private final AtencionMatriculaPagoRepository atenciones;
    private final NotificacionRepository notificaciones;
    private final MatriculaService matriculaService;
    private final CourseCommercialStatusService comercial;
    private final CulqiIntegration culqi;
    private final MailService mailService;
    private final CurrentUserService actual;
    private final PlatformTransactionManager transacciones;
    private final Clock clock;

    @Value("${application.frontend-base-url}")
    private String frontendBaseUrl;

    // ================================================================================== iniciar el pago ==

    /**
     * Valida curso, cuenta, duplicidad, cierre y cupo; exige que el alumno confirme el importe vigente; conserva
     * el intento como PENDIENTE y prepara el checkout de Culqi. No reserva cupo.
     */
    @Transactional
    public PagoCheckoutResponse iniciar(Long cursoId, BigDecimal importeConfirmado) {
        Usuario usuario = alumnoHabilitado();
        Curso curso = cursos.bloquearParaMatricula(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("El curso no existe."));
        if (!"PAGADO".equals(curso.getTipoVenta())) {
            throw new BusinessValidationException("Este curso es gratuito: no requiere pago.");
        }
        Optional<Matricula> existente = matriculas.findByUsuario_IdAndCurso_Id(usuario.getId(), curso.getId());
        if (existente.isPresent()) {
            Matricula m = existente.get();
            if (!"PENDIENTE_PAGO".equals(m.getEstado())) {
                throw new DuplicateResourceException("Ya tienes una matrícula en este curso.");
            }
            Optional<Pago> enProceso = pagos.findFirstByMatricula_IdAndEstado(m.getId(), Pago.PENDIENTE);
            if (enProceso.isPresent()) {
                return checkoutDe(enProceso.get(), usuario, true);
            }
        }
        CourseCommercialStatus estado = comercial.calculate(datos(curso),
                matriculas.contarActivas(List.of(curso.getId())).getOrDefault(curso.getId(), 0L));
        if (!estado.enrollmentAllowed()) {
            throw new BusinessValidationException(estado.label() + ": por ahora no se puede iniciar el pago.");
        }
        BigDecimal vigente = estado.currentPrice().setScale(2, RoundingMode.HALF_UP);
        if (importeConfirmado.setScale(2, RoundingMode.HALF_UP).compareTo(vigente) != 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRICE_CHANGED",
                    "El precio vigente es S/ " + vigente.toPlainString() + ". Confírmalo para continuar.");
        }

        Matricula matricula = existente.orElseGet(() -> matriculaService.crearPendientePago(usuario, curso));
        Pago pago = new Pago();
        pago.setMatricula(matricula);
        pago.setNumeroPedido("TMP-" + java.util.UUID.randomUUID());
        pago.setOrigen("EN_LINEA");
        pago.setEstado(Pago.PENDIENTE);
        pago.setMoneda("PEN");
        pago.setPrecioRegularAplicado(curso.getPrecioRegular());
        pago.setPrecioPromocionalAplicado(estado.promotionActive() ? curso.getPrecioPromocional() : null);
        pago.setImporte(vigente);
        pago.setCondicionesValidasAlIniciar(true);
        pago.setIniciadoEn(clock.instant());
        pagos.saveAndFlush(pago);
        pago.setNumeroPedido(String.format("PED-%d-%06d", LocalDate.now(clock.withZone(LIMA)).getYear(), pago.getId()));

        Persona p = usuario.getPersona();
        CheckoutCulqi checkout = culqi.prepararCheckout(new SolicitudCobro(pago.getNumeroPedido(), centimos(vigente),
                "PEN", descripcion(curso), usuario.getCorreo(), p.getNombres(), apellidos(p), telefono(p)));
        pago.setReferenciaExterna(checkout.ordenId());
        pagos.save(pago);
        return checkoutDe(pago, usuario, false);
    }

    private PagoCheckoutResponse checkoutDe(Pago pago, Usuario usuario, boolean reanudado) {
        Persona p = usuario.getPersona();
        return new PagoCheckoutResponse(pago.getNumeroPedido(), pago.getImporte(), pago.getMoneda(), reanudado,
                culqi.esSimulado() ? "SIMULADO" : "REAL", culqi.llavePublica(), pago.getReferenciaExterna(),
                centimos(pago.getImporte()), "Escuela Jurídica", usuario.getCorreo(), p.getNombres(), apellidos(p));
    }

    // ================================================================================== cobrar el token ==

    /** Cobra el token que entregó el checkout (tarjeta o Yape) y aplica el resultado que Culqi comunique. */
    public PagoDetalleResponse procesarToken(String numeroPedido, String tokenId) {
        CobroConToken cobro = new TransactionTemplate(transacciones).execute(estado -> {
            Pago pago = propio(numeroPedido);
            if (!Pago.PENDIENTE.equals(pago.getEstado())) {
                return null; // Ya tiene resultado: la petición repetida solo devuelve el estado actual.
            }
            Usuario usuario = pago.getMatricula().getUsuario();
            Persona p = usuario.getPersona();
            return new CobroConToken(tokenId, pago.getNumeroPedido(), centimos(pago.getImporte()), pago.getMoneda(),
                    usuario.getCorreo(), descripcion(pago.getMatricula().getCurso()), p.getNombres(), apellidos(p),
                    telefono(p));
        });
        if (cobro != null) {
            ResultadoCulqi r = culqi.cobrar(cobro); // El dinero lo procesa Culqi, fuera de nuestra transacción.
            String evento = r.cargoId() != null ? "cargo:" + r.cargoId() + ":" + r.estado()
                    : "token:" + tokenId + ":" + r.estado();
            aplicar(numeroPedido, r, evento);
        }
        return detalle(numeroPedido);
    }

    // ============================================================================ aplicar un resultado ==

    /**
     * Aplica un resultado de Culqi una sola vez. {@code eventoId} identifica la confirmación: si ya se aplicó,
     * no ocurre nada. Es el único camino por el que un pago cambia de estado (token, webhook, consulta o simulación).
     */
    public void aplicar(String numeroPedido, ResultadoCulqi r, String eventoId) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Pago pago = pagos.findByNumeroPedido(numeroPedido)
                    .orElseThrow(() -> new ResourceNotFoundException("El pago no existe."));
            pago = pagos.bloquear(pago.getId()).orElseThrow();
            if (eventos.existsByIdentificadorEvento(eventoId)) {
                return; // Confirmación repetida: ningún efecto.
            }
            Instant ahora = clock.instant();
            EventoPago evento = new EventoPago();
            evento.setPago(pago);
            evento.setIdentificadorEvento(eventoId);
            evento.setEstadoInformado(r.estado());
            evento.setContenido("{\"estado\":\"" + r.estado() + "\",\"codigo\":\"" + limpiarJson(r.codigo())
                    + "\",\"medio\":\"" + limpiarJson(r.medio()) + "\"}");
            evento.setRecibidoEn(ahora);
            evento.setAplicadoEn(ahora);
            eventos.save(evento);

            String nuevo = r.estado();
            if (pago.esAprobado()) {
                if (!Pago.APROBADO.equals(nuevo)) {
                    marcarAtencion(pago, AtencionMatriculaPago.PAGO_TARDIO, "Culqi informó " + nuevo
                            + " después de APROBADO; el resultado aprobado no se modificó.");
                }
                return;
            }
            if (pago.esNoAprobadoFinal()) {
                if (Pago.APROBADO.equals(nuevo)) {
                    marcarAtencion(pago, AtencionMatriculaPago.PAGO_TARDIO, "Culqi informó APROBADO después de haber "
                            + "comunicado " + pago.getEstado() + " para este intento. El resultado anterior se conserva "
                            + "y la Escuela debe revisarlo con Culqi.");
                }
                return;
            }
            copiarDatos(pago, r);
            if (Pago.PENDIENTE.equals(nuevo)) {
                pagos.save(pago);
                return;
            }
            pago.setEstado(nuevo);
            pago.setResultadoEn(r.fecha() != null ? r.fecha() : ahora);
            pago.setMotivo(Pago.APROBADO.equals(nuevo) ? null : motivoNoAprobado(r));
            if (Pago.APROBADO.equals(nuevo)) {
                pago.setConstanciaNumero(String.format("CONST-%d-%06d", LocalDate.now(clock.withZone(LIMA)).getYear(),
                        pago.getId()));
                pagos.saveAndFlush(pago);
                ActivacionPago activacion = matriculaService.activarPorPagoAprobado(pago.getMatricula());
                if (activacion.atencion() != null) {
                    marcarAtencion(pago, activacion.atencion(), "Culqi aprobó el pago, pero "
                            + (AtencionMatriculaPago.CURSO_CANCELADO.equals(activacion.atencion())
                                    ? "el curso fue cancelado" : "la matrícula fue cancelada")
                            + " antes del resultado. Se registró el pago una sola vez, no se concedió acceso y "
                            + "la Escuela debe resolver el caso fuera de ESEJUR.");
                }
                if (activacion.sobrecupo()) {
                    marcarAtencion(pago, AtencionMatriculaPago.SOBRECUPO, "El pago se aprobó con el cupo ya completo. "
                            + "Se concedió el acceso y se alerta el sobrecupo.");
                }
                if (activacion.accesoConcedido()) {
                    enviarAviso(pago, true);
                }
            } else {
                pagos.save(pago);
                enviarAviso(pago, false);
            }
        });
    }

    private void copiarDatos(Pago pago, ResultadoCulqi r) {
        if (r.medio() != null) pago.setMedio(r.medio());
        if (r.ultimosDigitos() != null) pago.setUltimosDigitos(r.ultimosDigitos());
        if (r.cuotas() != null) pago.setNumeroCuotas(r.cuotas());
        if (r.cargoId() != null && pago.getOperacionProveedor() == null
                && pagos.findByOperacionProveedor(r.cargoId()).isEmpty()) {
            pago.setOperacionProveedor(r.cargoId());
        }
    }

    private static String motivoNoAprobado(ResultadoCulqi r) {
        String texto = r.mensajeUsuario() == null ? "" : r.mensajeUsuario();
        return r.codigo() == null ? texto : (texto + " (" + r.codigo() + ")").strip();
    }

    private void marcarAtencion(Pago pago, String tipo, String descripcion) {
        pago.setRequiereAtencion(true);
        pago.setDetalleAtencion(descripcion);
        pagos.save(pago);
        AtencionMatriculaPago a = new AtencionMatriculaPago();
        a.setMatricula(pago.getMatricula());
        a.setPago(pago);
        a.setTipo(tipo);
        a.setDescripcion(descripcion);
        a.setCreadoEn(clock.instant());
        atenciones.save(a);
    }

    // ================================================================================== correos ==

    /** Confirmación (aprobado) o aviso de que la matrícula no se activó (no aprobado). Su fallo nunca altera el pago. */
    private boolean enviarAviso(Pago pago, boolean aprobado) {
        Matricula m = pago.getMatricula();
        Usuario u = m.getUsuario();
        String tipo = "PAGO_" + pago.getId() + "_AVISO";
        Notificacion n = notificaciones.findTopByTipoOrderByCreadoEnDesc(tipo).orElseGet(() -> {
            Notificacion nueva = new Notificacion();
            nueva.setUsuario(u);
            nueva.setTipo(tipo);
            nueva.setDestinatario(u.getCorreo());
            nueva.setAsunto(aprobado ? "Pago aprobado: " + m.getCurso().getTitulo()
                    : "Tu pago no se completó: " + m.getCurso().getTitulo());
            return nueva;
        });
        n.setIntentosEnvio(n.getIntentosEnvio() + 1);
        try {
            Map<String, String> campos = aprobado
                    ? Map.of("nombre", u.getPersona().getNombres(), "curso", m.getCurso().getTitulo(),
                            "importe", "S/ " + pago.getImporte().toPlainString(),
                            "medio", etiquetaMedio(pago), "referencia", pago.getNumeroPedido(),
                            "fechaAcceso", fechaAcceso(m), "enlace", frontendBaseUrl + "/app/panel")
                    : Map.of("nombre", u.getPersona().getNombres(), "curso", m.getCurso().getTitulo(),
                            "importe", "S/ " + pago.getImporte().toPlainString(),
                            "motivo", pago.getMotivo() == null ? "Culqi no aprobó la operación." : pago.getMotivo(),
                            "referencia", pago.getNumeroPedido(),
                            "enlace", frontendBaseUrl + "/cursos/" + m.getCurso().getUrlAmigable());
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                    aprobado ? "mail/pago-aprobado.html" : "mail/pago-no-aprobado.html", campos));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
            n.setUltimoError(null);
        } catch (MailDeliveryException e) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(e.getMessage());
        }
        notificaciones.save(n);
        return "ENVIADO".equals(n.getEstadoEnvio());
    }

    /** Reenvía el aviso del resultado sin crear otro pago, constancia ni acceso. */
    @Transactional
    public PagoDetalleResponse reenviarAviso(String numeroPedido) {
        Pago pago = propio(numeroPedido);
        if (Pago.PENDIENTE.equals(pago.getEstado())) {
            throw new BusinessValidationException("Culqi todavía no comunica un resultado: aún no hay aviso que reenviar.");
        }
        boolean aprobado = pago.esAprobado();
        if (aprobado && !"ACTIVA".equals(pago.getMatricula().getEstado())) {
            throw new BusinessValidationException("Este pago no activó una matrícula: no hay confirmación que reenviar.");
        }
        enviarAviso(pago, aprobado);
        return mapear(pago);
    }

    // ================================================================================ consultas ==

    /**
     * El intento tal como lo ve el alumno. Si sigue PENDIENTE y existe una orden en Culqi, se consulta su estado:
     * así un pago diferido se refleja aunque el webhook no haya podido llegar.
     */
    public PagoDetalleResponse detalle(String numeroPedido) {
        String orden = new TransactionTemplate(transacciones, soloLectura()).execute(estado -> {
            Pago pago = propio(numeroPedido);
            return Pago.PENDIENTE.equals(pago.getEstado()) && !culqi.esSimulado() ? pago.getReferenciaExterna() : null;
        });
        if (orden != null) {
            ResultadoCulqi r = culqi.consultarOrden(orden);
            if (r != null && !Pago.PENDIENTE.equals(r.estado())) {
                aplicar(numeroPedido, r, "consulta:" + orden + ":" + r.estado());
            }
        }
        return new TransactionTemplate(transacciones, soloLectura()).execute(estado -> mapear(propio(numeroPedido)));
    }

    @Transactional(readOnly = true)
    public ConstanciaPagoResponse constancia(String numeroPedido) {
        Pago pago = propio(numeroPedido);
        if (!pago.esAprobado() || pago.getConstanciaNumero() == null) {
            throw new BusinessValidationException(
                    "La constancia solo existe para un pago aprobado por Culqi. Este intento está " + pago.getEstado() + ".");
        }
        Matricula m = pago.getMatricula();
        return new ConstanciaPagoResponse(pago.getConstanciaNumero(), pago.getNumeroPedido(), pago.getImporte(),
                pago.getMoneda(), etiquetaMedio(pago), pago.getUltimosDigitos(), pago.getResultadoEn(),
                m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(), m.getCurso().getTitulo(),
                m.getCurso().getId());
    }

    @Transactional(readOnly = true)
    public PagosCursoResponse pagosDelCurso(Long cursoId) {
        Usuario usuario = usuarios.findWithPersonaById(actual.get().userId())
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe."));
        Optional<Matricula> m = matriculas.findByUsuario_IdAndCurso_Id(usuario.getId(), cursoId);
        if (m.isEmpty()) {
            return new PagosCursoResponse(null, null, List.of());
        }
        List<Pago> intentos = pagos.findByMatricula_IdOrderByIniciadoEnDescIdDesc(m.get().getId());
        String pendiente = intentos.stream().filter(p -> Pago.PENDIENTE.equals(p.getEstado()))
                .map(Pago::getNumeroPedido).findFirst().orElse(null);
        return new PagosCursoResponse(m.get().getEstado(), pendiente, intentos.stream().map(this::mapear).toList());
    }

    // ================================================================================== simulación ==

    /**
     * Solo con Culqi simulado: reproduce el aviso que Culqi enviaría para este intento. {@code repetirUltimo}
     * reenvía el mismo evento. El propietario o un administrador pueden usarlo (el administrador, para mostrar
     * aprobaciones tardías o contradictorias).
     */
    public PagoDetalleResponse simular(String numeroPedido, String resultado, boolean repetirUltimo) {
        if (!culqi.esSimulado()) {
            throw new BusinessValidationException("La simulación solo está disponible con Culqi simulado.");
        }
        String evento = new TransactionTemplate(transacciones).execute(estado -> {
            Pago pago = propio(numeroPedido);
            if (repetirUltimo) {
                return eventos.findFirstByPago_IdOrderByIdDesc(pago.getId()).map(EventoPago::getIdentificadorEvento)
                        .orElseThrow(() -> new BusinessValidationException("Todavía no hay una confirmación que repetir."));
            }
            return "sim:" + pago.getNumeroPedido() + ":" + resultado + ":" + System.nanoTime();
        });
        Pago pago = new TransactionTemplate(transacciones, soloLectura()).execute(estado -> propio(numeroPedido));
        String estadoEvento = repetirUltimo
                ? new TransactionTemplate(transacciones, soloLectura()).execute(estado -> eventos.findFirstByPago_IdOrderByIdDesc(pago.getId())
                        .map(EventoPago::getEstadoInformado).orElse(resultado))
                : resultado;
        String medio = pago.getMedio() != null ? pago.getMedio() : "BILLETERA";
        String codigo = switch (estadoEvento) {
            case "APROBADO" -> "venta_exitosa";
            case "RECHAZADO" -> "card_declined";
            case "EXPIRADO" -> "expired";
            default -> "processing_error";
        };
        String mensaje = switch (estadoEvento) {
            case "APROBADO" -> "Su compra ha sido exitosa.";
            case "RECHAZADO" -> "Culqi rechazó la operación.";
            case "EXPIRADO" -> "La operación expiró sin ser aprobada.";
            default -> "La operación no pudo completarse.";
        };
        ResultadoCulqi r = new ResultadoCulqi(estadoEvento, "sim_chr_" + numeroPedido, pago.getReferenciaExterna(), medio,
                null, null, codigo, mensaje, numeroPedido, clock.instant());
        aplicar(numeroPedido, r, evento);
        return detalle(numeroPedido);
    }

    // ==================================================================================== webhook ==

    /**
     * Aviso de Culqi (no firmado): se vuelve a consultar el recurso a la API antes de confiar en él. Un aviso de un
     * recurso que no es nuestro, o que no se puede verificar, se ignora.
     */
    public void procesarEventoCulqi(String eventoId, String tipo, String recursoId) {
        if (eventoId == null || tipo == null || recursoId == null) {
            return;
        }
        ResultadoCulqi r;
        Optional<Pago> pago;
        if (tipo.startsWith("order.")) {
            r = culqi.consultarOrden(recursoId);
            pago = new TransactionTemplate(transacciones, soloLectura()).execute(estado -> pagos.findByReferenciaExterna(recursoId));
        } else if (tipo.startsWith("charge.")) {
            r = culqi.consultarCargo(recursoId);
            pago = new TransactionTemplate(transacciones, soloLectura()).execute(estado -> pagos.findByOperacionProveedor(recursoId)
                    .or(() -> r == null || r.numeroPedido() == null ? Optional.empty() : pagos.findByNumeroPedido(r.numeroPedido())));
        } else {
            return;
        }
        if (r == null || pago == null || pago.isEmpty()) {
            log.info("Evento {} de Culqi ignorado: recurso {} no verificado o desconocido", tipo, recursoId);
            return;
        }
        aplicar(pago.get().getNumeroPedido(), r, "evt:" + eventoId);
    }

    // ==================================================================================== utilidades ==

    private PagoDetalleResponse mapear(Pago p) {
        Matricula m = p.getMatricula();
        boolean acceso = p.esAprobado() && "ACTIVA".equals(m.getEstado());
        String correo = notificaciones.findTopByTipoOrderByCreadoEnDesc("PAGO_" + p.getId() + "_AVISO")
                .map(Notificacion::getEstadoEnvio).orElse(null);
        return new PagoDetalleResponse(p.getNumeroPedido(), m.getCurso().getId(), m.getCurso().getTitulo(),
                m.getCurso().getUrlAmigable(), p.getEstado(), etiquetaMedioONulo(p), p.getUltimosDigitos(),
                p.getNumeroCuotas(), p.getImporte(), p.getMoneda(), p.getPrecioRegularAplicado(),
                p.getPrecioPromocionalAplicado(), mensaje(p, m), p.getIniciadoEn(), p.getResultadoEn(),
                p.esAprobado() ? p.getConstanciaNumero() : null, m.getEstado(), acceso,
                p.esNoAprobadoFinal() && "PENDIENTE_PAGO".equals(m.getEstado()), culqi.esSimulado(), correo);
    }

    private static String mensaje(Pago p, Matricula m) {
        return switch (p.getEstado()) {
            case Pago.APROBADO -> "ACTIVA".equals(m.getEstado())
                    ? "Culqi aprobó tu pago. Tu matrícula está activa."
                    : "Culqi aprobó el pago, pero la matrícula está " + m.getEstado().toLowerCase(Locale.ROOT)
                            + ". No se concedió acceso; la Escuela atenderá tu caso.";
            case Pago.RECHAZADO -> "Culqi rechazó el pago" + motivoVisible(p) + ". Tu matrícula no se activó.";
            case Pago.ERROR -> "La operación no pudo completarse" + motivoVisible(p) + ". Tu matrícula no se activó.";
            case Pago.EXPIRADO -> "La operación expiró sin ser aprobada. Tu matrícula no se activó.";
            default -> "Culqi todavía no comunica el resultado final. No se ha concedido acceso ni reservado cupo.";
        };
    }

    private static String motivoVisible(Pago p) {
        return p.getMotivo() == null || p.getMotivo().isBlank() ? "" : ": " + p.getMotivo();
    }

    private String etiquetaMedio(Pago p) {
        String medio = etiquetaMedioONulo(p);
        return medio == null ? "Culqi" : medio;
    }

    private static String etiquetaMedioONulo(Pago p) {
        if (p.getMedio() == null) return null;
        return switch (p.getMedio()) {
            case "TARJETA" -> p.getUltimosDigitos() == null ? "Tarjeta" : "Tarjeta terminada en " + p.getUltimosDigitos();
            case "YAPE" -> "Yape";
            case "BILLETERA" -> "Billetera móvil (Plin u otra)";
            default -> p.getMedio();
        };
    }

    private static String fechaAcceso(Matricula m) {
        LocalDate inicio = m.getCurso().getFechaInicio();
        return inicio != null && inicio.isAfter(LocalDate.now(ZoneId.of("America/Lima")))
                ? "Desde el " + inicio.format(FECHA) : "Inmediato, al iniciar sesión";
    }

    /** El intento de la sesión actual (o cualquiera, si es administrador). */
    private Pago propio(String numeroPedido) {
        Pago pago = pagos.findByNumeroPedido(numeroPedido)
                .orElseThrow(() -> new ResourceNotFoundException("El pago no existe."));
        boolean dueno = pago.getMatricula().getUsuario().getId().equals(actual.get().userId());
        if (!dueno && !actual.get().hasRole("ADMINISTRADOR")) {
            throw new ResourceNotFoundException("El pago no existe.");
        }
        return pago;
    }

    private Usuario alumnoHabilitado() {
        Usuario u = usuarios.findWithPersonaById(actual.get().userId())
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe."));
        if (!u.isActivo() || u.getCorreoVerificadoEn() == null || u.isRequiereCambioContrasena()) {
            throw new BusinessValidationException("Completa la habilitación de tu cuenta para pagar un curso.");
        }
        if (!actual.get().hasRole("ALUMNO")) {
            throw new ForbiddenException();
        }
        return u;
    }

    private static CourseCommercialData datos(Curso c) {
        return new CourseCommercialData(c.getModalidad(), c.getTipoVenta(), c.getEstadoCurso().getCodigo(),
                c.getPrecioRegular(), c.getPrecioPromocional(), c.getPromocionInicioEn(), c.getPromocionFinEn(),
                c.getFechaInicio(), c.getFechaCierreMatricula(), c.getCupoMaximo());
    }

    private static long centimos(BigDecimal importe) {
        return importe.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String descripcion(Curso curso) {
        String titulo = curso.getTitulo();
        return titulo.length() > 80 ? titulo.substring(0, 80) : titulo;
    }

    private static String apellidos(Persona p) {
        return (p.getApellidoPaterno() + " " + (p.getApellidoMaterno() == null ? "" : p.getApellidoMaterno())).strip();
    }

    private static String telefono(Persona p) {
        String digitos = p.getTelefono() == null ? "" : p.getTelefono().replaceAll("\\D", "");
        return digitos.length() >= 9 ? digitos : "999999999";
    }

    private static String limpiarJson(String valor) {
        return valor == null ? "" : valor.replace("\"", "'").replace("\\", "/");
    }

    private static org.springframework.transaction.support.DefaultTransactionDefinition soloLectura() {
        var d = new org.springframework.transaction.support.DefaultTransactionDefinition();
        d.setReadOnly(true);
        return d;
    }
}
