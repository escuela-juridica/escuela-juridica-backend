package pe.edu.utp.escuela.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utp.escuela.app.entity.Persona;
import pe.edu.utp.escuela.app.entity.Rol;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilPeticion;
import pe.edu.utp.escuela.app.dto.CambiarActivoPeticion;
import pe.edu.utp.escuela.app.dto.CondicionCuentaAdmin;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminPeticion;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.dto.RolUsuarioAdmin;
import pe.edu.utp.escuela.app.dto.UsuarioRolFila;
import pe.edu.utp.escuela.app.exception.DuplicateResourceException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.OperationNotAllowedException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.CodigoVerificacionRepositorio;
import pe.edu.utp.escuela.app.repository.NotificacionRepositorio;
import pe.edu.utp.escuela.app.repository.PersonaRepositorio;
import pe.edu.utp.escuela.app.repository.RolRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRepositorio;
import pe.edu.utp.escuela.app.repository.UsuarioRolRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;
import pe.edu.utp.escuela.app.security.CurrentUserService.CurrentUser;
import pe.edu.utp.escuela.app.util.TextNormalizer;

@ExtendWith(MockitoExtension.class)
class AdminUsuariosServicioTests {

    @Mock private UsuarioRepositorio usuarios;
    @Mock private PersonaRepositorio personas;
    @Mock private RolRepositorio roles;
    @Mock private UsuarioRolRepositorio usuarioRoles;
    @Mock private CodigoVerificacionRepositorio codigos;
    @Mock private NotificacionRepositorio notificaciones;
    @Mock private PasswordEncoder encoder;
    @Mock private MailService mailService;
    @Mock private CurrentUserService currentUserService;

    private AdminUsuariosServicio servicio;
    private final AtomicLong secuencia = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("America/Lima"));
        servicio = new AdminUsuariosServicio(usuarios, personas, roles, usuarioRoles, codigos,
                notificaciones, encoder, mailService, new TextNormalizer(), currentUserService, clock);
    }

    private void comoAdministrador(Long id) {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(id, "admin@escuelajuridica.edu.pe", Set.of("ADMINISTRADOR")));
    }

    private void comoAlumno(Long id) {
        when(currentUserService.get())
                .thenReturn(new CurrentUser(id, "alumno@example.com", Set.of("ALUMNO")));
    }

    private Rol rol(String codigo) {
        Rol rol = new Rol();
        rol.setId(codigo.equals("ROLE_ADMINISTRADOR") ? 1L : 2L);
        rol.setCodigo(codigo);
        rol.setActivo(true);
        return rol;
    }

    private Usuario usuarioExistente(Long id, String correo, boolean verificado, boolean requiereCambio) {
        Persona persona = new Persona();
        persona.setId(id * 10);
        persona.setNombres("Ana");
        persona.setApellidoPaterno("Perez");
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPersona(persona);
        usuario.setCorreo(correo);
        usuario.setActivo(true);
        usuario.setRequiereCambioContrasena(requiereCambio);
        if (verificado) {
            usuario.setCorreoVerificadoEn(Instant.parse("2026-01-01T00:00:00Z"));
        }
        return usuario;
    }

    private void stubGuardarConId() {
        when(personas.saveAndFlush(any())).thenAnswer(inv -> {
            Persona p = inv.getArgument(0);
            p.setId(secuencia.incrementAndGet());
            return p;
        });
        when(usuarios.saveAndFlush(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(secuencia.incrementAndGet());
            return u;
        });
    }

    @Test
    void cualquierOperacionSinRolAdministradorLanzaProhibido() {
        comoAlumno(5L);
        assertThrows(ForbiddenException.class, () -> servicio.obtener(1L));
    }

    @Test
    void crearConCorreoNuevoGeneraContrasenaTemporalYEnviaBienvenida() {
        comoAdministrador(1L);
        stubGuardarConId();
        when(usuarios.findByCorreoIgnoreCase("nueva@example.com")).thenReturn(Optional.empty());
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(encoder.encode(anyString())).thenReturn("hash");

        CrearUsuarioAdminRespuesta respuesta = servicio.crear(new CrearUsuarioAdminPeticion(
                "Maria", "Torres", null, "nueva@example.com", null, null, List.of(RolUsuarioAdmin.ALUMNO)));

        assertFalse(respuesta.reutilizada());
        assertNotNull(respuesta.contrasenaTemporal());
        assertTrue(respuesta.contrasenaTemporal().length() >= 8);
        assertEquals(CondicionCuentaAdmin.AMBAS_PENDIENTES, respuesta.usuario().condicion());
    }

    @Test
    void crearConCorreoExistenteReutilizaYNoGeneraContrasena() {
        comoAdministrador(1L);
        Usuario existente = usuarioExistente(7L, "existe@example.com", true, false);
        when(usuarios.findByCorreoIgnoreCase("existe@example.com")).thenReturn(Optional.of(existente));
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(usuarioRoles.buscarPorUsuario(7L)).thenReturn(List.of());

        CrearUsuarioAdminRespuesta respuesta = servicio.crear(new CrearUsuarioAdminPeticion(
                "Ana", "Perez", null, "EXISTE@example.com", null, null, List.of(RolUsuarioAdmin.ALUMNO)));

        assertTrue(respuesta.reutilizada());
        assertNull(respuesta.contrasenaTemporal());
    }

    @Test
    void crearConCorreoExistenteYRolYaAsignadoNoDuplicaLaAsignacion() {
        comoAdministrador(1L);
        Usuario existente = usuarioExistente(7L, "existe@example.com", true, false);
        when(usuarios.findByCorreoIgnoreCase("existe@example.com")).thenReturn(Optional.of(existente));
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(usuarioRoles.buscarPorUsuario(7L)).thenReturn(
                List.of(new UsuarioRolFila(7L, "ROLE_ALUMNO", true, null, Instant.now())));

        servicio.crear(new CrearUsuarioAdminPeticion(
                "Ana", "Perez", null, "existe@example.com", null, null, List.of(RolUsuarioAdmin.ALUMNO)));

        org.mockito.Mockito.verify(usuarioRoles, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void crearSegundoRolSobreCuentaExistenteQuedaComoSecundario() {
        comoAdministrador(1L);
        Usuario existente = usuarioExistente(7L, "existe@example.com", true, false);
        when(usuarios.findByCorreoIgnoreCase("existe@example.com")).thenReturn(Optional.of(existente));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));
        when(usuarioRoles.buscarPorUsuario(7L)).thenReturn(
                List.of(new UsuarioRolFila(7L, "ROLE_ALUMNO", true, null, Instant.now())));

        servicio.crear(new CrearUsuarioAdminPeticion(
                "Ana", "Perez", null, "existe@example.com", null, null, List.of(RolUsuarioAdmin.ADMINISTRADOR)));

        var captor = org.mockito.ArgumentCaptor.forClass(pe.edu.utp.escuela.app.entity.UsuarioRol.class);
        org.mockito.Mockito.verify(usuarioRoles).saveAndFlush(captor.capture());
        assertFalse(captor.getValue().isPrincipal());
        assertEquals(1L, captor.getValue().getAsignadoPorUsuarioId());
    }

    @Test
    void crearConDocumentoYaRegistradoLanzaDuplicado() {
        comoAdministrador(1L);
        when(usuarios.findByCorreoIgnoreCase("nueva@example.com")).thenReturn(Optional.empty());
        when(personas.existsByDocumentoIdentidad("12345678")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.crear(new CrearUsuarioAdminPeticion(
                "Maria", "Torres", null, "nueva@example.com", null, "12345678", List.of(RolUsuarioAdmin.ALUMNO))));
    }

    @Test
    void crearConOpcionalesVaciosNoBloqueaLaOperacion() {
        comoAdministrador(1L);
        stubGuardarConId();
        when(usuarios.findByCorreoIgnoreCase("nueva@example.com")).thenReturn(Optional.empty());
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(encoder.encode(anyString())).thenReturn("hash");

        CrearUsuarioAdminRespuesta respuesta = servicio.crear(new CrearUsuarioAdminPeticion(
                "Maria", "Torres", null, "nueva@example.com", null, null, List.of(RolUsuarioAdmin.ALUMNO)));

        assertNull(respuesta.usuario().apellidoMaterno());
        assertNull(respuesta.usuario().telefono());
        assertNull(respuesta.usuario().documentoIdentidad());
    }

    @Test
    void crearConAmbosRolesDejaAdministradorComoPrincipalSiempre() {
        comoAdministrador(1L);
        stubGuardarConId();
        when(usuarios.findByCorreoIgnoreCase("nueva@example.com")).thenReturn(Optional.empty());
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));
        when(encoder.encode(anyString())).thenReturn("hash");

        servicio.crear(new CrearUsuarioAdminPeticion(
                "Maria", "Torres", null, "nueva@example.com", null, null,
                List.of(RolUsuarioAdmin.ALUMNO, RolUsuarioAdmin.ADMINISTRADOR)));

        var captor = org.mockito.ArgumentCaptor.forClass(pe.edu.utp.escuela.app.entity.UsuarioRol.class);
        org.mockito.Mockito.verify(usuarioRoles, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        var asignaciones = captor.getAllValues();
        assertEquals(1, asignaciones.stream().filter(pe.edu.utp.escuela.app.entity.UsuarioRol::isPrincipal).count());
        var principal = asignaciones.stream().filter(pe.edu.utp.escuela.app.entity.UsuarioRol::isPrincipal).findFirst().orElseThrow();
        assertEquals(1L, principal.getId().getRolId());
    }

    @Test
    void resetearContrasenaGeneraUnaNuevaYForzaElCambio() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now())));
        when(encoder.encode(anyString())).thenReturn("hash-nuevo");

        var respuesta = servicio.resetearContrasena(9L);

        assertNotNull(respuesta.contrasenaTemporal());
        assertTrue(respuesta.contrasenaTemporal().length() >= 8);
        assertEquals("hash-nuevo", objetivo.getContrasenaHash());
        assertTrue(objetivo.isRequiereCambioContrasena());
    }

    @Test
    void obtenerConIdInexistenteLanzaNoEncontrado() {
        comoAdministrador(1L);
        when(usuarios.findWithPersonaById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> servicio.obtener(999L));
    }

    @Test
    void actualizarDatosPersonalesCambiaLosCamposDeLaPersona() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));

        var respuesta = servicio.actualizarDatosPersonales(9L,
                new ActualizarPerfilPeticion("Maria Jose", "Torres", "Lazo", "987654321", "45781203"));

        assertEquals("Maria Jose", respuesta.nombres());
        assertEquals("Torres", respuesta.apellidoPaterno());
        assertEquals("Lazo", respuesta.apellidoMaterno());
        assertEquals("987654321", respuesta.telefono());
        assertEquals("45781203", respuesta.documentoIdentidad());
    }

    @Test
    void actualizarDatosPersonalesConDocumentoYaUsadoPorOtraPersonaLanzaDuplicado() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(personas.existsByDocumentoIdentidadAndIdNot("45781203", objetivo.getPersona().getId()))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.actualizarDatosPersonales(9L,
                new ActualizarPerfilPeticion("Maria", "Torres", null, null, "45781203")));
    }

    @Test
    void actualizarDatosPersonalesConElMismoDocumentoQueYaTeniaNoLanzaDuplicado() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        objetivo.getPersona().setDocumentoIdentidad("45781203");
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));

        var respuesta = servicio.actualizarDatosPersonales(9L,
                new ActualizarPerfilPeticion("Maria", "Torres", null, null, "45781203"));

        assertEquals("45781203", respuesta.documentoIdentidad());
    }

    @Test
    void cambiarActivoParaDesactivarseAUnoMismoLanzaOperacionNoPermitida() {
        comoAdministrador(1L);
        Usuario usuario = usuarioExistente(1L, "admin@x.com", true, false);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(OperationNotAllowedException.class,
                () -> servicio.cambiarActivo(1L, new CambiarActivoPeticion(false, null)));
    }

    @Test
    void cambiarActivoParaDesactivarAlUltimoAdministradorActivoLanzaOperacionNoPermitida() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "otro-admin@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ADMINISTRADOR", true, null, Instant.now())));
        when(usuarioRoles.contarActivosConRol("ROLE_ADMINISTRADOR")).thenReturn(1L);

        assertThrows(OperationNotAllowedException.class,
                () -> servicio.cambiarActivo(9L, new CambiarActivoPeticion(false, null)));
    }

    @Test
    void cambiarActivoParaDesactivarAlumnoFunciona() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now())));

        var respuesta = servicio.cambiarActivo(9L, new CambiarActivoPeticion(false, "Motivo"));

        assertFalse(respuesta.activo());
    }

    @Test
    void revocarRolQueNoTieneNoHaceNada() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));

        servicio.revocarRol(9L, RolUsuarioAdmin.ADMINISTRADOR);

        org.mockito.Mockito.verify(usuarioRoles, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void revocarElUnicoRolDeLaCuentaLanzaOperacionNoPermitida() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));

        assertThrows(OperationNotAllowedException.class, () -> servicio.revocarRol(9L, RolUsuarioAdmin.ALUMNO));
    }

    @Test
    void revocarsePropioRolDeAdministradorLanzaOperacionNoPermitida() {
        comoAdministrador(1L);
        Usuario yoMismo = usuarioExistente(1L, "admin@x.com", true, false);
        when(usuarios.findWithPersonaById(1L)).thenReturn(Optional.of(yoMismo));
        when(usuarioRoles.buscarPorUsuario(1L)).thenReturn(List.of(
                new UsuarioRolFila(1L, "ROLE_ALUMNO", true, null, Instant.now()),
                new UsuarioRolFila(1L, "ROLE_ADMINISTRADOR", false, 1L, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));

        assertThrows(OperationNotAllowedException.class,
                () -> servicio.revocarRol(1L, RolUsuarioAdmin.ADMINISTRADOR));
    }

    @Test
    void revocarRolAlUltimoAdministradorActivoLanzaOperacionNoPermitida() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "otro-admin@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(List.of(
                new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now()),
                new UsuarioRolFila(9L, "ROLE_ADMINISTRADOR", false, 1L, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));
        when(usuarioRoles.contarActivosConRol("ROLE_ADMINISTRADOR")).thenReturn(1L);

        assertThrows(OperationNotAllowedException.class,
                () -> servicio.revocarRol(9L, RolUsuarioAdmin.ADMINISTRADOR));
    }

    @Test
    void revocarRolSecundarioFunciona() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "otro-admin@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(List.of(
                new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now()),
                new UsuarioRolFila(9L, "ROLE_ADMINISTRADOR", false, 1L, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));
        when(usuarioRoles.contarActivosConRol("ROLE_ADMINISTRADOR")).thenReturn(2L);

        servicio.revocarRol(9L, RolUsuarioAdmin.ADMINISTRADOR);

        org.mockito.Mockito.verify(usuarioRoles).deleteById(
                new pe.edu.utp.escuela.app.entity.UsuarioRol.Clave(9L, 1L));
    }

    @Test
    void revocarRolPrincipalPromueveElRolRestante() {
        comoAdministrador(1L);
        Usuario objetivo = usuarioExistente(9L, "otro-admin@x.com", true, false);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(objetivo));
        when(usuarioRoles.buscarPorUsuario(9L)).thenReturn(List.of(
                new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now()),
                new UsuarioRolFila(9L, "ROLE_ADMINISTRADOR", false, 1L, Instant.now())));
        when(roles.findByCodigoAndActivoTrue("ROLE_ALUMNO")).thenReturn(Optional.of(rol("ROLE_ALUMNO")));
        when(roles.findByCodigoAndActivoTrue("ROLE_ADMINISTRADOR")).thenReturn(Optional.of(rol("ROLE_ADMINISTRADOR")));

        var restante = new pe.edu.utp.escuela.app.entity.UsuarioRol();
        restante.setId(new pe.edu.utp.escuela.app.entity.UsuarioRol.Clave(9L, 1L));
        restante.setPrincipal(false);
        when(usuarioRoles.findById(new pe.edu.utp.escuela.app.entity.UsuarioRol.Clave(9L, 1L)))
                .thenReturn(Optional.of(restante));

        servicio.revocarRol(9L, RolUsuarioAdmin.ALUMNO);

        assertTrue(restante.isPrincipal());
    }

    @Test
    void reenviarHabilitacionConCorreoYaVerificadoNoReenviaNada() {
        comoAdministrador(1L);
        Usuario usuario = usuarioExistente(9L, "ya-verificado@x.com", true, true);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(usuario));

        assertFalse(servicio.reenviarHabilitacion(9L));
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.never()).sendHtml(any());
    }

    @Test
    void reenviarHabilitacionConCorreoPendienteReenviaElCodigo() {
        comoAdministrador(1L);
        Usuario usuario = usuarioExistente(9L, "pendiente@x.com", false, true);
        when(usuarios.findWithPersonaById(9L)).thenReturn(Optional.of(usuario));
        when(encoder.encode(anyString())).thenReturn("hash");

        assertTrue(servicio.reenviarHabilitacion(9L));
        org.mockito.Mockito.verify(mailService).sendHtml(any());
    }

    @Test
    void listarDevuelvePaginaConRolesAsociados() {
        comoAdministrador(1L);
        Usuario usuario = usuarioExistente(9L, "alumno@x.com", true, false);
        when(usuarios.buscarAdministrativos(anyString(), any(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(usuario), PageRequest.of(0, 20), 1));
        when(usuarioRoles.buscarPorUsuarios(any())).thenReturn(
                List.of(new UsuarioRolFila(9L, "ROLE_ALUMNO", true, null, Instant.now())));

        var pagina = servicio.listar("", null, null, PageRequest.of(0, 20));

        assertEquals(1, pagina.items().size());
        assertEquals(RolUsuarioAdmin.ALUMNO, pagina.items().get(0).rolPrincipal());
    }
}
