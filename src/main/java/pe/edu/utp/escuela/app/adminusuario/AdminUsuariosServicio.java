package pe.edu.utp.escuela.app.adminusuario;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilPeticion;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.entity.CodigoVerificacionCorreo;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Rol;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.entity.UsuarioRol;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.MailDeliveryException;
import pe.edu.utp.escuela.app.exception.OperationNotAllowedException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CodigoVerificacionRepositorio;
import pe.edu.utp.escuela.app.repository.NotificacionRepositorio;
import pe.edu.utp.escuela.app.repository.PersonaRepositorio;
import pe.edu.utp.escuela.app.repository.RolRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.util.TextNormalizer;

/** HU-008 — Gestionar usuarios administrativamente. */
@Service
@RequiredArgsConstructor
public class AdminUsuariosServicio {

    private static final String CONTRASENA_TEMPORAL = "Escuela1415@";

    private final UsuarioRepositorio usuarios;
    private final PersonaRepositorio personas;
    private final RolRepositorio roles;
    private final UsuarioRolRepositorio usuarioRoles;
    private final CodigoVerificacionRepositorio codigos;
    private final NotificacionRepositorio notificaciones;
    private final PasswordEncoder encoder;
    private final MailService mailService;
    private final TextNormalizer textos;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public PageResponse<UsuarioAdminRespuesta> listar(
            String texto, Boolean activo, RolUsuarioAdmin rol, Pageable pageable) {
        exigirAdministrador();
        String termino = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        String rolCodigo = rol == null ? "" : rol.codigo();
        Page<Usuario> pagina = usuarios.buscarAdministrativos(termino, activo, rolCodigo, pageable);

        List<Long> ids = pagina.getContent().stream().map(Usuario::getId).toList();
        Map<Long, List<UsuarioRolFila>> rolesPorUsuario = ids.isEmpty() ? Map.of()
                : usuarioRoles.buscarPorUsuarios(ids).stream()
                        .collect(Collectors.groupingBy(UsuarioRolFila::usuarioId));

        List<UsuarioAdminRespuesta> items = pagina.getContent().stream()
                .map(usuario -> mapear(usuario, rolesPorUsuario.getOrDefault(usuario.getId(), List.of()), null))
                .toList();
        return PageResponse.from(items, pagina);
    }

    @Transactional(readOnly = true)
    public UsuarioAdminRespuesta obtener(Long usuarioId) {
        exigirAdministrador();
        return detalleDe(buscarOLanzar(usuarioId));
    }

    /**
     * Pedido explícitamente por el usuario del proyecto tras confirmar que la historia sí
     * contempla editar los datos personales desde este panel (no solo roles y habilitación).
     * Reutiliza el mismo DTO y las mismas reglas que HU-005 (mi perfil), aplicadas aquí sobre
     * una cuenta ajena.
     */
    @Transactional
    public UsuarioAdminRespuesta actualizarDatosPersonales(Long usuarioId, ActualizarPerfilPeticion p) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);
        Persona persona = usuario.getPersona();

        String documento = textos.trimToNull(p.documentoIdentidad());
        if (documento != null && !documento.equals(persona.getDocumentoIdentidad())
                && personas.existsByDocumentoIdentidadAndIdNot(documento, persona.getId())) {
            throw new DuplicateResourceException("El documento ya se encuentra registrado.");
        }

        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        persona.setTelefono(textos.trimToNull(p.telefono()));
        persona.setDocumentoIdentidad(documento);

        return detalleDe(usuario);
    }

    @Transactional
    public CrearUsuarioAdminRespuesta crear(CrearUsuarioAdminPeticion p) {
        exigirAdministrador();
        String correo = textos.normalizeEmail(p.correo());

        Optional<Usuario> existente = usuarios.findByCorreoIgnoreCase(correo);
        if (existente.isPresent()) {
            Usuario usuario = existente.get();
            concederRolSiFalta(usuario, p.rol());
            return new CrearUsuarioAdminRespuesta(detalleDe(usuario), true, null);
        }

        String documento = textos.trimToNull(p.documentoIdentidad());
        if (documento != null && personas.existsByDocumentoIdentidad(documento)) {
            throw new DuplicateResourceException("El documento ya se encuentra registrado.");
        }

        Persona persona = new Persona();
        persona.setNombres(textos.requireText(p.nombres(), "Nombres"));
        persona.setApellidoPaterno(textos.requireText(p.apellidoPaterno(), "Apellido paterno"));
        persona.setApellidoMaterno(textos.trimToNull(p.apellidoMaterno()));
        persona.setTelefono(textos.trimToNull(p.telefono()));
        persona.setDocumentoIdentidad(documento);
        personas.saveAndFlush(persona);

        Long adminActualId = currentUserService.get().userId();
        Usuario usuario = new Usuario();
        usuario.setPersona(persona);
        usuario.setCorreo(correo);
        usuario.setOrigenRegistro("ADMINISTRATIVO");
        usuario.setActivo(true);
        usuario.setRequiereCambioContrasena(true);
        usuario.setContrasenaHash(encoder.encode(CONTRASENA_TEMPORAL));
        usuario.setCreadoPorUsuarioId(adminActualId);
        usuarios.saveAndFlush(usuario);

        asignarRol(usuario, p.rol(), adminActualId);

        String codigo = generarCodigo(usuario);
        enviarBienvenida(usuario, CONTRASENA_TEMPORAL, codigo);

        return new CrearUsuarioAdminRespuesta(detalleDe(usuario), false, CONTRASENA_TEMPORAL);
    }

    @Transactional
    public UsuarioAdminRespuesta concederRol(Long usuarioId, ConcederRolPeticion p) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);
        concederRolSiFalta(usuario, p.rol());
        return detalleDe(usuario);
    }

    /**
     * Desviación explícita y deliberada de HU-008: la historia dice literalmente "en esta
     * versión no se elimina roles" — esto va contra esa regla y solo existe porque se pidió
     * expresamente. Mantiene las mismas protecciones que {@code cambiarActivo}: no puedes
     * retirarte tu propio rol de administrador, no puedes dejar al sistema sin al menos un
     * administrador activo, y nunca puedes dejar una cuenta sin ningún rol.
     */
    @Transactional
    public UsuarioAdminRespuesta revocarRol(Long usuarioId, RolUsuarioAdmin rolSolicitado) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);
        Rol rol = rolPorCodigo(rolSolicitado);
        List<UsuarioRolFila> filas = usuarioRoles.buscarPorUsuario(usuarioId);

        UsuarioRolFila fila = filas.stream()
                .filter(f -> f.rolCodigo().equals(rol.getCodigo()))
                .findFirst()
                .orElse(null);
        if (fila == null) {
            return detalleDe(usuario);
        }
        if (filas.size() <= 1) {
            throw new OperationNotAllowedException("No puedes retirar el único rol de la cuenta.");
        }
        if (rolSolicitado == RolUsuarioAdmin.ADMINISTRADOR) {
            if (usuario.getId().equals(currentUserService.get().userId())) {
                throw new OperationNotAllowedException("No puedes retirarte el rol de administrador a ti mismo.");
            }
            if (usuario.isActivo() && usuarioRoles.contarActivosConRol(RolUsuarioAdmin.ADMINISTRADOR.codigo()) <= 1) {
                throw new OperationNotAllowedException(
                        "No puedes retirar el rol al último administrador habilitado.");
            }
        }

        usuarioRoles.deleteById(new UsuarioRol.Clave(usuarioId, rol.getId()));

        if (fila.principal()) {
            UsuarioRolFila restante = filas.stream()
                    .filter(f -> !f.rolCodigo().equals(rol.getCodigo()))
                    .findFirst()
                    .orElseThrow();
            Rol rolRestante = rolPorCodigo(RolUsuarioAdmin.desdeCodigo(restante.rolCodigo()));
            UsuarioRol asignacionRestante = usuarioRoles
                    .findById(new UsuarioRol.Clave(usuarioId, rolRestante.getId()))
                    .orElseThrow();
            asignacionRestante.setPrincipal(true);
        }

        return detalleDe(usuario);
    }

    @Transactional
    public UsuarioAdminRespuesta cambiarActivo(Long usuarioId, CambiarActivoPeticion p) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);

        if (!p.activo()) {
            if (usuario.getId().equals(currentUserService.get().userId())) {
                throw new OperationNotAllowedException("No puedes desactivar tu propia cuenta.");
            }
            boolean esAdministrador = usuarioRoles.buscarPorUsuario(usuario.getId()).stream()
                    .anyMatch(fila -> fila.rolCodigo().equals(RolUsuarioAdmin.ADMINISTRADOR.codigo()));
            if (esAdministrador && usuario.isActivo()
                    && usuarioRoles.contarActivosConRol(RolUsuarioAdmin.ADMINISTRADOR.codigo()) <= 1) {
                throw new OperationNotAllowedException(
                        "No puedes desactivar al último administrador habilitado.");
            }
        }

        usuario.setActivo(p.activo());
        usuario.setDeshabilitadoEn(p.activo() ? null : clock.instant());
        return detalleDe(usuario);
    }

    @Transactional
    public boolean reenviarHabilitacion(Long usuarioId) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);
        if (usuario.getCorreoVerificadoEn() != null) {
            return false;
        }
        String codigo = generarCodigo(usuario);
        try {
            mailService.sendHtml(HtmlMailMessage.to(usuario.getCorreo(),
                    "Bienvenido a ESEJUR: verifica tu correo", "mail/verification-code.html",
                    Map.of("nombre", usuario.getPersona().getNombres(), "codigo", codigo)));
            return true;
        } catch (MailDeliveryException exception) {
            return false;
        }
    }

    private void exigirAdministrador() {
        if (!currentUserService.get().hasRole("ADMINISTRADOR")) {
            throw new ForbiddenException();
        }
    }

    private Usuario buscarOLanzar(Long usuarioId) {
        return usuarios.findWithPersonaById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta ya no existe."));
    }

    /** Concede el rol solo si todavía no lo tiene; conceder el mismo rol de nuevo no duplica la
     * asignación ni cambia el rol principal. */
    private void concederRolSiFalta(Usuario usuario, RolUsuarioAdmin rolSolicitado) {
        Rol rol = rolPorCodigo(rolSolicitado);
        boolean yaLoTiene = usuarioRoles.buscarPorUsuario(usuario.getId()).stream()
                .anyMatch(fila -> fila.rolCodigo().equals(rol.getCodigo()));
        if (yaLoTiene) {
            return;
        }
        asignarRol(usuario, rolSolicitado, currentUserService.get().userId());
    }

    /** El primer rol que recibe la cuenta queda como principal; cualquier otro se agrega como
     * secundario sin tocar el principal ya existente. */
    private void asignarRol(Usuario usuario, RolUsuarioAdmin rolSolicitado, Long otorganteId) {
        Rol rol = rolPorCodigo(rolSolicitado);
        boolean tieneAlgunRol = !usuarioRoles.buscarPorUsuario(usuario.getId()).isEmpty();

        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setId(new UsuarioRol.Clave(usuario.getId(), rol.getId()));
        asignacion.setPrincipal(!tieneAlgunRol);
        asignacion.setAsignadoPorUsuarioId(otorganteId);
        asignacion.setAsignadoEn(clock.instant());
        usuarioRoles.saveAndFlush(asignacion);
    }

    private Rol rolPorCodigo(RolUsuarioAdmin rol) {
        return roles.findByCodigoAndActivoTrue(rol.codigo())
                .orElseThrow(() -> new IllegalStateException("Falta " + rol.codigo() + " activo"));
    }

    private String generarCodigo(Usuario usuario) {
        codigos.invalidarAnteriores(usuario.getId(), clock.instant());
        String visible = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        CodigoVerificacionCorreo codigo = new CodigoVerificacionCorreo();
        codigo.setUsuario(usuario);
        codigo.setCodigoHash(encoder.encode(visible));
        codigo.setSolicitadoEn(clock.instant());
        codigo.setModificadoEn(clock.instant());
        codigos.saveAndFlush(codigo);
        return visible;
    }

    private void enviarBienvenida(Usuario usuario, String contrasenaTemporal, String codigoVisible) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTipo("BIENVENIDA_ADMINISTRATIVA");
        notificacion.setDestinatario(usuario.getCorreo());
        notificacion.setAsunto("Tu cuenta en ESEJUR");
        try {
            mailService.sendHtml(HtmlMailMessage.to(usuario.getCorreo(), notificacion.getAsunto(),
                    "mail/bienvenida-administrativa.html",
                    Map.of(
                            "nombre", usuario.getPersona().getNombres(),
                            "correo", usuario.getCorreo(),
                            "contrasenaTemporal", contrasenaTemporal,
                            "codigo", codigoVisible)));
            notificacion.setEstadoEnvio("ENVIADO");
            notificacion.setEnviadoEn(clock.instant());
        } catch (MailDeliveryException exception) {
            notificacion.setEstadoEnvio("ERROR");
            notificacion.setUltimoError(exception.getMessage());
        }
        notificaciones.saveAndFlush(notificacion);
    }

    private UsuarioAdminRespuesta detalleDe(Usuario usuario) {
        List<UsuarioRolFila> filas = usuarioRoles.buscarPorUsuario(usuario.getId());
        String concedidoPorNombre = filas.stream()
                .filter(f -> f.rolCodigo().equals(RolUsuarioAdmin.ADMINISTRADOR.codigo()))
                .findFirst()
                .map(UsuarioRolFila::asignadoPorUsuarioId)
                .flatMap(usuarios::findWithPersonaById)
                .map(u -> u.getPersona().nombreCompleto())
                .orElse(null);
        return mapear(usuario, filas, concedidoPorNombre);
    }

    private UsuarioAdminRespuesta mapear(Usuario usuario, List<UsuarioRolFila> filas, String concedidoPorNombre) {
        Persona persona = usuario.getPersona();
        List<RolUsuarioAdmin> rolesDeUsuario = filas.stream()
                .map(f -> RolUsuarioAdmin.desdeCodigo(f.rolCodigo()))
                .toList();
        RolUsuarioAdmin principal = filas.stream()
                .filter(UsuarioRolFila::principal)
                .findFirst()
                .map(f -> RolUsuarioAdmin.desdeCodigo(f.rolCodigo()))
                .orElse(null);
        CondicionCuentaAdmin condicion = CondicionCuentaAdmin.de(
                usuario.getCorreoVerificadoEn() != null, usuario.isRequiereCambioContrasena());

        return new UsuarioAdminRespuesta(
                usuario.getId(),
                persona.getNombres(),
                persona.getApellidoPaterno(),
                persona.getApellidoMaterno(),
                persona.nombreCompleto(),
                usuario.getCorreo(),
                persona.getTelefono(),
                persona.getDocumentoIdentidad(),
                usuario.getOrigenRegistro(),
                usuario.isActivo(),
                condicion,
                principal,
                rolesDeUsuario,
                usuario.getCreadoEn(),
                concedidoPorNombre);
    }
}
