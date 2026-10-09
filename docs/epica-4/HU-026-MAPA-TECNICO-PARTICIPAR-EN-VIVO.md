# HU-026 — Mapa técnico para participar en sesiones en vivo

> Estado de integración: la "sesión en vivo" **es una `Leccion` con `tipo = EN_VIVO`** — no existe una
> entidad `Sesion` separada (lo programa el admin desde HU-012, ya en producción, vía
> `ContenidoServicio.actualizarSesion`). Lo que falta por completo es el lado del **alumno**: calendario,
> apertura controlada por ventana horaria, y asistencia. La tabla `asistencia` ya existe en el script
> SQL, pero no tiene entidad ni repositorio en Java todavía — se crean en esta historia.

## Resultado que debe entregar

Un alumno de un curso `EN_VIVO` o `HIBRIDO` ve un calendario con sus sesiones, recibe un recordatorio
el día anterior, y solo dentro de la ventana de horario puede abrir el enlace real (fuera de la
ventana ve fecha/hora pero el botón está deshabilitado). Al abrir dentro de ventana se registra una
sola asistencia. El porcentaje de asistencia se calcula sobre sesiones elegibles, nunca dividiendo
entre cero.

## Punto de partida

- `Leccion` ya tiene `tipo`, `estado`, `fechaHoraInicio`, `fechaHoraFin`, `enlaceReunion`,
  `motivoCancelacion` — todo lo que describe una sesión ya vive ahí.
- `LeccionRepositorio.contarSesionesFuturasActivas(cursoId, desde)` ya existe (lo usa HU-012 para
  bloquear cambios de modalidad con sesiones futuras) — sirve de referencia para las nuevas consultas
  de calendario.
- `Matricula.fechaMatricula` ya existe y es el corte para "sesiones posteriores a su matrícula".
- La tabla `asistencia` existe en el SQL pero **no tiene entidad Java todavía** — se crea aquí.
- El patrón de notificación reutilizable (`Notificacion`, `MailService.sendHtml`,
  `NotificacionRepositorio.findTopByTipoOrderByCreadoEnDesc`) ya está resuelto en
  `MatriculaServicio.enviarConfirmacion` — se copia la misma estructura, no se inventa una nueva.

## Dependencias

- Ninguna dentro de EP04; requiere sesiones configuradas en EP02 (HU-012, ya existe) y matrícula con
  acceso de EP03.
- Puede desarrollarse en paralelo con HU-027 (esa historia solo toca el lado admin de la misma
  `Leccion`).

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Calendario del alumno | `GET /api/aula/sesiones?desde=&hasta=` | Sesiones de todos sus cursos EN_VIVO/HIBRIDO en ese rango |
| Tarjeta de una sesión | `GET /api/aula/sesiones/{leccionId}` | Estado antes/durante/después, enlace solo si corresponde |
| Abrir sesión | `POST /api/aula/sesiones/{leccionId}/abrir` | Registra asistencia una vez y entrega el enlace real |
| Porcentaje de asistencia de un curso | `GET /api/aula/cursos/{cursoId}/asistencia` | Número o "No aplica" |

El enlace real (`enlaceReunion`) **nunca viaja en `GET /sesiones` ni en el recordatorio por correo**:
solo lo entrega `POST /abrir`, y solo dentro de la ventana.

## Trabajo del backend

### Archivos y responsabilidades

- `entity/Asistencia.java` (nueva, mapea la tabla `asistencia` ya existente).
- `repository/AsistenciaRepositorio.java` (nuevo).
- `dto/SesionCalendarioRespuesta.java`, `dto/SesionTarjetaRespuesta.java`,
  `dto/AsistenciaCursoRespuesta.java`.
- `service/SesionesEnVivoServicio.java` (nuevo): calendario, tarjeta, abrir, porcentaje.
- `service/RecordatorioSesionServicio.java` (nuevo): job programado del aviso del día anterior.
- `controller/AulaControlador.java`: se amplía con los cuatro endpoints de esta historia.

### La entidad que falta

```java
package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asistencia", uniqueConstraints = @UniqueConstraint(columnNames = {"matricula_id", "leccion_id"}))
@Getter @Setter
public class Asistencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asistencia_id")
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "matricula_id")
    private Matricula matricula;

    @ManyToOne(optional = false) @JoinColumn(name = "leccion_id")
    private Leccion leccion;

    @Column(nullable = false, length = 15)
    private String estado = "PENDIENTE"; // PENDIENTE | PRESENTE | AUSENTE | JUSTIFICADA

    @Column(name = "registrada_por_usuario_id")
    private Long registradaPorUsuarioId;

    @Column(name = "motivo_correccion")
    private String motivoCorreccion;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    @Column(name = "modificada_en")
    private Instant modificadaEn;
}
```

```java
public interface AsistenciaRepositorio extends JpaRepository<Asistencia, Long> {
    Optional<Asistencia> findByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);

    @Query("""
        select count(a) from Asistencia a
        where a.matricula.id = :matriculaId and a.estado = 'PRESENTE'
        """)
    long contarAsistidas(@Param("matriculaId") Long matriculaId);
}
```

### Reglas en orden — calendario y tarjeta

1. El calendario solo incluye lecciones `tipo = EN_VIVO` de cursos con `modalidad` en
   (`EN_VIVO`, `HIBRIDO`) donde el alumno tenga una matrícula con acceso efectivo.
2. La tarjeta tiene tres estados según `Instant.now()` comparado con `fechaHoraInicio`/`fechaHoraFin`
   en zona `America/Lima`: `ANTES` (fecha/hora visible, botón deshabilitado, sin enlace),
   `DURANTE` (botón habilitado), `DESPUES` (muestra grabación si `Recurso` de grabación existe, si no
   "grabación pendiente").
3. Una sesión `estado = CANCELADA` se muestra igual (tachada/marcada), nunca desaparece, y queda
   excluida del cálculo de asistencia.

### Reglas en orden — abrir sesión

1. Resolver la matrícula del alumno para el curso de esa lección; exigir acceso efectivo (reutilizar
   `AulaServicio.motivoSinAccesoPublico`).
2. Validar `fechaHoraInicio <= ahora <= fechaHoraFin` en `America/Lima`; fuera de ventana,
   `BusinessValidationException` sin entregar el enlace.
3. Buscar o crear el registro de `Asistencia` (`findByMatricula_IdAndLeccion_Id`); si no existe, crear
   uno con `estado = PRESENTE`; si ya existe con `PRESENTE`, no se duplica (se entrega el enlace igual,
   abrir dos veces dentro de la ventana no cuenta dos asistencias).
4. Devolver `enlaceReunion` solo después de los tres pasos anteriores.

### Reglas en orden — porcentaje de asistencia

1. Sesiones elegibles de un curso para un alumno: `tipo = EN_VIVO`, `estado <> CANCELADA`,
   `fechaHoraInicio` posterior a `matricula.fechaMatricula`.
2. Si no hay sesiones elegibles, responder `"No aplica"` (una cadena, no un número) — el frontend lo
   muestra tal cual y, si `ReglaCurso.requiereAsistencia = true`, no lo trata como requisito cumplido.
3. Si hay elegibles, `porcentaje = asistidas_elegibles / total_elegibles * 100`, redondeado.

### El recordatorio (job programado)

```java
package pe.edu.utp.escuela.app.service;

import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Notificacion;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.*;

@Service
@RequiredArgsConstructor
public class RecordatorioSesionServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepositorio lecciones;
    private final MatriculaRepositorio matriculas;
    private final NotificacionRepositorio notificaciones;
    private final MailService mailService;
    private final Clock clock;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 30_000) // cada hora
    @Transactional
    public void enviarRecordatoriosDelDiaSiguiente() {
        LocalDate manana = LocalDate.now(clock.withZone(LIMA)).plusDays(1);
        Instant desde = manana.atStartOfDay(LIMA).toInstant();
        Instant hasta = manana.plusDays(1).atStartOfDay(LIMA).toInstant();

        List<Leccion> sesionesManana = lecciones.buscarEnVivoEntre(desde, hasta); // nuevo metodo
        for (Leccion sesion : sesionesManana) {
            String tipo = "RECORDATORIO_SESION_" + sesion.getId();
            if (notificaciones.findTopByTipoOrderByCreadoEnDesc(tipo).isPresent()) {
                continue; // ya se envió para esta sesión
            }
            List<Matricula> alumnos = matriculas
                    .findByEstadoAndCurso_Id("ACTIVA", sesion.getModulo().getCurso().getId());
            for (Matricula m : alumnos) {
                enviarUno(m, sesion, tipo);
            }
        }
    }

    private void enviarUno(Matricula m, Leccion sesion, String tipo) {
        Notificacion n = new Notificacion();
        n.setUsuario(m.getUsuario());
        n.setTipo(tipo);
        n.setDestinatario(m.getUsuario().getCorreo());
        n.setAsunto("Recordatorio: " + sesion.getTitulo() + " mañana");
        try {
            mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                    "mail/recordatorio-sesion.html", Map.of(
                            "curso", sesion.getModulo().getCurso().getTitulo(),
                            "sesion", sesion.getTitulo(),
                            "fecha", sesion.getFechaHoraInicio())));
            n.setEstadoEnvio("ENVIADO");
            n.setEnviadoEn(clock.instant());
        } catch (Exception e) {
            n.setEstadoEnvio("ERROR");
            n.setUltimoError(e.getMessage());
        }
        notificaciones.save(n);
    }
}
```

El enlace real **no se incluye** en la plantilla `mail/recordatorio-sesion.html` — solo curso, sesión
y fecha/hora; el alumno entra a ESEJUR y recién ahí, dentro de la ventana, ve el botón habilitado.

Agregar a `LeccionRepositorio`:

```java
@Query("""
    select l from Leccion l
    where l.tipo = 'EN_VIVO' and l.activo = true and l.estado <> 'CANCELADA'
      and l.fechaHoraInicio >= :desde and l.fechaHoraInicio < :hasta
    """)
List<Leccion> buscarEnVivoEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
```

Agregar a `MatriculaRepositorio`: `List<Matricula> findByEstadoAndCurso_Id(String estado, Long cursoId)`.

## Trabajo del frontend

### Calendario (`features/aula/calendario`, nuevo)

- Maquetar según `maquetacion-html/HU-026-PF-CALENDARIO-calendario.html`.
- Vista mensual con navegación y botón "Hoy".
- Si el alumno no tiene ningún curso `EN_VIVO`/`HIBRIDO`, no se muestra un calendario vacío: se oculta
  la sección completa (criterio de aceptación explícito de la historia).

### Tarjeta de sesión (componente compartido con HU-022)

- Mismas clases CSS que la tarjeta embebida en el aula de HU-022 — coordinar nombres antes de
  maquetar para no duplicar estilos.
- Los tres estados (antes/durante/después) se resuelven con la hora del **servidor** (la respuesta ya
  trae el estado calculado), nunca con `new Date()` del navegador, para no depender del reloj del
  alumno.

### Servicio Angular

```ts
// features/aula/sesiones-api.service.ts
obtenerCalendario(desde: string, hasta: string) {
  return this.http.get<SesionCalendario[]>(`${API_URL}/aula/sesiones`, { params: { desde, hasta } });
}
abrirSesion(leccionId: number) {
  return this.http.post<{ enlaceReunion: string }>(`${API_URL}/aula/sesiones/${leccionId}/abrir`, {});
}
```

El botón "Unirme" llama a `abrirSesion`, y recién con la respuesta hace `window.open(enlaceReunion)` —
nunca navega a un enlace que ya tenía guardado del lado del cliente.

## Pruebas mínimas

- abrir dentro de ventana registra una asistencia;
- abrir dos veces dentro de ventana no duplica la asistencia;
- abrir fuera de ventana no registra ni entrega enlace;
- sesión cancelada excluida del cálculo de asistencia;
- matrícula tardía: sesiones anteriores a la matrícula no cuentan como elegibles;
- cero sesiones elegibles → "No aplica", nunca una división por cero;
- recordatorio no se reenvía dos veces para la misma sesión;
- alumno solo `VIRTUAL` no ve la sección de calendario.

## Terminado cuando

Los cuatro endpoints funcionan de punta a punta: calendario, tarjeta con sus tres estados, apertura
con asistencia única, y porcentaje con el caso "No aplica" cubierto. El recordatorio se demuestra con
una sesión programada para el día siguiente y no se duplica en una segunda ejecución del job.

## Implementación guiada para copiar y adaptar

### 1. Archivos que se crean en orden

1. `entity/Asistencia.java` + `repository/AsistenciaRepositorio.java`.
2. Métodos nuevos en `LeccionRepositorio` y `MatriculaRepositorio` (ver arriba).
3. `dto/SesionCalendarioRespuesta.java`, `dto/SesionTarjetaRespuesta.java`,
   `dto/AsistenciaCursoRespuesta.java`.
4. `service/SesionesEnVivoServicio.java`.
5. `service/RecordatorioSesionServicio.java`.
6. Métodos nuevos en `controller/AulaControlador.java`.
7. Angular: `features/aula/sesiones-api.service.ts`, `features/aula/calendario/*`, componente de
   tarjeta compartido.

### 2. DTOs

```java
public record SesionCalendarioRespuesta(
        Long leccionId, Long cursoId, String cursoTitulo, String tituloSesion,
        Instant fechaHoraInicio, Instant fechaHoraFin, String estadoVentana // "ANTES"|"DURANTE"|"DESPUES"
) {}

public record SesionTarjetaRespuesta(
        Long leccionId, String tituloSesion, Instant fechaHoraInicio, Instant fechaHoraFin,
        String estadoVentana, boolean yaAsistio, String urlGrabacion // null si no hay
) {}

public record AsistenciaCursoRespuesta(String porcentaje /* "No aplica" o "83" */, int asistidas, int elegibles) {}
```

### 3. `SesionesEnVivoServicio`

```java
package pe.edu.utp.escuela.app.service;

import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.repository.*;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class SesionesEnVivoServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepositorio lecciones;
    private final MatriculaRepositorio matriculas;
    private final AsistenciaRepositorio asistencias;
    private final AulaServicio aulaServicio;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SesionCalendarioRespuesta> calendario(Instant desde, Instant hasta) {
        Long usuarioId = actual.get().userId();
        return lecciones.buscarEnVivoDeAlumnoEnRango(usuarioId, desde, hasta).stream()
                .map(l -> new SesionCalendarioRespuesta(l.getId(), l.getModulo().getCurso().getId(),
                        l.getModulo().getCurso().getTitulo(), l.getTitulo(),
                        l.getFechaHoraInicio(), l.getFechaHoraFin(), estadoVentana(l)))
                .toList();
    }

    @Transactional
    public String abrir(Long leccionId) {
        Leccion l = lecciones.findById(leccionId)
                .orElseThrow(() -> new BusinessValidationException("La sesión no existe."));
        Matricula m = matriculaConAcceso(l);

        if (!"DURANTE".equals(estadoVentana(l))) {
            throw new BusinessValidationException("Esta sesión no está disponible en este momento.");
        }

        Asistencia a = asistencias.findByMatricula_IdAndLeccion_Id(m.getId(), leccionId)
                .orElseGet(() -> {
                    Asistencia nueva = new Asistencia();
                    nueva.setMatricula(m);
                    nueva.setLeccion(l);
                    nueva.setRegistradaEn(clock.instant());
                    return nueva;
                });
        if (!"PRESENTE".equals(a.getEstado())) {
            a.setEstado("PRESENTE");
            a.setModificadaEn(clock.instant());
            asistencias.save(a);
        }

        return l.getEnlaceReunion();
    }

    @Transactional(readOnly = true)
    public AsistenciaCursoRespuesta porcentaje(Long cursoId) {
        Matricula m = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ForbiddenException("No tienes matrícula en este curso."));

        List<Leccion> elegibles = lecciones.buscarEnVivoElegibles(cursoId, m.getFechaMatricula());
        if (elegibles.isEmpty()) {
            return new AsistenciaCursoRespuesta("No aplica", 0, 0);
        }
        long asistidas = elegibles.stream()
                .filter(l -> asistencias.findByMatricula_IdAndLeccion_Id(m.getId(), l.getId())
                        .map(a -> "PRESENTE".equals(a.getEstado())).orElse(false))
                .count();
        int porcentaje = (int) Math.round(asistidas * 100.0 / elegibles.size());
        return new AsistenciaCursoRespuesta(String.valueOf(porcentaje), (int) asistidas, elegibles.size());
    }

    private Matricula matriculaConAcceso(Leccion l) {
        Long cursoId = l.getModulo().getCurso().getId();
        Matricula m = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ForbiddenException("No tienes acceso a este curso."));
        String motivo = aulaServicio.motivoSinAccesoPublico(m);
        if (motivo != null) throw new ForbiddenException(motivo);
        return m;
    }

    private String estadoVentana(Leccion l) {
        Instant ahora = clock.instant();
        if (ahora.isBefore(l.getFechaHoraInicio())) return "ANTES";
        if (ahora.isAfter(l.getFechaHoraFin())) return "DESPUES";
        return "DURANTE";
    }
}
```

Dos consultas nuevas en `LeccionRepositorio` (seguir el estilo de `buscarActivasDeModulos`):

```java
@Query("""
    select l from Leccion l
    join l.modulo mo join mo.curso c join c.matriculas ma
    where ma.usuario.id = :usuarioId and ma.estado = 'ACTIVA'
      and c.modalidad in ('EN_VIVO','HIBRIDO') and l.tipo = 'EN_VIVO' and l.activo = true
      and l.fechaHoraInicio between :desde and :hasta
    order by l.fechaHoraInicio
    """)
List<Leccion> buscarEnVivoDeAlumnoEnRango(@Param("usuarioId") Long usuarioId,
        @Param("desde") Instant desde, @Param("hasta") Instant hasta);

@Query("""
    select l from Leccion l join l.modulo mo
    where mo.curso.id = :cursoId and l.tipo = 'EN_VIVO' and l.activo = true
      and l.estado <> 'CANCELADA' and l.fechaHoraInicio > :fechaMatricula
    """)
List<Leccion> buscarEnVivoElegibles(@Param("cursoId") Long cursoId, @Param("fechaMatricula") Instant fechaMatricula);
```

> Nota: si `Curso` no tiene hoy una colección `matriculas` mapeada para el `join` de arriba, resolver
> `buscarEnVivoDeAlumnoEnRango` en dos pasos desde el servicio (primero los cursos del alumno vía
> `MatriculaRepositorio`, después las lecciones de esos cursos) en vez de forzar un join inexistente.

### 4. Controlador: métodos nuevos sobre `AulaControlador`

```java
private final SesionesEnVivoServicio sesionesEnVivoServicio;

@GetMapping("/sesiones")
public List<SesionCalendarioRespuesta> calendario(
        @RequestParam Instant desde, @RequestParam Instant hasta) {
    return sesionesEnVivoServicio.calendario(desde, hasta);
}

@PostMapping("/sesiones/{leccionId}/abrir")
public Map<String, String> abrir(@PathVariable Long leccionId) {
    return Map.of("enlaceReunion", sesionesEnVivoServicio.abrir(leccionId));
}

@GetMapping("/cursos/{cursoId}/asistencia")
public AsistenciaCursoRespuesta asistencia(@PathVariable Long cursoId) {
    return sesionesEnVivoServicio.porcentaje(cursoId);
}
```

### 5. Comprobación incremental

1. Probar `SesionesEnVivoServicio.abrir` con `Clock.fixed(...)` antes, durante y después de la
   ventana.
2. Probar que abrir dos veces durante la ventana no crea dos filas de `Asistencia` (verificar con
   `AsistenciaRepositorio.findByMatricula_IdAndLeccion_Id` después de la segunda llamada).
3. Probar `porcentaje` con cero, algunas y todas las sesiones asistidas, y con cero elegibles.
4. Ejecutar `RecordatorioSesionServicio` dos veces seguidas en una prueba y confirmar que
   `NotificacionRepositorio` solo tiene una fila para esa sesión.
5. Conectar Angular: confirmar que el botón "Unirme" aparece deshabilitado fuera de ventana y que al
   habilitarse pide el enlace recién al hacer clic, no antes.
