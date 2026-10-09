# HU-008 — Mapa técnico para gestionar usuarios administrativamente

> Estado de integración: esta historia **ya estuvo 100% implementada y funcionando** (hay
> screenshots reales de la pantalla "Usuarios" del panel de administración con datos reales). El
> dueño del proyecto decidió reasignarla como ejercicio de programación para el equipo: el
> controlador, el servicio y los componentes de Angular con su lógica real **se retiraron a
> propósito**, dejando un placeholder visible que sigue compilando. Este mapa no es un diseño
> nuevo — documenta exactamente lo que existía, con el código real embebido al final, para que el
> equipo lo reconstruya aprendiendo de esa referencia en vez de adivinar el diseño desde cero.
>
> **Importante — no todo se retiró.** `AdminUsuariosServicio.obtener(Long)` **se mantiene en el
> backend** porque `MatriculaServicio.tieneRol(...)` (HU-019, matricular administrativamente) lo
> usa para validar que el alumno elegido tenga el rol ALUMNO. De la misma forma,
> `AdminUsuariosApiService.listar(...)` y los tipos `UsuarioAdminRespuesta` / `RolUsuarioAdmin` /
> `CondicionCuentaAdmin` / `PageResponse` **se mantienen en el frontend** porque
> `matriculas-listado.ts` (HU-019) los usa para poblar el combo de alumnos al registrar una
> matrícula manual. Todo lo demás de esos archivos (y los dos controladores/componentes completos)
> se retiró. Revisa esos dos llamadores antes de volver a tocar esos archivos.

## Resultado que debe entregar

Un administrador busca, crea, consulta y edita cuentas de usuario desde un panel paginado: puede
filtrar por texto/rol/estado, abrir el detalle de una cuenta en un modal, editar sus datos
personales, conceder o retirar roles, habilitar/deshabilitar la cuenta, reenviar el código de
verificación de correo y resetear la contraseña generando una nueva temporal. Crear una cuenta
nunca pide contraseña: el sistema genera una temporal aleatoria, fuerza su cambio en el próximo
ingreso y la envía por correo junto con el código de verificación.

## Punto de partida

Todo esto ya existe y se reutiliza, no se recrea:

- Entidades y repositorios: `Usuario`/`UsuarioRepositorio`, `Persona`/`PersonaRepositorio`,
  `Rol`/`RolRepositorio`, `UsuarioRol`/`UsuarioRolRepositorio`,
  `CodigoVerificacionCorreo`/`CodigoVerificacionRepositorio`,
  `Notificacion`/`NotificacionRepositorio`.
- `PasswordEncoder`, `MailService` + `HtmlMailMessage` (envío de correo con plantilla),
  `TextNormalizer` (`trimToNull`, `requireText`, `normalizeEmail`), `CurrentUserService` (usuario
  autenticado + sus roles).
- Plantillas de correo ya existentes: `mail/bienvenida-administrativa.html`,
  `mail/contrasena-restablecida.html`, `mail/verification-code.html`.
- DTOs exclusivos de esta historia, **intactos en el código** (no se tocaron, no hace falta
  recrearlos): `CrearUsuarioAdminPeticion`, `CrearUsuarioAdminRespuesta`, `UsuarioAdminRespuesta`,
  `ConcederRolPeticion`, `CambiarActivoPeticion`, `ResetearContrasenaRespuesta`,
  `RolUsuarioAdmin` (enum `ALUMNO`/`ADMINISTRADOR`, con `codigo()` → `"ROLE_" + name()` y
  `desdeCodigo(String)`), `UsuarioRolFila` (proyección de `usuario_rol`), `CondicionCuentaAdmin`
  (enum `NINGUNA`/`PENDIENTE_VERIFICACION`/`CAMBIO_PENDIENTE`/`AMBAS_PENDIENTES`, con el método
  `de(correoVerificado, requiereCambioContrasena)`).
- `ActualizarPerfilPeticion` y `PageResponse<T>` también existen, pero son **compartidos con otras
  historias** (HU-005 "Mi perfil", y paginación general) — no son exclusivos de HU-008, no los
  documentes ni los toques como si lo fueran.
- Frontend: `shared/ui/modal/modal` (el listado abre crear/editar en modales, no en rutas
  aparte), los validadores de `features/cuenta/mi-perfil/mi-perfil.validators`
  (`nombrePropioValidator`, `documentoOpcionalValidator`, `telefonoOpcionalValidator`), y
  `core/session/nombre-utils` (`obtenerIniciales`, usado en otras pantallas admin).
- Diseño: Figma `EP02-PF-010-HU-008-Gestión de usuarios`.
- Tabla: `usuario_rol` (clave compuesta `usuarioId` + `rolId`, con `principal` y auditoría de quién
  asignó cada fila).

## Dependencias

Ninguna dentro de la épica; esta historia es independiente. Pero **dos piezas pequeñas de ella son
consumidas por HU-019** (matricular administrativamente, fuera de esta épica) y deben seguir
existiendo con la misma firma cuando el equipo reconstruya el resto:

- Backend: `AdminUsuariosServicio.obtener(Long usuarioId)` — lo llama
  `MatriculaServicio.tieneRol(usuarioId, "ROLE_ALUMNO")` antes de matricular a alguien
  manualmente.
- Frontend: `AdminUsuariosApiService.listar(texto, rol, activo, page, size)` y los tipos
  `UsuarioAdminRespuesta`/`RolUsuarioAdmin`/`CondicionCuentaAdmin`/`PageResponse` — los usa
  `matriculas-listado.ts` para el combo "Alumno" del formulario de matrícula manual.

No rompas esos dos contratos mientras reconstruyes el resto de la historia.

## Contratos que deben acordarse

| Método y ruta | Resultado conceptual |
|---|---|
| `GET /api/admin/usuarios` | Página de usuarios (filtros opcionales `texto`, `activo`, `rol`; `page`/`size`, tamaño máximo 50) |
| `GET /api/admin/usuarios/{usuarioId}` | Detalle administrativo de una cuenta |
| `PUT /api/admin/usuarios/{usuarioId}/datos-personales` | Actualiza nombres/apellidos/teléfono/documento de una cuenta ajena |
| `POST /api/admin/usuarios` | Crea una cuenta; si el correo ya existe, conserva la identidad y solo concede el rol faltante |
| `POST /api/admin/usuarios/{usuarioId}/roles` | Concede un rol adicional (no duplica si ya lo tiene) |
| `DELETE /api/admin/usuarios/{usuarioId}/roles/{rol}` | Retira un rol (desviación deliberada de la historia original, ver abajo) |
| `PATCH /api/admin/usuarios/{usuarioId}/activo` | Habilita o deshabilita la cuenta |
| `POST /api/admin/usuarios/{usuarioId}/resetear-contrasena` | Genera una contraseña temporal nueva y la envía por correo |
| `POST /api/admin/usuarios/{usuarioId}/reenviar-habilitacion` | Reenvía el código de verificación si el correo sigue sin verificar |

Todos exigen rol `ADMINISTRADOR` (403 si no lo tiene) y devuelven 404 si la cuenta ya no existe
(salvo `POST /api/admin/usuarios`, que crea).

## Trabajo del backend

### Archivos y responsabilidades

- `controller/AdminUsuariosControlador.java`: expone los 9 endpoints de la tabla, sin lógica de
  negocio — delega todo a `AdminUsuariosServicio`.
- `service/AdminUsuariosServicio.java`: concentra toda la lógica de negocio (ver reglas abajo).
  Hoy solo sobrevive `obtener(Long)` en el código (ver nota de integración); el resto se reconstruye
  siguiendo este mapa.

### Reglas en orden (no obvias, extraídas del código real)

1. **Toda operación exige rol ADMINISTRADOR** (`exigirAdministrador()`, al principio de cada
   método público) — si no, `ForbiddenException`.
2. **Crear usuario (`crear`)**:
   - El rol principal **no se pregunta**: con un solo rol solicitado, ese es el principal; si se
     pide `ADMINISTRADOR` junto con `ALUMNO`, `ADMINISTRADOR` siempre gana por ser el de mayor
     alcance (`determinarPrincipal`).
   - Si el correo **ya existe**, la cuenta existente se conserva tal cual (no se toca contraseña
     ni identidad): solo se conceden los roles solicitados que todavía no tenga
     (`concederRolSiFalta` por cada uno). La respuesta trae `reutilizada = true` y
     `contrasenaTemporal = null`.
   - Si el correo es nuevo y el documento de identidad viene informado pero ya está registrado en
     otra persona, `DuplicateResourceException` antes de crear nada.
   - Al crear de verdad: se genera una **contraseña temporal aleatoria** (nunca una fija
     compartida), se guarda su hash, se marca `requiereCambioContrasena = true`, se asigna cada rol
     solicitado (el primero que recibe la cuenta queda como principal salvo que gane
     `ADMINISTRADOR` por la regla anterior), se genera un código de verificación de correo y se
     envía el correo de bienvenida con la contraseña temporal y el código. La respuesta trae esa
     contraseña **una sola vez** (`contrasenaTemporal` con valor); nunca se vuelve a poder
     consultar después.
   - Generación de la contraseña temporal (`generarContrasenaTemporal`): 10 caracteres, garantiza
     al menos una mayúscula, una minúscula y un dígito (cumple la política de contraseñas siempre,
     no por azar), y **excluye `0/O/1/l/I`** para que sea legible al transcribirla desde la
     pantalla o el correo.
3. **Conceder rol (`concederRol` / `concederRolSiFalta`)**: si la cuenta ya tiene ese rol, no hace
   nada (no duplica la asignación ni cambia el rol principal existente). Si no lo tiene, se agrega
   como secundario si la cuenta ya tenía algún rol, o como principal si no tenía ninguno.
4. **Retirar rol (`revocarRol`) — desviación deliberada y documentada en el propio código**: la
   historia original de HU-008 dice literalmente que "en esta versión no se elimina roles"; esto
   se implementó **a propósito en contra de esa frase**, porque se pidió explícitamente. Mantiene
   las mismas protecciones que deshabilitar una cuenta:
   - Si la cuenta no tiene ese rol, no hace nada (respuesta sin cambios).
   - **Nunca se puede dejar una cuenta sin ningún rol**: si es el único rol que tiene,
     `OperationNotAllowedException`.
   - Si el rol a retirar es `ADMINISTRADOR`: **no puedes retirarte tu propio rol de administrador a
     ti mismo** (comparando contra el usuario autenticado), y **no puedes dejar el sistema sin
     ningún administrador activo habilitado** (si la cuenta está activa y
     `contarActivosConRol(ADMINISTRADOR) <= 1`, se bloquea).
   - Si el rol retirado era el **principal**, el rol restante se promueve a principal
     automáticamente (no puede quedar una cuenta con roles pero sin ninguno marcado como
     principal).
5. **Habilitar/deshabilitar (`cambiarActivo`)**: al desactivar, **no puedes desactivar tu propia
   cuenta**, y si la cuenta es administrador activo y es el único administrador activo
   (`contarActivosConRol(ADMINISTRADOR) <= 1`), tampoco se puede — mismo patrón de protección que
   `revocarRol`. Al desactivar se registra `deshabilitadoEn`; al reactivar, se limpia a `null`.
6. **Reenviar habilitación (`reenviarHabilitacion`)**: solo reenvía si el correo **todavía no está
   verificado** (`correoVerificadoEn == null`); si ya está verificado, no hace nada y devuelve
   `false`. La contraseña temporal **nunca se puede reenviar** porque no se guarda en texto plano
   en ningún lado — solo el código de verificación se regenera y reenvía.
7. **Resetear contraseña (`resetearContrasena`)**: genera una contraseña temporal nueva (mismo
   generador que al crear), actualiza el hash, fuerza `requiereCambioContrasena = true` y envía un
   correo de aviso. La respuesta trae la contraseña en texto plano **una sola vez**, igual que al
   crear la cuenta; no se puede volver a consultar después.
8. **Actualizar datos personales (`actualizarDatosPersonales`)**: pedido explícitamente por el
   dueño del proyecto tras confirmar que la historia sí contempla editar los datos personales desde
   este panel, no solo roles y habilitación. Reutiliza el mismo DTO (`ActualizarPerfilPeticion`) y
   las mismas reglas que HU-005 ("Mi perfil"), aplicadas aquí sobre una cuenta ajena: si el
   documento cambia y ya está en uso por otra persona, `DuplicateResourceException`.
9. **Listar (`listar`)**: paginado server-side desde el primer momento
   (`usuarios.buscarAdministrativos(termino, activo, rolCodigo, pageable)`), con el texto
   normalizado a minúsculas y el rol resuelto a su código (`ROLE_...`). Los roles de cada usuario
   de la página se resuelven en una sola consulta agrupada (`usuarioRoles.buscarPorUsuarios(ids)`),
   no una consulta por fila (evita N+1).
10. **`concedidoPorNombre`** en la respuesta de detalle: nombre completo de quién concedió el rol
    `ADMINISTRADOR` a esa cuenta (buscando el `asignadoPorUsuarioId` de la fila con ese rol); `null`
    si la cuenta no tiene ese rol o no se pudo resolver a quién lo asignó.

## Trabajo del frontend

### `features/admin/usuarios/usuarios-listado` (pantalla principal, ruta `/admin/usuarios`)

- Maquetar según `maquetacion-html/HU-008-PF-USUARIOS-listado.html` (ver guía de maquetación más
  abajo) y el Figma `EP02-PF-010-HU-008-Gestión de usuarios`.
- Tabla **paginada server-side desde el primer diseño** (20 por página), con buscador de
  texto libre y filtro por rol. Columna de "Estado de cuenta" con un único rótulo por fila
  (`etiquetaEstadoCuenta`): si la cuenta está deshabilitada, "Deshabilitada" gana sobre cualquier
  otra condición; si está habilitada pero tiene contraseña temporal o le faltan ambas cosas,
  "Contraseña temporal"; si solo falta verificar el correo, "Correo sin verificar"; si no falta
  nada, "Habilitada".
- "Crear usuario" y "Ver" abren **modales** (componente `app-modal` compartido), no rutas aparte:
  `usuario-crear` para alta, `usuario-detalle` para ver/editar una cuenta existente.
- Al crear o actualizar una cuenta, la fila puede no estar en la página actual — el patrón es
  recargar la página completa en vez de intentar insertar/actualizar la fila a mano.

### `features/admin/usuarios/usuario-crear` (contenido del modal de alta)

- Formulario reactivo: nombres, apellidos (paterno obligatorio, materno opcional), correo,
  teléfono opcional, documento opcional, y checkboxes de rol (Alumno/Administrador, con Alumno
  marcado por defecto). No se puede dejar la selección de roles vacía (el último marcado no se
  puede desmarcar).
- Nombres y apellidos se auto-convierten a mayúsculas mientras se escriben (mismo patrón que otros
  formularios de persona del sistema).
- Mensaje aclaratorio permanente: el correo es el usuario, la contraseña la genera el sistema.
- Tras crear: si `reutilizada = true`, mensaje de que ya existía la cuenta (sin contraseña nueva);
  si no, muestra la contraseña temporal generada una sola vez.

### `features/admin/usuarios/usuario-detalle` (contenido del modal de detalle/edición)

- Carga el detalle al recibir `usuarioId` (`ngOnChanges`). Tres secciones: datos personales (con
  modo edición in-place, mismos validadores que "Mi perfil"), roles (checkboxes que llaman
  conceder/revocar en vivo, con mensaje de ayuda explicando las tres protecciones: no dejar la
  cuenta sin roles, no quitarte tu propio admin, no dejar el sistema sin ningún admin), y acceso
  (interruptor habilitar/deshabilitar + botones "Reenviar código de verificación" — solo visible si
  hace falta — y "Resetear contraseña").
- Un único slot de alerta (éxito/error) que se autolimpia a los 5 segundos, para que un error de
  una acción no quede pegado en pantalla mientras otra acción distinta termina bien. La contraseña
  temporal reseteada es la excepción: queda visible hasta que el administrador la cierra a
  propósito (hay que poder copiarla).

### Servicio y modelos Angular

- `admin-usuarios-api.service.ts`: un método por endpoint, mismo patrón que el resto de servicios
  admin (`inject(HttpClient)`, `API_URL`). **Hoy solo sobrevive `listar(...)` en el código** (ver
  nota de integración); el resto se reconstruye siguiendo este mapa.
- `usuario-admin.model.ts`: tipos que reflejan los DTOs del backend, nombres de campo idénticos.
  **Hoy solo sobreviven `RolUsuarioAdmin`, `CondicionCuentaAdmin`, `UsuarioAdminRespuesta` y
  `PageResponse<T>`**.
- `usuario-admin-etiquetas.ts`: funciones puras de presentación (`etiquetaCondicion`,
  `etiquetaEstadoCuenta`, `claseEstadoCuenta`) — se reconstruye junto con el resto, no depende de
  HTTP.

## Pruebas mínimas

Basadas en los tests reales que existían en `AdminUsuariosServicioTests` (ya retirados; esto es lo
que debe seguir cumpliéndose cuando el equipo reconstruya el servicio):

- Cualquier operación sin rol ADMINISTRADOR lanza `ForbiddenException`.
- Crear con correo nuevo genera una contraseña temporal (≥ 8 caracteres) y deja la cuenta en
  condición `AMBAS_PENDIENTES`.
- Crear con correo ya existente reutiliza la cuenta (`reutilizada = true`) y no genera contraseña
  (`contrasenaTemporal = null`).
- Crear sobre un correo existente que ya tiene el rol solicitado no duplica la asignación (no debe
  guardar una fila nueva).
- Conceder un segundo rol sobre una cuenta existente queda como secundario (no principal), con el
  administrador actual como otorgante.
- Crear con un documento de identidad ya registrado lanza `DuplicateResourceException`.
- Crear sin apellido materno, teléfono ni documento no bloquea la operación (quedan `null` en la
  respuesta).
- Crear solicitando ambos roles deja siempre `ADMINISTRADOR` como principal, sin importar el orden
  en que se pidieron.
- Resetear contraseña genera una nueva temporal (≥ 8 caracteres), actualiza el hash guardado y
  fuerza `requiereCambioContrasena = true`.
- Obtener con un id inexistente lanza `ResourceNotFoundException`.
- Actualizar datos personales refleja los campos nuevos (nombres, apellidos, teléfono, documento)
  en la respuesta.
- Actualizar con un documento ya usado por **otra** persona lanza `DuplicateResourceException`.
- Actualizar conservando el **mismo** documento que la persona ya tenía no lanza duplicado.
- Un administrador no puede desactivar su propia cuenta (`OperationNotAllowedException`).
- No se puede desactivar al último administrador activo habilitado
  (`OperationNotAllowedException`).
- Desactivar a un alumno (sin rol administrador) funciona sin restricciones.
- Revocar un rol que la cuenta no tiene no hace nada (no llama a `deleteById`).
- Revocar el único rol de una cuenta lanza `OperationNotAllowedException`.
- Un administrador no puede retirarse su propio rol `ADMINISTRADOR`
  (`OperationNotAllowedException`).
- No se puede retirar el rol `ADMINISTRADOR` al último administrador activo habilitado.
- Revocar un rol secundario (no el único, y no deja el sistema sin administradores) funciona y
  elimina la asignación.
- Al revocar el rol que era principal, el rol restante queda promovido a principal.
- Reenviar con el correo ya verificado no reenvía nada (devuelve `false`, no llama a
  `mailService.sendHtml`).
- Reenviar con el correo pendiente de verificar genera y envía un nuevo código (devuelve `true`).
- Listar devuelve una página con los roles de cada usuario correctamente resueltos.

Por la instrucción vigente del proyecto de no escribir nuevos tests JUnit, estas verificaciones se
confirman manualmente (Swagger / la UI) salvo que se indique lo contrario.

## Terminado cuando

Un administrador puede buscar, crear, ver, editar, habilitar/deshabilitar y gestionar los roles de
cualquier cuenta desde el panel, con paginación server-side desde el primer momento. Crear una
cuenta nunca pide contraseña manualmente. Ningún flujo permite dejar el sistema sin al menos un
administrador activo, ni que un administrador se quite a sí mismo su propio rol o se desactive a sí
mismo. `MatriculaServicio.tieneRol(...)` (HU-019) y `matriculas-listado.ts` (HU-019) siguen
funcionando sin cambios, apoyados en las piezas mínimas que se mantuvieron de esta historia.

## Implementación guiada para copiar y adaptar

El código de esta sección es el que **realmente estuvo funcionando en producción** antes de
retirarse — se copia tal cual, no hay que inventar nada.

### 1. Orden sugerido para reconstruir

1. Backend: completar `AdminUsuariosServicio.java` (ya existe con solo `obtener`) con el resto de
   métodos del punto 2 de abajo.
2. Backend: `controller/AdminUsuariosControlador.java` (se recrea completo, no existe).
3. Frontend: completar `usuario-admin.model.ts` y `admin-usuarios-api.service.ts` (ya existen
   recortados) con el resto de tipos/métodos del punto 4 de abajo.
4. Frontend: recrear `usuario-admin-etiquetas.ts` (no existe).
5. Frontend: `usuarios-listado` (ts/html/scss), `usuario-crear` (ts/html/scss), `usuario-detalle`
   (ts/html/scss) — reemplazar el placeholder `.pantalla-pendiente` de cada uno por el contenido
   real de abajo.

### 2. `AdminUsuariosServicio.java` completo (código real original)

```java
package pe.edu.utp.escuela.app.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
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
import pe.edu.utp.escuela.app.dto.CambiarActivoPeticion;
import pe.edu.utp.escuela.app.dto.ConcederRolPeticion;
import pe.edu.utp.escuela.app.dto.CondicionCuentaAdmin;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminPeticion;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ResetearContrasenaRespuesta;
import pe.edu.utp.escuela.app.dto.RolUsuarioAdmin;
import pe.edu.utp.escuela.app.dto.UsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.dto.UsuarioRolFila;
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
        List<RolUsuarioAdmin> rolesSolicitados = p.roles().stream().distinct().toList();
        RolUsuarioAdmin principal = determinarPrincipal(rolesSolicitados);

        Optional<Usuario> existente = usuarios.findByCorreoIgnoreCase(correo);
        if (existente.isPresent()) {
            Usuario usuario = existente.get();
            for (RolUsuarioAdmin rol : rolesSolicitados) {
                concederRolSiFalta(usuario, rol);
            }
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
        String contrasenaTemporal = generarContrasenaTemporal();
        Usuario usuario = new Usuario();
        usuario.setPersona(persona);
        usuario.setCorreo(correo);
        usuario.setOrigenRegistro("ADMINISTRATIVO");
        usuario.setActivo(true);
        usuario.setRequiereCambioContrasena(true);
        usuario.setContrasenaHash(encoder.encode(contrasenaTemporal));
        usuario.setCreadoPorUsuarioId(adminActualId);
        usuarios.saveAndFlush(usuario);

        for (RolUsuarioAdmin rol : rolesSolicitados) {
            asignarRol(usuario, rol, adminActualId, rol == principal);
        }

        String codigo = generarCodigo(usuario);
        enviarBienvenida(usuario, contrasenaTemporal, codigo);

        return new CrearUsuarioAdminRespuesta(detalleDe(usuario), false, contrasenaTemporal);
    }

    /** Ya no se pregunta ni se infiere "cuál se guardó primero": con un solo rol, ese es el
     * principal; con ambos, ADMINISTRADOR siempre gana por ser el de mayor alcance. */
    private RolUsuarioAdmin determinarPrincipal(List<RolUsuarioAdmin> roles) {
        return roles.contains(RolUsuarioAdmin.ADMINISTRADOR) ? RolUsuarioAdmin.ADMINISTRADOR : roles.get(0);
    }

    /** Genera una contraseña temporal aleatoria (no una fija compartida por todas las cuentas),
     * cumpliendo siempre la política de contraseñas (mayúscula, minúscula y dígito); evita
     * 0/O/1/l/I para que sea legible al transcribirla desde la pantalla o el correo. */
    private String generarContrasenaTemporal() {
        String mayusculas = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String minusculas = "abcdefghjkmnpqrstuvwxyz";
        String digitos = "23456789";
        String todos = mayusculas + minusculas + digitos;

        List<Character> caracteres = new ArrayList<>();
        caracteres.add(mayusculas.charAt(random.nextInt(mayusculas.length())));
        caracteres.add(minusculas.charAt(random.nextInt(minusculas.length())));
        caracteres.add(digitos.charAt(random.nextInt(digitos.length())));
        for (int i = 0; i < 7; i++) {
            caracteres.add(todos.charAt(random.nextInt(todos.length())));
        }
        Collections.shuffle(caracteres, random);

        StringBuilder resultado = new StringBuilder(caracteres.size());
        caracteres.forEach(resultado::append);
        return resultado.toString();
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

    /** Resetea la contraseña con una temporal aleatoria nueva (pedido explícitamente: antes no
     * existía forma de que el administrador restableciera el acceso de alguien). La respuesta la
     * trae una sola vez, igual que al crear la cuenta; también se envía por correo. */
    @Transactional
    public ResetearContrasenaRespuesta resetearContrasena(Long usuarioId) {
        exigirAdministrador();
        Usuario usuario = buscarOLanzar(usuarioId);
        String contrasenaTemporal = generarContrasenaTemporal();
        usuario.setContrasenaHash(encoder.encode(contrasenaTemporal));
        usuario.setRequiereCambioContrasena(true);
        enviarContrasenaRestablecida(usuario, contrasenaTemporal);
        return new ResetearContrasenaRespuesta(detalleDe(usuario), contrasenaTemporal);
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

    /** Para conceder un rol suelto sobre una cuenta que ya existe: el primer rol que recibe
     * queda como principal; cualquier otro se agrega como secundario sin tocar el principal ya
     * existente. */
    private void asignarRol(Usuario usuario, RolUsuarioAdmin rolSolicitado, Long otorganteId) {
        boolean tieneAlgunRol = !usuarioRoles.buscarPorUsuario(usuario.getId()).isEmpty();
        asignarRol(usuario, rolSolicitado, otorganteId, !tieneAlgunRol);
    }

    private void asignarRol(Usuario usuario, RolUsuarioAdmin rolSolicitado, Long otorganteId, boolean principal) {
        Rol rol = rolPorCodigo(rolSolicitado);
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setId(new UsuarioRol.Clave(usuario.getId(), rol.getId()));
        asignacion.setPrincipal(principal);
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

    private void enviarContrasenaRestablecida(Usuario usuario, String contrasenaTemporal) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTipo("CONTRASENA_RESTABLECIDA");
        notificacion.setDestinatario(usuario.getCorreo());
        notificacion.setAsunto("Tu contraseña en ESEJUR fue restablecida");
        try {
            mailService.sendHtml(HtmlMailMessage.to(usuario.getCorreo(), notificacion.getAsunto(),
                    "mail/contrasena-restablecida.html",
                    Map.of(
                            "nombre", usuario.getPersona().getNombres(),
                            "contrasenaTemporal", contrasenaTemporal)));
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
```

### 3. `AdminUsuariosControlador.java` completo (código real original)

```java
package pe.edu.utp.escuela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.ActualizarPerfilPeticion;
import pe.edu.utp.escuela.app.dto.CambiarActivoPeticion;
import pe.edu.utp.escuela.app.dto.ConcederRolPeticion;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminPeticion;
import pe.edu.utp.escuela.app.dto.CrearUsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.dto.PageResponse;
import pe.edu.utp.escuela.app.dto.ResetearContrasenaRespuesta;
import pe.edu.utp.escuela.app.dto.RolUsuarioAdmin;
import pe.edu.utp.escuela.app.dto.UsuarioAdminRespuesta;
import pe.edu.utp.escuela.app.service.AdminUsuariosServicio;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "HU-008 Gestionar usuarios", description = "Búsqueda, alta, roles y habilitación de cuentas, para administradores")
public class AdminUsuariosControlador {

    private final AdminUsuariosServicio servicio;

    @GetMapping
    @Operation(summary = "Listar usuarios (búsqueda y filtros opcionales, paginado)")
    @ApiResponse(responseCode = "200", description = "Página de usuarios")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    public ResponseEntity<PageResponse<UsuarioAdminRespuesta>> listar(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) RolUsuarioAdmin rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int tamano = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(texto, activo, rol, PageRequest.of(Math.max(page, 0), tamano)));
    }

    @GetMapping("/{usuarioId}")
    @Operation(summary = "Consultar el detalle administrativo de un usuario")
    @ApiResponse(responseCode = "200", description = "Detalle del usuario")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<UsuarioAdminRespuesta> obtener(@PathVariable Long usuarioId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.obtener(usuarioId));
    }

    @PutMapping("/{usuarioId}/datos-personales")
    @Operation(summary = "Actualizar los datos personales de una cuenta")
    @ApiResponse(responseCode = "200", description = "Datos actualizados")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "El documento de identidad ya está en uso")
    public ResponseEntity<UsuarioAdminRespuesta> actualizarDatosPersonales(
            @PathVariable Long usuarioId, @Valid @RequestBody ActualizarPerfilPeticion p) {
        return ResponseEntity.ok(servicio.actualizarDatosPersonales(usuarioId, p));
    }

    @PostMapping
    @Operation(summary = "Crear un usuario administrativamente",
            description = "Si el correo ya existe, conserva la cuenta y solo concede el rol faltante; "
                    + "no cambia la contraseña ni duplica la identidad.")
    @ApiResponse(responseCode = "200", description = "Cuenta creada o rol concedido sobre una existente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "409", description = "El documento de identidad ya está en uso")
    public ResponseEntity<CrearUsuarioAdminRespuesta> crear(@Valid @RequestBody CrearUsuarioAdminPeticion p) {
        return ResponseEntity.ok(servicio.crear(p));
    }

    @PostMapping("/{usuarioId}/roles")
    @Operation(summary = "Conceder un rol adicional a una cuenta existente",
            description = "No duplica la asignación si ya lo tiene; nunca cambia el rol principal.")
    @ApiResponse(responseCode = "200", description = "Rol concedido (o ya existente, sin cambios)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<UsuarioAdminRespuesta> concederRol(
            @PathVariable Long usuarioId, @Valid @RequestBody ConcederRolPeticion p) {
        return ResponseEntity.ok(servicio.concederRol(usuarioId, p));
    }

    @DeleteMapping("/{usuarioId}/roles/{rol}")
    @Operation(summary = "Retirar un rol de una cuenta",
            description = "Desviación deliberada de HU-008 (que dice que no se elimina roles), "
                    + "pedida explícitamente. Nunca deja una cuenta sin ningún rol.")
    @ApiResponse(responseCode = "200", description = "Rol retirado (o ya no lo tenía, sin cambios)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "No puedes dejar la cuenta sin roles, ni retirarte tu propio rol de administrador, ni al último administrador")
    public ResponseEntity<UsuarioAdminRespuesta> revocarRol(
            @PathVariable Long usuarioId, @PathVariable RolUsuarioAdmin rol) {
        return ResponseEntity.ok(servicio.revocarRol(usuarioId, rol));
    }

    @PatchMapping("/{usuarioId}/activo")
    @Operation(summary = "Habilitar o deshabilitar una cuenta")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    @ApiResponse(responseCode = "409", description = "No puedes desactivar tu propia cuenta ni al último administrador")
    public ResponseEntity<UsuarioAdminRespuesta> cambiarActivo(
            @PathVariable Long usuarioId, @Valid @RequestBody CambiarActivoPeticion p) {
        return ResponseEntity.ok(servicio.cambiarActivo(usuarioId, p));
    }

    @PostMapping("/{usuarioId}/resetear-contrasena")
    @Operation(summary = "Resetear la contraseña de una cuenta",
            description = "Genera una contraseña temporal aleatoria nueva, fuerza el cambio en el "
                    + "próximo ingreso y la envía por correo. La respuesta la trae una sola vez.")
    @ApiResponse(responseCode = "200", description = "Contraseña restablecida")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<ResetearContrasenaRespuesta> resetearContrasena(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicio.resetearContrasena(usuarioId));
    }

    @PostMapping("/{usuarioId}/reenviar-habilitacion")
    @Operation(summary = "Reenviar el código de verificación vigente",
            description = "Solo reenvía si el correo todavía no está verificado; la contraseña "
                    + "temporal no se puede reenviar porque nunca se guarda en texto plano.")
    @ApiResponse(responseCode = "204", description = "Procesado (puede no haber reenviado nada si ya estaba verificado)")
    @ApiResponse(responseCode = "403", description = "No tienes rol ADMINISTRADOR")
    @ApiResponse(responseCode = "404", description = "La cuenta ya no existe")
    public ResponseEntity<Void> reenviarHabilitacion(@PathVariable Long usuarioId) {
        servicio.reenviarHabilitacion(usuarioId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

### 4. `usuario-admin.model.ts` completo (código real original)

```ts
export type RolUsuarioAdmin = 'ALUMNO' | 'ADMINISTRADOR';

/** CAMBIO_PENDIENTE: falta reemplazar la contraseña temporal. PENDIENTE_VERIFICACION: falta
 * verificar el correo. AMBAS_PENDIENTES: recién creada, faltan las dos. NINGUNA: cuenta operativa. */
export type CondicionCuentaAdmin =
  | 'NINGUNA'
  | 'PENDIENTE_VERIFICACION'
  | 'CAMBIO_PENDIENTE'
  | 'AMBAS_PENDIENTES';

export interface UsuarioAdminRespuesta {
  usuarioId: number;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  nombreCompleto: string;
  correo: string;
  telefono: string | null;
  documentoIdentidad: string | null;
  origenRegistro: string;
  activo: boolean;
  condicion: CondicionCuentaAdmin;
  rolPrincipal: RolUsuarioAdmin | null;
  roles: RolUsuarioAdmin[];
  creadoEn: string;
  /** Nombre de quién concedió el rol ADMINISTRADOR; null si no tiene ese rol o se desconoce. */
  concedidoPorNombre: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CrearUsuarioAdminPeticion {
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  correo: string;
  telefono: string | null;
  documentoIdentidad: string | null;
  /** El rol principal no se elige: con un único rol, ese es el principal; con ambos,
   * Administrador siempre gana. */
  roles: RolUsuarioAdmin[];
}

export interface CrearUsuarioAdminRespuesta {
  usuario: UsuarioAdminRespuesta;
  /** true si el correo ya existía: se conservó la cuenta y no se generó contraseña temporal. */
  reutilizada: boolean;
  contrasenaTemporal: string | null;
}

export interface ConcederRolPeticion {
  rol: RolUsuarioAdmin;
}

export interface CambiarActivoPeticion {
  activo: boolean;
  motivo: string | null;
}

export interface ResetearContrasenaRespuesta {
  usuario: UsuarioAdminRespuesta;
  /** Solo viene en esta respuesta puntual; no se puede volver a consultar después. */
  contrasenaTemporal: string;
}

export interface ActualizarDatosPersonalesPeticion {
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  telefono: string | null;
  documentoIdentidad: string | null;
}
```

### 5. `admin-usuarios-api.service.ts` completo (código real original)

```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../../core/api/api.config';
import {
  ActualizarDatosPersonalesPeticion,
  CambiarActivoPeticion,
  ConcederRolPeticion,
  CrearUsuarioAdminPeticion,
  CrearUsuarioAdminRespuesta,
  PageResponse,
  ResetearContrasenaRespuesta,
  RolUsuarioAdmin,
  UsuarioAdminRespuesta,
} from './usuario-admin.model';

/** HU-008 — Gestionar usuarios administrativamente. Consume `/api/admin/usuarios`, respaldado por
 * base de datos real y protegido por sesión + rol ADMINISTRADOR. */
@Injectable({ providedIn: 'root' })
export class AdminUsuariosApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/admin/usuarios`;

  listar(
    texto: string,
    rol: RolUsuarioAdmin | 'TODOS',
    activo: boolean | 'TODOS',
    page: number,
    size: number,
  ): Observable<PageResponse<UsuarioAdminRespuesta>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (texto.trim()) {
      params = params.set('texto', texto.trim());
    }
    if (rol !== 'TODOS') {
      params = params.set('rol', rol);
    }
    if (activo !== 'TODOS') {
      params = params.set('activo', activo);
    }
    return this.http.get<PageResponse<UsuarioAdminRespuesta>>(this.url, { params });
  }

  obtener(usuarioId: number): Observable<UsuarioAdminRespuesta> {
    return this.http.get<UsuarioAdminRespuesta>(`${this.url}/${usuarioId}`);
  }

  actualizarDatosPersonales(
    usuarioId: number,
    peticion: ActualizarDatosPersonalesPeticion,
  ): Observable<UsuarioAdminRespuesta> {
    return this.http.put<UsuarioAdminRespuesta>(`${this.url}/${usuarioId}/datos-personales`, peticion);
  }

  crear(peticion: CrearUsuarioAdminPeticion): Observable<CrearUsuarioAdminRespuesta> {
    return this.http.post<CrearUsuarioAdminRespuesta>(this.url, peticion);
  }

  concederRol(usuarioId: number, peticion: ConcederRolPeticion): Observable<UsuarioAdminRespuesta> {
    return this.http.post<UsuarioAdminRespuesta>(`${this.url}/${usuarioId}/roles`, peticion);
  }

  /** Desviación deliberada de HU-008 (que dice que un rol no se retira), pedida explícitamente. */
  revocarRol(usuarioId: number, rol: RolUsuarioAdmin): Observable<UsuarioAdminRespuesta> {
    return this.http.delete<UsuarioAdminRespuesta>(`${this.url}/${usuarioId}/roles/${rol}`);
  }

  cambiarActivo(usuarioId: number, peticion: CambiarActivoPeticion): Observable<UsuarioAdminRespuesta> {
    return this.http.patch<UsuarioAdminRespuesta>(`${this.url}/${usuarioId}/activo`, peticion);
  }

  reenviarHabilitacion(usuarioId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${usuarioId}/reenviar-habilitacion`, {});
  }

  resetearContrasena(usuarioId: number): Observable<ResetearContrasenaRespuesta> {
    return this.http.post<ResetearContrasenaRespuesta>(`${this.url}/${usuarioId}/resetear-contrasena`, {});
  }
}
```

### 6. `usuario-admin-etiquetas.ts` completo (código real original)

```ts
import { CondicionCuentaAdmin, UsuarioAdminRespuesta } from './usuario-admin.model';

export function etiquetaCondicion(condicion: CondicionCuentaAdmin): string {
  switch (condicion) {
    case 'AMBAS_PENDIENTES':
      return 'Verificación y contraseña pendientes';
    case 'PENDIENTE_VERIFICACION':
      return 'Verificación de correo pendiente';
    case 'CAMBIO_PENDIENTE':
      return 'Cambio de contraseña pendiente';
    default:
      return '';
  }
}

/** Un solo rótulo por fila: "Habilitada" / "Deshabilitada" / "Contraseña temporal" /
 * "Correo sin verificar". Si faltan las dos, prioriza la contraseña porque es lo primero que la
 * persona debe resolver al entrar con la clave temporal. */
export function etiquetaEstadoCuenta(usuario: Pick<UsuarioAdminRespuesta, 'activo' | 'condicion'>): string {
  if (!usuario.activo) {
    return 'Deshabilitada';
  }
  if (usuario.condicion === 'CAMBIO_PENDIENTE' || usuario.condicion === 'AMBAS_PENDIENTES') {
    return 'Contraseña temporal';
  }
  if (usuario.condicion === 'PENDIENTE_VERIFICACION') {
    return 'Correo sin verificar';
  }
  return 'Habilitada';
}

/** Clase de color para acompañar `etiquetaEstadoCuenta`. */
export function claseEstadoCuenta(usuario: Pick<UsuarioAdminRespuesta, 'activo' | 'condicion'>): string {
  if (!usuario.activo) {
    return 'badge--disp-cerrado';
  }
  return usuario.condicion === 'NINGUNA' ? 'badge--disp-inmediato' : 'badge--advertencia-suave';
}
```

### 7. `usuarios-listado` completo (código real original)

```ts
// usuarios-listado.ts
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { Modal } from '../../../../shared/ui/modal/modal';
import { AdminUsuariosApiService } from '../admin-usuarios-api.service';
import { UsuarioCrear } from '../usuario-crear/usuario-crear';
import { UsuarioDetalle } from '../usuario-detalle/usuario-detalle';
import { CrearUsuarioAdminRespuesta, RolUsuarioAdmin, UsuarioAdminRespuesta } from '../usuario-admin.model';
import { claseEstadoCuenta, etiquetaEstadoCuenta } from '../usuario-admin-etiquetas';

/** HU-008 — Gestionar usuarios administrativamente. Listado paginado contra la API real; crear
 * y editar se hacen en modales, no en pantallas aparte.
 * Diseño según el Figma EP02-PF-010-HU-008-Gestión de usuarios. */
@Component({
  selector: 'app-usuarios-listado',
  imports: [FormsModule, Modal, UsuarioCrear, UsuarioDetalle],
  templateUrl: './usuarios-listado.html',
  styleUrl: './usuarios-listado.scss',
})
export class UsuariosListado implements OnInit {
  private readonly api = inject(AdminUsuariosApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detector = inject(ChangeDetectorRef);

  protected readonly texto = signal('');
  protected readonly filtroRol = signal<RolUsuarioAdmin | 'TODOS'>('TODOS');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly usuarios = signal<UsuarioAdminRespuesta[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);

  /** null: ningún modal abierto. 'nuevo': modal de creación. número: editando ese usuarioId. */
  protected readonly modalAbierto = signal<'nuevo' | number | null>(null);

  protected readonly etiquetaEstadoCuenta = etiquetaEstadoCuenta;
  protected readonly claseEstadoCuenta = claseEstadoCuenta;

  ngOnInit(): void {
    this.cargar();
  }

  protected onBusquedaInput(valor: string): void {
    this.texto.set(valor);
    this.pagina.set(0);
    this.cargar();
  }

  protected onFiltroRolChange(valor: string): void {
    this.filtroRol.set(valor as RolUsuarioAdmin | 'TODOS');
    this.pagina.set(0);
    this.cargar();
  }

  protected irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginas()) {
      return;
    }
    this.pagina.set(pagina);
    this.cargar();
  }

  protected abrirCrear(): void {
    this.modalAbierto.set('nuevo');
  }

  protected abrirEditar(usuarioId: number): void {
    this.modalAbierto.set(usuarioId);
  }

  protected cerrarModal(): void {
    this.modalAbierto.set(null);
  }

  /** Solo el número de usuario en edición; null en cualquier otro caso (incluido 'nuevo'). */
  protected get idEnEdicion(): number | null {
    const modal = this.modalAbierto();
    return typeof modal === 'number' ? modal : null;
  }

  /** Una cuenta creada u editada puede no estar en la página actual; recargar es más simple y
   * correcto que intentar insertarla a mano en la lista paginada. */
  protected alCrear(respuesta: CrearUsuarioAdminRespuesta): void {
    this.cargar();
    this.modalAbierto.set(respuesta.usuario.usuarioId);
  }

  protected alActualizar(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api
      .listar(this.texto(), this.filtroRol(), 'TODOS', this.pagina(), 20)
      .pipe(
        finalize(() => {
          this.cargando.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta) => {
          this.usuarios.set(respuesta.items);
          this.totalPaginas.set(respuesta.totalPages);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(
            error.status === 403
              ? 'No tienes permiso para ver esta información.'
              : 'No pudimos cargar los usuarios. Inténtalo nuevamente.',
          );
        },
      });
  }
}
```

```html
<!-- usuarios-listado.html -->
<!-- HU-008: diseño según el Figma EP02-PF-010-HU-008-Gestión de usuarios. -->
<div class="usuarios-admin-cabecera">
  <div>
    <h1 class="usuarios-admin-titulo">Usuarios</h1>
    <p class="usuarios-admin-subtitulo">La habilitación exige correo verificado y contraseña propia.</p>
  </div>
  <button type="button" class="btn btn--primario" (click)="abrirCrear()">Crear usuario</button>
</div>

<div class="usuarios-admin-filtros">
  <input
    class="input"
    [ngModel]="texto()"
    (ngModelChange)="onBusquedaInput($event)"
    placeholder="Buscar por nombre o correo"
  />
  <select class="input" [ngModel]="filtroRol()" (ngModelChange)="onFiltroRolChange($event)">
    <option value="TODOS">Todos los roles</option>
    <option value="ALUMNO">Alumno</option>
    <option value="ADMINISTRADOR">Administrador</option>
  </select>
</div>

@if (cargando()) {
  <p>Cargando…</p>
} @else if (error()) {
  <div class="alerta alerta--error">
    <span class="alerta__icono">!</span>
    <div class="alerta__titulo">{{ error() }}</div>
  </div>
} @else if (usuarios().length === 0) {
  <div class="estado-vacio">
    <h3 class="estado-vacio__titulo">Sin resultados</h3>
    <p class="estado-vacio__texto">Ningún usuario coincide con esa búsqueda.</p>
  </div>
} @else {
  <div class="usuarios-admin-tabla">
    <div class="usuarios-admin-fila usuarios-admin-fila--cabecera">
      <span>Nombre</span>
      <span>Correo</span>
      <span>Rol</span>
      <span>Origen</span>
      <span>Estado de cuenta</span>
      <span></span>
    </div>
    @for (usuario of usuarios(); track usuario.usuarioId) {
      <div class="usuarios-admin-fila">
        <span class="usuarios-admin-fila__nombre">{{ usuario.nombreCompleto }}</span>
        <span class="usuarios-admin-fila__correo">{{ usuario.correo }}</span>
        <span>{{ usuario.rolPrincipal === 'ADMINISTRADOR' ? 'Administrador' : 'Alumno' }}</span>
        <span>
          @switch (usuario.origenRegistro) {
            @case ('FORMULARIO') { Formulario }
            @case ('GOOGLE') { Google }
            @case ('ADMINISTRATIVO') { Administración }
          }
        </span>
        <span>
          <span class="badge" [class]="claseEstadoCuenta(usuario)">
            {{ etiquetaEstadoCuenta(usuario) }}
          </span>
        </span>
        <button
          type="button"
          class="usuarios-admin-fila__ver"
          (click)="abrirEditar(usuario.usuarioId)"
          [attr.aria-label]="'Ver ' + usuario.nombreCompleto"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12Z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
          <span>Ver</span>
        </button>
      </div>
    }
  </div>

  @if (totalPaginas() > 1) {
    <div class="usuarios-admin-paginacion">
      <button type="button" class="btn btn--secundario" [disabled]="pagina() === 0" (click)="irAPagina(pagina() - 1)">
        Anterior
      </button>
      <span>Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
      <button
        type="button"
        class="btn btn--secundario"
        [disabled]="pagina() + 1 >= totalPaginas()"
        (click)="irAPagina(pagina() + 1)"
      >
        Siguiente
      </button>
    </div>
  }
}

@if (modalAbierto() === 'nuevo') {
  <app-modal titulo="Crear usuario" ancho="lg" (cerrar)="cerrarModal()">
    <app-usuario-crear (cerrar)="cerrarModal()" (creado)="alCrear($event)" />
  </app-modal>
} @else if (idEnEdicion; as id) {
  <app-modal titulo="Detalle del usuario" ancho="lg" (cerrar)="cerrarModal()">
    <app-usuario-detalle [usuarioId]="id" (actualizado)="alActualizar()" />
  </app-modal>
}
```

```scss
// usuarios-listado.scss
.usuarios-admin-cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.usuarios-admin-titulo {
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: var(--neutral-950);
  margin: 0;
}

.usuarios-admin-subtitulo {
  color: var(--neutral-600);
  margin: 4px 0 0;
}

.usuarios-admin-filtros {
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 12px;
  margin-bottom: 24px;
}

.usuarios-admin-tabla {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--neutral-200);
  border-radius: var(--radio-lg);
  overflow: hidden;
  background: #ffffff;
}

.usuarios-admin-fila {
  display: grid;
  grid-template-columns: 1.4fr 1.6fr 0.9fr 1fr 1.3fr 84px;
  gap: 12px;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--neutral-100);
  color: inherit;
  font-size: 14px;
}

.usuarios-admin-fila:last-child {
  border-bottom: none;
}

.usuarios-admin-fila:not(.usuarios-admin-fila--cabecera):hover {
  background: var(--neutral-050);
}

.usuarios-admin-fila__ver {
  appearance: none;
  border: none;
  background: var(--acento-700);
  color: var(--blanco);
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radio-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
}

.usuarios-admin-fila__ver:hover {
  background: var(--marca-900);
}

.usuarios-admin-fila__ver:focus-visible {
  outline: 2px solid var(--acento-500);
  outline-offset: 2px;
}

.usuarios-admin-fila--cabecera {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--neutral-500);
}

.usuarios-admin-fila__nombre {
  font-weight: 600;
  color: var(--neutral-950);
}

.usuarios-admin-fila__correo {
  color: var(--neutral-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.usuarios-admin-paginacion {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  font-size: 14px;
  color: var(--neutral-600);
}

@media (max-width: 900px) {
  .usuarios-admin-filtros {
    grid-template-columns: 1fr;
  }

  .usuarios-admin-fila--cabecera {
    display: none;
  }

  .usuarios-admin-fila {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
```

### 8. `usuario-crear` completo (código real original)

```ts
// usuario-crear.ts
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { AdminUsuariosApiService } from '../admin-usuarios-api.service';
import { CrearUsuarioAdminRespuesta, RolUsuarioAdmin } from '../usuario-admin.model';
import {
  documentoOpcionalValidator,
  nombrePropioValidator,
  telefonoOpcionalValidator,
} from '../../../cuenta/mi-perfil/mi-perfil.validators';

interface ErrorApiAdmin {
  code?: string;
  message?: string;
}

/** HU-008 — Crear usuario administrativamente, como contenido de un modal (ver
 * usuarios-listado). Si el correo ya existe, conserva la cuenta y solo concede el rol faltante;
 * no genera contraseña temporal ni duplica la identidad. */
@Component({
  selector: 'app-usuario-crear',
  imports: [ReactiveFormsModule],
  templateUrl: './usuario-crear.html',
  styleUrl: './usuario-crear.scss',
})
export class UsuarioCrear {
  private readonly api = inject(AdminUsuariosApiService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detector = inject(ChangeDetectorRef);

  readonly cerrar = output<void>();
  readonly creado = output<CrearUsuarioAdminRespuesta>();

  protected readonly intentoGuardar = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorCrear = signal<string | null>(null);
  protected readonly resultado = signal<CrearUsuarioAdminRespuesta | null>(null);

  protected readonly todosLosRoles: RolUsuarioAdmin[] = ['ALUMNO', 'ADMINISTRADOR'];
  protected readonly rolesSeleccionados = signal<RolUsuarioAdmin[]>(['ALUMNO']);

  protected readonly formulario = this.fb.group({
    nombres: ['', [Validators.required, Validators.maxLength(120), nombrePropioValidator]],
    apellidoPaterno: ['', [Validators.required, Validators.maxLength(80), nombrePropioValidator]],
    apellidoMaterno: ['', [Validators.maxLength(80), nombrePropioValidator]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    telefono: ['', [Validators.maxLength(30), telefonoOpcionalValidator]],
    documentoIdentidad: ['', [Validators.maxLength(30), documentoOpcionalValidator]],
  });

  constructor() {
    for (const nombre of ['nombres', 'apellidoPaterno', 'apellidoMaterno'] as const) {
      const control = this.formulario.controls[nombre];
      control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((valor) => {
        const mayuscula = valor.toLocaleUpperCase('es-PE');
        if (valor !== mayuscula) {
          control.setValue(mayuscula, { emitEvent: false });
        }
      });
    }
  }

  protected campoInvalido(
    nombre: 'nombres' | 'apellidoPaterno' | 'apellidoMaterno' | 'correo' | 'telefono' | 'documentoIdentidad',
  ): boolean {
    const control = this.formulario.controls[nombre];
    return control.invalid && (control.touched || this.intentoGuardar());
  }

  protected etiquetaRol(rol: RolUsuarioAdmin): string {
    return rol === 'ADMINISTRADOR' ? 'Administrador' : 'Alumno';
  }

  protected tieneRol(rol: RolUsuarioAdmin): boolean {
    return this.rolesSeleccionados().includes(rol);
  }

  /** No se puede dejar la cuenta sin ningún rol: el último marcado no se puede desmarcar. El rol
   * principal no se pregunta: si incluye Administrador, ese es el principal; si no, Alumno. */
  protected alternarRol(rol: RolUsuarioAdmin): void {
    const actuales = this.rolesSeleccionados();
    if (actuales.includes(rol)) {
      if (actuales.length === 1) {
        return;
      }
      this.rolesSeleccionados.set(actuales.filter((r) => r !== rol));
    } else {
      this.rolesSeleccionados.set([...actuales, rol]);
    }
  }

  protected crear(): void {
    if (this.guardando()) {
      return;
    }

    this.intentoGuardar.set(true);
    this.errorCrear.set(null);
    this.resultado.set(null);

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const valores = this.formulario.getRawValue();
    this.guardando.set(true);
    this.api
      .crear({
        nombres: valores.nombres.trim(),
        apellidoPaterno: valores.apellidoPaterno.trim(),
        apellidoMaterno: this.textoOpcional(valores.apellidoMaterno),
        correo: valores.correo.trim(),
        telefono: this.textoOpcional(valores.telefono)?.replace(/[\s-]/g, '') ?? null,
        documentoIdentidad: this.textoOpcional(valores.documentoIdentidad),
        roles: this.rolesSeleccionados(),
      })
      .pipe(
        finalize(() => {
          this.guardando.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta) => {
          this.resultado.set(respuesta);
          this.creado.emit(respuesta);
        },
        error: (error: HttpErrorResponse) => {
          const respuesta = typeof error.error === 'object' && error.error !== null
            ? (error.error as ErrorApiAdmin)
            : null;
          this.errorCrear.set(respuesta?.message ?? 'No pudimos crear el usuario. Inténtalo nuevamente.');
        },
      });
  }

  private textoOpcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }
}
```

```html
<!-- usuario-crear.html -->
@if (resultado(); as r) {
  @if (r.reutilizada) {
    <div class="alerta alerta--info">
      <span class="alerta__icono">i</span>
      <div>
        <div class="alerta__titulo">Ya existía una cuenta con ese correo</div>
        <div class="alerta__texto">
          Se reutilizó "{{ r.usuario.nombreCompleto }}" ({{ r.usuario.correo }}). No se generó
          contraseña temporal ni se envió correo.
        </div>
      </div>
    </div>
  } @else {
    <div class="alerta alerta--exito">
      <span class="alerta__icono">✓</span>
      <div>
        <div class="alerta__titulo">Cuenta creada</div>
        <div class="alerta__texto">
          Contraseña temporal: <strong>{{ r.contrasenaTemporal }}</strong>. Queda con verificación
          y cambio de contraseña pendientes hasta que la persona complete el ingreso.
        </div>
      </div>
    </div>
  }
  <div class="usuario-crear-acciones">
    <button type="button" class="btn btn--primario" (click)="cerrar.emit()">Cerrar</button>
  </div>
} @else {
  <form [formGroup]="formulario" (ngSubmit)="crear()">
    <div class="alerta alerta--info usuario-crear-alerta">
      <span class="alerta__icono">i</span>
      <div class="alerta__texto">
        No defines usuario ni contraseña aquí: el correo que pongas abajo es el usuario. El sistema
        genera una contraseña temporal aleatoria que verás una sola vez al crear la cuenta y que
        también se envía por correo junto con el código de verificación.
      </div>
    </div>
    @if (errorCrear()) {
      <div class="alerta alerta--error usuario-crear-alerta">
        <span class="alerta__icono">!</span>
        <div class="alerta__titulo">{{ errorCrear() }}</div>
      </div>
    }
    <div class="usuario-crear-campos">
      <div>
        <label class="label" for="nombres">Nombres</label>
        <input
          id="nombres"
          class="input"
          formControlName="nombres"
          [class.input--error]="campoInvalido('nombres')"
        />
        @if (campoInvalido('nombres')) {
          <div class="campo-error">Ingresa los nombres (solo letras).</div>
        }
      </div>

      <div>
        <label class="label" for="apellidoPaterno">Apellido paterno</label>
        <input
          id="apellidoPaterno"
          class="input"
          formControlName="apellidoPaterno"
          [class.input--error]="campoInvalido('apellidoPaterno')"
        />
        @if (campoInvalido('apellidoPaterno')) {
          <div class="campo-error">Ingresa el apellido paterno (solo letras).</div>
        }
      </div>

      <div>
        <label class="label"><span>Apellido materno</span><span class="nota-pill">Opcional</span></label>
        <input
          id="apellidoMaterno"
          class="input"
          formControlName="apellidoMaterno"
          [class.input--error]="campoInvalido('apellidoMaterno')"
        />
        @if (campoInvalido('apellidoMaterno')) {
          <div class="campo-error">Solo letras, espacios, apóstrofe y guion.</div>
        }
      </div>

      <div>
        <label class="label" for="correo">Correo</label>
        <input
          id="correo"
          type="email"
          class="input"
          formControlName="correo"
          [class.input--error]="campoInvalido('correo')"
        />
        @if (campoInvalido('correo')) {
          <div class="campo-error">Ingresa un correo válido.</div>
        }
      </div>

      <div>
        <label class="label"><span>Teléfono</span><span class="nota-pill">Opcional</span></label>
        <input
          id="telefono"
          class="input"
          formControlName="telefono"
          placeholder="999 999 999"
          [class.input--error]="campoInvalido('telefono')"
        />
        @if (campoInvalido('telefono')) {
          <div class="campo-error">Debe tener entre 6 y 9 dígitos.</div>
        }
      </div>

      <div>
        <label class="label"><span>Documento de identidad</span><span class="nota-pill">Opcional</span></label>
        <input
          id="documentoIdentidad"
          class="input"
          formControlName="documentoIdentidad"
          placeholder="45781203"
          [class.input--error]="campoInvalido('documentoIdentidad')"
        />
        @if (campoInvalido('documentoIdentidad')) {
          <div class="campo-error">Debe tener 8 dígitos.</div>
        }
      </div>

      <div class="usuario-crear-campo--ancho">
        <label class="label">Rol</label>
        <div class="usuario-crear-roles">
          @for (rol of todosLosRoles; track rol) {
            <label class="usuario-crear-check">
              <input type="checkbox" [checked]="tieneRol(rol)" (change)="alternarRol(rol)" />
              <span>{{ etiquetaRol(rol) }}</span>
            </label>
          }
        </div>
        @if (rolesSeleccionados().length > 1) {
          <p class="usuario-crear-nota">
            Con ambos roles, Administrador queda como rol principal.
          </p>
        }
      </div>
    </div>

    <div class="usuario-crear-acciones">
      <button type="submit" class="btn btn--primario" [disabled]="guardando()">
        {{ guardando() ? 'Creando…' : 'Crear usuario' }}
      </button>
      <button type="button" class="btn btn--secundario" (click)="cerrar.emit()">Cancelar</button>
    </div>
  </form>
}
```

```scss
// usuario-crear.scss
.usuario-crear-campos {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  column-gap: 24px;
  row-gap: 22px;
  margin-bottom: 28px;
}

.usuario-crear-campo--ancho {
  grid-column: 1 / -1;
}

.usuario-crear-alerta {
  margin-bottom: 20px;
}

.usuario-crear-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 4px;
}

.usuario-crear-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--neutral-950);
  cursor: pointer;
}

.usuario-crear-check input {
  width: 16px;
  height: 16px;
  accent-color: var(--acento-700);
}

.usuario-crear-nota {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--neutral-500);
}

.usuario-crear-acciones {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 4px;
}

@media (max-width: 560px) {
  .usuario-crear-campos {
    grid-template-columns: 1fr;
  }
}
```

### 9. `usuario-detalle` completo (código real original)

```ts
// usuario-detalle.ts
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, Input, OnChanges, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  documentoOpcionalValidator,
  nombrePropioValidator,
  telefonoOpcionalValidator,
} from '../../../cuenta/mi-perfil/mi-perfil.validators';
import { AdminUsuariosApiService } from '../admin-usuarios-api.service';
import { claseEstadoCuenta, etiquetaEstadoCuenta } from '../usuario-admin-etiquetas';
import { RolUsuarioAdmin, UsuarioAdminRespuesta } from '../usuario-admin.model';

interface ErrorApiAdmin {
  code?: string;
  message?: string;
}

/** Cuánto tiempo queda visible una alerta antes de desaparecer sola. */
const DURACION_MENSAJE_MS = 5000;

/** HU-008 — Detalle administrativo de un usuario, mostrado dentro de un modal (ver
 * usuarios-listado). Habilitar/deshabilitar, conceder o retirar roles vía checkboxes, editar los
 * datos personales y reenviar el código de verificación mientras la cuenta siga pendiente. */
@Component({
  selector: 'app-usuario-detalle',
  imports: [ReactiveFormsModule],
  templateUrl: './usuario-detalle.html',
  styleUrl: './usuario-detalle.scss',
})
export class UsuarioDetalle implements OnChanges {
  @Input({ required: true }) usuarioId!: number;
  readonly cerrar = output<void>();
  /** Avisa al listado para refrescar esa fila sin recargar todo. */
  readonly actualizado = output<UsuarioAdminRespuesta>();

  private readonly api = inject(AdminUsuariosApiService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly detector = inject(ChangeDetectorRef);

  protected readonly usuario = signal<UsuarioAdminRespuesta | null | undefined>(undefined);
  protected readonly cambiandoEstado = signal(false);
  protected readonly concediendoRol = signal<RolUsuarioAdmin | null>(null);
  protected readonly reenviando = signal(false);
  protected readonly reseteandoContrasena = signal(false);
  /** Se muestra hasta que el admin la cierra a propósito: a diferencia de `mensaje`, no
   * desaparece sola, porque hay que poder copiarla. */
  protected readonly contrasenaTemporal = signal<string | null>(null);

  /** Un único slot de alerta: evita que un error de una acción quede "pegado" en pantalla
   * mientras otra acción distinta se completa con éxito. */
  protected readonly mensaje = signal<{ tipo: 'error' | 'exito'; texto: string } | null>(null);
  private mensajeTimeout: ReturnType<typeof setTimeout> | null = null;

  protected readonly editando = signal(false);
  protected readonly guardandoDatos = signal(false);
  protected readonly intentoGuardarDatos = signal(false);

  protected readonly etiquetaEstadoCuenta = etiquetaEstadoCuenta;
  protected readonly claseEstadoCuenta = claseEstadoCuenta;
  protected readonly todosLosRoles: RolUsuarioAdmin[] = ['ALUMNO', 'ADMINISTRADOR'];

  protected readonly formDatos = this.fb.group({
    nombres: ['', [Validators.required, Validators.maxLength(120), nombrePropioValidator]],
    apellidoPaterno: ['', [Validators.required, Validators.maxLength(80), nombrePropioValidator]],
    apellidoMaterno: ['', [Validators.maxLength(80), nombrePropioValidator]],
    telefono: ['', [Validators.maxLength(30), telefonoOpcionalValidator]],
    documentoIdentidad: ['', [Validators.maxLength(30), documentoOpcionalValidator]],
  });

  constructor() {
    for (const nombre of ['nombres', 'apellidoPaterno', 'apellidoMaterno'] as const) {
      const control = this.formDatos.controls[nombre];
      control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((valor) => {
        const mayuscula = valor.toLocaleUpperCase('es-PE');
        if (valor !== mayuscula) {
          control.setValue(mayuscula, { emitEvent: false });
        }
      });
    }
    this.destroyRef.onDestroy(() => this.limpiarTimeoutMensaje());
  }

  ngOnChanges(): void {
    this.cargar(this.usuarioId);
  }

  protected tieneRol(rol: RolUsuarioAdmin): boolean {
    return this.usuario()?.roles.includes(rol) ?? false;
  }

  protected etiquetaRol(rol: RolUsuarioAdmin): string {
    return rol === 'ADMINISTRADOR' ? 'Administrador' : 'Alumno';
  }

  protected alternarRol(rol: RolUsuarioAdmin): void {
    const usuario = this.usuario();
    if (!usuario || this.concediendoRol()) {
      return;
    }
    const concedia = this.tieneRol(rol);
    this.concediendoRol.set(rol);
    const peticion = concedia
      ? this.api.revocarRol(usuario.usuarioId, rol)
      : this.api.concederRol(usuario.usuarioId, { rol });
    peticion
      .pipe(
        finalize(() => {
          this.concediendoRol.set(null);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado) => {
          this.usuario.set(actualizado);
          this.actualizado.emit(actualizado);
          this.mostrarMensaje('exito', concedia ? 'Rol retirado.' : 'Rol agregado.');
        },
        error: (error: HttpErrorResponse) => {
          this.mostrarMensaje('error', this.obtenerErrorApi(error)?.message ?? 'No pudimos actualizar el rol.');
        },
      });
  }

  protected cambiarEstado(activo: boolean): void {
    const usuario = this.usuario();
    if (!usuario || this.cambiandoEstado()) {
      return;
    }
    this.cambiandoEstado.set(true);
    this.api
      .cambiarActivo(usuario.usuarioId, { activo, motivo: null })
      .pipe(
        finalize(() => {
          this.cambiandoEstado.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado) => {
          this.usuario.set(actualizado);
          this.actualizado.emit(actualizado);
          this.mostrarMensaje('exito', activo ? 'Cuenta habilitada.' : 'Cuenta deshabilitada.');
        },
        error: (error: HttpErrorResponse) => {
          this.mostrarMensaje(
            'error',
            this.obtenerErrorApi(error)?.message ?? 'No pudimos cambiar el estado de la cuenta.',
          );
        },
      });
  }

  protected reenviarHabilitacion(): void {
    const usuario = this.usuario();
    if (!usuario || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.api
      .reenviarHabilitacion(usuario.usuarioId)
      .pipe(
        finalize(() => {
          this.reenviando.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.mostrarMensaje('exito', 'Código de verificación reenviado.'),
        error: (error: HttpErrorResponse) => {
          this.mostrarMensaje(
            'error',
            this.obtenerErrorApi(error)?.message ?? 'No pudimos reenviar las instrucciones.',
          );
        },
      });
  }

  protected resetearContrasena(): void {
    const usuario = this.usuario();
    if (!usuario || this.reseteandoContrasena()) {
      return;
    }
    this.reseteandoContrasena.set(true);
    this.contrasenaTemporal.set(null);
    this.api
      .resetearContrasena(usuario.usuarioId)
      .pipe(
        finalize(() => {
          this.reseteandoContrasena.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta) => {
          this.usuario.set(respuesta.usuario);
          this.actualizado.emit(respuesta.usuario);
          this.contrasenaTemporal.set(respuesta.contrasenaTemporal);
        },
        error: (error: HttpErrorResponse) => {
          this.mostrarMensaje(
            'error',
            this.obtenerErrorApi(error)?.message ?? 'No pudimos resetear la contraseña.',
          );
        },
      });
  }

  protected cerrarContrasenaTemporal(): void {
    this.contrasenaTemporal.set(null);
  }

  protected campoDatosInvalido(
    nombre: 'nombres' | 'apellidoPaterno' | 'apellidoMaterno' | 'telefono' | 'documentoIdentidad',
  ): boolean {
    const control = this.formDatos.controls[nombre];
    return control.invalid && (control.touched || this.intentoGuardarDatos());
  }

  protected abrirEdicion(): void {
    const u = this.usuario();
    if (!u) {
      return;
    }
    this.intentoGuardarDatos.set(false);
    this.formDatos.setValue({
      nombres: u.nombres,
      apellidoPaterno: u.apellidoPaterno,
      apellidoMaterno: u.apellidoMaterno ?? '',
      telefono: u.telefono ?? '',
      documentoIdentidad: u.documentoIdentidad ?? '',
    });
    this.editando.set(true);
  }

  protected cancelarEdicion(): void {
    this.editando.set(false);
  }

  protected guardarDatos(): void {
    const usuario = this.usuario();
    if (!usuario || this.guardandoDatos()) {
      return;
    }
    this.intentoGuardarDatos.set(true);
    if (this.formDatos.invalid) {
      this.formDatos.markAllAsTouched();
      return;
    }

    const v = this.formDatos.getRawValue();
    this.guardandoDatos.set(true);
    this.api
      .actualizarDatosPersonales(usuario.usuarioId, {
        nombres: v.nombres.trim(),
        apellidoPaterno: v.apellidoPaterno.trim(),
        apellidoMaterno: this.textoOpcional(v.apellidoMaterno),
        telefono: this.textoOpcional(v.telefono)?.replace(/[\s-]/g, '') ?? null,
        documentoIdentidad: this.textoOpcional(v.documentoIdentidad),
      })
      .pipe(
        finalize(() => {
          this.guardandoDatos.set(false);
          this.detector.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado) => {
          this.usuario.set(actualizado);
          this.actualizado.emit(actualizado);
          this.editando.set(false);
          this.mostrarMensaje('exito', 'Información actualizada.');
        },
        error: (error: HttpErrorResponse) => {
          const respuesta = this.obtenerErrorApi(error);
          if (error.status === 409 && respuesta?.code === 'DUPLICATE_RESOURCE') {
            this.formDatos.controls.documentoIdentidad.setErrors({ duplicado: true });
          }
          this.mostrarMensaje(
            'error',
            respuesta?.message ?? 'No pudimos guardar los datos. Revisa los campos marcados.',
          );
        },
      });
  }

  private cargar(usuarioId: number): void {
    this.usuario.set(undefined);
    this.editando.set(false);
    this.mensaje.set(null);
    this.contrasenaTemporal.set(null);
    this.limpiarTimeoutMensaje();
    this.api
      .obtener(usuarioId)
      .pipe(
        finalize(() => this.detector.markForCheck()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (usuario) => this.usuario.set(usuario),
        error: () => this.usuario.set(null),
      });
  }

  private mostrarMensaje(tipo: 'error' | 'exito', texto: string): void {
    this.limpiarTimeoutMensaje();
    this.mensaje.set({ tipo, texto });
    this.mensajeTimeout = setTimeout(() => {
      this.mensaje.set(null);
      this.mensajeTimeout = null;
      this.detector.markForCheck();
    }, DURACION_MENSAJE_MS);
  }

  private limpiarTimeoutMensaje(): void {
    if (this.mensajeTimeout !== null) {
      clearTimeout(this.mensajeTimeout);
      this.mensajeTimeout = null;
    }
  }

  private obtenerErrorApi(error: HttpErrorResponse): ErrorApiAdmin | null {
    if (typeof error.error !== 'object' || error.error === null) {
      return null;
    }
    return error.error as ErrorApiAdmin;
  }

  private textoOpcional(valor: string): string | null {
    const limpio = valor.trim();
    return limpio.length > 0 ? limpio : null;
  }
}
```

```html
<!-- usuario-detalle.html -->
@if (usuario() === undefined) {
  <p class="usuario-detalle-estado">Cargando…</p>
} @else if (usuario() === null) {
  <div class="alerta alerta--error">
    <span class="alerta__icono">!</span>
    <div class="alerta__titulo">No encontramos esa cuenta.</div>
  </div>
} @else {
  @let u = usuario()!;

  <div class="usuario-detalle-encabezado">
    <div>
      <div class="usuario-detalle-nombre">{{ u.nombreCompleto }}</div>
      <div class="usuario-detalle-correo">{{ u.correo }}</div>
    </div>
    <span class="badge" [class]="claseEstadoCuenta(u)">{{ etiquetaEstadoCuenta(u) }}</span>
  </div>

  @if (mensaje(); as m) {
    <div class="alerta usuario-detalle-alerta" [class.alerta--error]="m.tipo === 'error'" [class.alerta--exito]="m.tipo === 'exito'">
      <span class="alerta__icono">{{ m.tipo === 'error' ? '!' : '✓' }}</span>
      <div class="alerta__titulo">{{ m.texto }}</div>
    </div>
  }

  <div class="usuario-detalle-seccion">
    <div class="usuario-detalle-seccion__cabecera">
      <h3 class="usuario-detalle-seccion__titulo">Datos personales</h3>
      @if (!editando()) {
        <button type="button" class="btn btn--secundario" (click)="abrirEdicion()">Editar</button>
      }
    </div>

    @if (editando()) {
      <form [formGroup]="formDatos" (ngSubmit)="guardarDatos()" class="usuario-detalle-form">
        <div class="usuario-detalle-campos usuario-detalle-campos--formulario">
          <div>
            <label class="label" for="ud-nombres">Nombres</label>
            <input
              id="ud-nombres"
              class="input"
              formControlName="nombres"
              [class.input--error]="campoDatosInvalido('nombres')"
            />
            @if (campoDatosInvalido('nombres')) {
              <div class="campo-error">Ingresa los nombres (solo letras).</div>
            }
          </div>
          <div>
            <label class="label" for="ud-paterno">Apellido paterno</label>
            <input
              id="ud-paterno"
              class="input"
              formControlName="apellidoPaterno"
              [class.input--error]="campoDatosInvalido('apellidoPaterno')"
            />
            @if (campoDatosInvalido('apellidoPaterno')) {
              <div class="campo-error">Ingresa el apellido paterno (solo letras).</div>
            }
          </div>
          <div>
            <label class="label"><span>Apellido materno</span><span class="nota-pill">Opcional</span></label>
            <input
              class="input"
              formControlName="apellidoMaterno"
              [class.input--error]="campoDatosInvalido('apellidoMaterno')"
            />
            @if (campoDatosInvalido('apellidoMaterno')) {
              <div class="campo-error">Solo letras, espacios, apóstrofe y guion.</div>
            }
          </div>
          <div>
            <label class="label"><span>Teléfono</span><span class="nota-pill">Opcional</span></label>
            <input
              class="input"
              formControlName="telefono"
              placeholder="999 999 999"
              [class.input--error]="campoDatosInvalido('telefono')"
            />
            @if (campoDatosInvalido('telefono')) {
              <div class="campo-error">Debe tener entre 6 y 9 dígitos.</div>
            }
          </div>
          <div class="usuario-detalle-campo--ancho">
            <label class="label"><span>Documento de identidad</span><span class="nota-pill">Opcional</span></label>
            <input
              class="input"
              formControlName="documentoIdentidad"
              placeholder="45781203"
              [class.input--error]="campoDatosInvalido('documentoIdentidad')"
            />
            @if (campoDatosInvalido('documentoIdentidad')) {
              <div class="campo-error">
                {{ formDatos.controls.documentoIdentidad.hasError('duplicado') ? 'Ese documento ya está en uso.' : 'Debe tener 8 dígitos.' }}
              </div>
            }
          </div>
        </div>
        <div class="usuario-detalle-form__acciones">
          <button type="submit" class="btn btn--primario" [disabled]="guardandoDatos()">
            {{ guardandoDatos() ? 'Guardando…' : 'Guardar' }}
          </button>
          <button type="button" class="btn btn--secundario" (click)="cancelarEdicion()">Cancelar</button>
        </div>
      </form>
    } @else {
      <div class="usuario-detalle-campos">
        <div>
          <div class="usuario-detalle-etiqueta">Nombres</div>
          <div class="usuario-detalle-valor">{{ u.nombres }}</div>
        </div>
        <div>
          <div class="usuario-detalle-etiqueta">Apellido paterno</div>
          <div class="usuario-detalle-valor">{{ u.apellidoPaterno }}</div>
        </div>
        <div>
          <div class="usuario-detalle-etiqueta">Apellido materno</div>
          <div class="usuario-detalle-valor">{{ u.apellidoMaterno || '—' }}</div>
        </div>
        <div>
          <div class="usuario-detalle-etiqueta">Teléfono</div>
          <div class="usuario-detalle-valor">{{ u.telefono || '—' }}</div>
        </div>
        <div>
          <div class="usuario-detalle-etiqueta">Documento de identidad</div>
          <div class="usuario-detalle-valor">{{ u.documentoIdentidad || '—' }}</div>
        </div>
        <div>
          <div class="usuario-detalle-etiqueta">Origen</div>
          <div class="usuario-detalle-valor">
            @switch (u.origenRegistro) {
              @case ('FORMULARIO') { Formulario }
              @case ('GOOGLE') { Google }
              @case ('ADMINISTRATIVO') { Administrativo }
            }
          </div>
        </div>
        @if (u.concedidoPorNombre) {
          <div class="usuario-detalle-campo--ancho">
            <div class="usuario-detalle-etiqueta">Rol Administrador concedido por</div>
            <div class="usuario-detalle-valor">{{ u.concedidoPorNombre }}</div>
          </div>
        }
      </div>
    }
  </div>

  <div class="usuario-detalle-seccion">
    <h3 class="usuario-detalle-seccion__titulo">Roles</h3>
    <p class="usuario-detalle-seccion__ayuda">
      Marca o desmarca los roles de la cuenta. No puedes dejarla sin ningún rol, ni retirarte tu
      propio rol de administrador, ni al último administrador habilitado.
    </p>
    <div class="usuario-detalle-roles">
      @for (rol of todosLosRoles; track rol) {
        <label class="usuario-detalle-check">
          <input
            type="checkbox"
            [checked]="tieneRol(rol)"
            [disabled]="concediendoRol() !== null"
            (change)="alternarRol(rol)"
          />
          <span>{{ etiquetaRol(rol) }}</span>
          @if (concediendoRol() === rol) {
            <span class="usuario-detalle-check__estado">Guardando…</span>
          }
        </label>
      }
    </div>
  </div>

  <div class="usuario-detalle-seccion">
    <h3 class="usuario-detalle-seccion__titulo">Acceso</h3>
    <div class="usuario-detalle-interruptor-fila">
      <label class="interruptor">
        <input
          type="checkbox"
          class="interruptor__entrada"
          [checked]="u.activo"
          [disabled]="cambiandoEstado()"
          (change)="cambiarEstado(!u.activo)"
        />
        <span class="interruptor__pista"><span class="interruptor__perilla"></span></span>
        <span class="interruptor__texto">{{ u.activo ? 'Cuenta habilitada' : 'Cuenta deshabilitada' }}</span>
      </label>

      @if (u.condicion !== 'NINGUNA' && u.condicion !== 'CAMBIO_PENDIENTE') {
        <button type="button" class="btn btn--secundario" [disabled]="reenviando()" (click)="reenviarHabilitacion()">
          Reenviar código de verificación
        </button>
      }
      <button type="button" class="btn btn--secundario" [disabled]="reseteandoContrasena()" (click)="resetearContrasena()">
        {{ reseteandoContrasena() ? 'Reseteando…' : 'Resetear contraseña' }}
      </button>
    </div>

    @if (contrasenaTemporal(); as temporal) {
      <div class="alerta alerta--exito usuario-detalle-alerta">
        <span class="alerta__icono">✓</span>
        <div>
          <div class="alerta__titulo">Contraseña reseteada</div>
          <div class="alerta__texto">
            Nueva contraseña temporal: <strong>{{ temporal }}</strong>. Se envió por correo y se
            pedirá cambiarla en el próximo ingreso. No vuelve a mostrarse después de cerrar esto.
          </div>
        </div>
        <button type="button" class="alerta__cerrar" (click)="cerrarContrasenaTemporal()" aria-label="Cerrar">✕</button>
      </div>
    }
  </div>
}
```

```scss
// usuario-detalle.scss
.usuario-detalle-estado {
  text-align: center;
  color: var(--neutral-500);
  padding: 24px 0;
}

.usuario-detalle-encabezado {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.usuario-detalle-nombre {
  font-size: 18px;
  font-weight: 700;
  color: var(--neutral-950);
}

.usuario-detalle-correo {
  font-size: 14px;
  color: var(--neutral-600);
  margin-top: 2px;
}

.usuario-detalle-alerta {
  margin-bottom: 16px;
}

.usuario-detalle-campos {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(180px, 100%), 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.usuario-detalle-campo--ancho {
  grid-column: 1 / -1;
}

.usuario-detalle-campos--formulario {
  grid-template-columns: repeat(2, 1fr);
  column-gap: 24px;
  row-gap: 22px;
  margin-bottom: 28px;
}

@media (max-width: 560px) {
  .usuario-detalle-campos--formulario {
    grid-template-columns: 1fr;
  }
}

.usuario-detalle-etiqueta {
  font-size: 12px;
  font-weight: 600;
  color: var(--neutral-500);
  margin-bottom: 4px;
}

.usuario-detalle-valor {
  font-size: 14px;
  color: var(--neutral-950);
}

.usuario-detalle-seccion {
  padding-top: 20px;
  border-top: 1px solid var(--neutral-100);
  margin-top: 20px;
}

.usuario-detalle-seccion__titulo {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-950);
}

.usuario-detalle-seccion__cabecera {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.usuario-detalle-seccion__cabecera .usuario-detalle-seccion__titulo {
  margin: 0;
}

.usuario-detalle-form__acciones {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.usuario-detalle-seccion__ayuda {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--neutral-500);
}

.usuario-detalle-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.usuario-detalle-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--neutral-950);
  cursor: pointer;
}

.usuario-detalle-check input {
  width: 16px;
  height: 16px;
  accent-color: var(--acento-700);
}

.usuario-detalle-check--deshabilitado {
  color: var(--neutral-500);
  cursor: default;
}

.usuario-detalle-check__estado {
  font-size: 12px;
  color: var(--acento-700);
}

.usuario-detalle-interruptor-fila {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
```

### 10. Comprobación incremental

1. Confirmar en Swagger que los 9 endpoints responden con un administrador autenticado y 403 con
   cualquier otro rol.
2. Probar crear con correo nuevo y con correo existente (con y sin el rol ya asignado).
3. Probar revocar el único rol, revocarse el propio admin, y revocar al último administrador activo
   — los tres deben fallar con 409.
4. Probar desactivar la propia cuenta y desactivar al último administrador activo — ambos deben
   fallar con 409.
5. Conectar Angular y verificar la paginación server-side, los modales de creación/detalle, y que
   `matriculas-listado.ts` sigue poblando su combo de alumnos sin cambios.
