# HU-027 — Mapa técnico para administrar cambios en sesiones en vivo

> Estado de integración: la sesión que se reprograma/cancela es la misma `Leccion` con `tipo=EN_VIVO`
> que ya programa `ContenidoServicio.actualizarSesion` (HU-012, en producción). Esta historia no
> reemplaza ese método — lo complementa con reprogramar-con-motivo, cancelar, y las correcciones
> posteriores a una sesión ya realizada. Dos piezas nuevas: la tabla `historial_sesion` (existe en el
> SQL, sin entidad Java todavía) y la reutilización de `MaterialLeccion` para la grabación (no se crea
> una columna nueva: la grabación es un material más de esa lección).
>
> El recálculo de "fecha de emisión de certificados aún no emitidos" depende de cómo EP05
> (Evaluación y Certificación, todavía no planificada) modele la emisión de certificados. Este mapa dc
> deja un método con un `TODO` explícito en vez de inventar una tabla que podría no coincidir con el
> diseño futuro de esa épica.

## Resultado que debe entregar

El administrador reprograma o cancela una sesión futura con motivo obligatorio, y el sistema conserva
el horario anterior, actualiza lo vigente y avisa al alumno. Una sesión ya realizada no se puede
reprogramar ni cancelar, pero sí se le puede corregir el enlace, agregar la grabación o corregir una
asistencia puntual dejando registro del ajuste.

## Punto de partida

- `Leccion.estado`, `fechaHoraInicio/Fin`, `enlaceReunion`, `motivoCancelacion` ya existen.
- `ContenidoServicio.actualizarSesion` ya valida curso no `VIRTUAL` y fechas dentro del rango del
  curso — esta historia reutiliza esas mismas validaciones, no las repite distinto.
- La tabla `historial_sesion` existe en el script SQL, sin entidad Java: se crea aquí.
- `Asistencia` (entidad nueva de HU-026) ya trae `motivoCorreccion` — ese campo es exactamente para
  la corrección posterior que pide esta historia; si HU-026 todavía no se integró, coordinar con ese
  equipo antes de tocar la misma tabla.
- El patrón de notificación y de `HistorialEstadoMatricula` de `MatriculaServicio` es la plantilla a
  copiar para el aviso y el historial de esta historia.

## Dependencias

- Ninguna dentro de EP04; requiere una sesión creada en EP02 (HU-012, ya existe).
- Puede desarrollarse en paralelo con HU-026 (ambas tocan `Leccion`, pero HU-026 solo lee/escribe
  `Asistencia`, no `Leccion`).

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Reprogramar | `PATCH /api/admin/lecciones/{id}/sesion/reprogramar` | Horario actualizado, anterior conservado, aviso enviado |
| Cancelar | `PATCH /api/admin/lecciones/{id}/sesion/cancelar` | Sesión CANCELADA, excluida, aviso enviado |
| Corregir enlace de una realizada | `PATCH /api/admin/lecciones/{id}/sesion/enlace` | Solo el enlace cambia, sin tocar fechas |
| Corregir una asistencia | `PATCH /api/admin/asistencias/{id}` | Estado corregido con motivo registrado |
| Historial de cambios de una sesión | `GET /api/admin/lecciones/{id}/sesion/historial` | Lista de reprogramaciones/cancelaciones anteriores |

La grabación **no tiene endpoint propio**: se agrega con los endpoints de materiales que ya existen
(`POST /api/admin/lecciones/{id}/materiales` o `/materiales/archivo`), igual que cualquier otro
material de esa lección.

## Trabajo del backend

### Archivos y responsabilidades

- `entity/HistorialSesion.java` (nueva).
- `repository/HistorialSesionRepositorio.java` (nuevo).
- `dto/ReprogramarSesionPeticion.java`, `dto/CancelarSesionPeticion.java`,
  `dto/CorregirEnlaceSesionPeticion.java`, `dto/CorregirAsistenciaPeticion.java`,
  `dto/HistorialSesionRespuesta.java`.
- `service/AdministrarSesionesServicio.java` (nuevo).
- `controller/ContenidoControlador.java`: se amplían los endpoints de sesión (ya existe el controlador
  admin de contenido desde HU-011/HU-012; no se crea uno paralelo).

### La entidad que falta

```java
package pe.edu.utp.escuela.app.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "historial_sesion")
@Getter @Setter
public class HistorialSesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historial_sesion_id")
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "leccion_id")
    private Leccion leccion;

    @Column(nullable = false, length = 20)
    private String accion; // REPROGRAMACION | CANCELACION

    @Column(name = "inicio_anterior") private Instant inicioAnterior;
    @Column(name = "fin_anterior") private Instant finAnterior;
    @Column(name = "enlace_anterior") private String enlaceAnterior;
    @Column(name = "inicio_nuevo") private Instant inicioNuevo;
    @Column(name = "fin_nuevo") private Instant finNuevo;
    @Column(name = "enlace_nuevo") private String enlaceNuevo;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "realizado_por_usuario_id", nullable = false)
    private Long realizadoPorUsuarioId;

    @Column(name = "realizado_en", nullable = false)
    private Instant realizadoEn;
}
```

```java
public interface HistorialSesionRepositorio extends JpaRepository<HistorialSesion, Long> {
    List<HistorialSesion> findByLeccion_IdOrderByRealizadoEnDesc(Long leccionId);
}
```

### Reglas en orden — reprogramar

1. Exigir administrador (`exigirAdministrador()`, mismo helper que ya usa `ContenidoServicio`).
2. Cargar la lección; si `fechaHoraFin` ya pasó, `BusinessValidationException` ("No puedes reprogramar
   una sesión ya realizada.").
3. Si `estado = CANCELADA`, mismo rechazo (una sesión cancelada no se reprograma, se reemplaza por una
   nueva si hace falta — eso es una decisión manual del admin, no de esta historia).
4. Validar `motivo` no vacío.
5. Validar `nuevaFechaHoraFin > nuevaFechaHoraInicio`.
6. Si `nuevaFechaHoraFin` (convertida a `LocalDate` en `America/Lima`) es posterior a
   `curso.fechaFin`: **no guardar nada todavía** y devolver un resultado que pida ampliar primero la
   fecha de fin del curso (ver DTO de respuesta). El frontend muestra ese mensaje y ofrece el campo
   para ampliar la fecha; al confirmarla, este mismo endpoint se vuelve a llamar.
7. Si la fecha es válida: guardar un `HistorialSesion` con los valores *anteriores* de la lección,
   actualizar `fechaHoraInicio/Fin` en la lección, llamar a `recalcularFechasCertificadosPendientes`
   (hook, ver abajo) si la fecha de fin del curso cambió en este mismo flujo, y enviar el aviso.
8. El aviso se envía **aunque el recordatorio del horario anterior ya hubiera salido** — no hay
   deduplicación aquí (a diferencia del recordatorio de HU-026, que si no se dedup, correspondería a
   otra sesión).

### Reglas en orden — cancelar

1. Exigir administrador; mismas validaciones de "no realizada, no ya cancelada" que reprogramar.
2. Exigir `motivo`.
3. Guardar `HistorialSesion` (`accion = CANCELACION`, solo campos "anteriores" llenos).
4. `leccion.setEstado("CANCELADA")`, `leccion.setMotivoCancelacion(motivo)`.
5. No se toca `curso.horasAcademicas` ni `curso.fechaFin` automáticamente — si la cancelación reduce lo
   ofrecido, el endpoint devuelve una advertencia textual para que el admin revise esos valores a
   mano (nunca se recalculan solos).
6. Enviar aviso. El recordatorio de HU-026 nunca se dispara para esta sesión porque su consulta ya
   filtra `estado <> 'CANCELADA'` — no hace falta borrar nada aparte.

### Reglas en orden — sesión ya realizada

1. `fechaHoraFin` ya pasó y `estado <> CANCELADA` → se permite **solo** corregir enlace (nuevo
   endpoint) y agregar/reemplazar grabación (endpoints de materiales ya existentes).
2. Corregir asistencia: valida que la `Asistencia` pertenezca a esa lección, cambia `estado`, exige
   `motivoCorreccion` no vacío, actualiza `modificadaEn`. Nunca se borra el registro original: el
   ajuste queda en el mismo campo.

### El hook de certificados pendientes

```java
// TODO: EP05 (Evaluación y Certificación) todavía no define cómo se modela la emisión de
// certificados. Por ahora este método solo documenta el punto de extensión: cuando esa épica exista,
// aquí se recalcula la fecha programada de los certificados de matrículas con
// logroCertificacion != null y aún no emitido, para las matrículas de este curso.
private void recalcularFechasCertificadosPendientes(Long cursoId, LocalDate nuevaFechaFin) {
    // Intencionalmente vacío hasta que EP05 defina la entidad de certificado.
}
```

## Trabajo del frontend

### Pantalla admin (dentro del editor de curso ya existente)

- Maquetar según `maquetacion-html/HU-027-PF-ADMIN-sesiones.html`.
- Reutilizar la tabla/lista de sesiones que ya arma la pestaña de contenido del editor de curso
  (HU-012); agregar ahí los botones "Reprogramar" y "Cancelar" solo quando la sesión sea futura, y
  "Corregir" (enlace/grabación/asistencia) solo cuando ya fue realizada.
- Modal de reprogramar: campos de nueva fecha/hora + motivo obligatorio. Si el backend responde
  pidiendo ampliar la fecha de fin del curso, mostrar ese campo en el mismo modal antes de reintentar.
- Modal de cancelar: motivo obligatorio + texto de advertencia ("esta acción no se puede deshacer").
- Historial visible: lista simple con `accion`, fechas antes/después y motivo, ordenada más reciente
  primero.

### Servicio Angular

```ts
// features/admin/cursos/sesiones-admin-api.service.ts
reprogramar(leccionId: number, body: { nuevaFechaHoraInicio: string; nuevaFechaHoraFin: string; motivo: string }) {
  return this.http.patch<ReprogramarResultado>(`${API_URL}/admin/lecciones/${leccionId}/sesion/reprogramar`, body);
}
cancelar(leccionId: number, motivo: string) {
  return this.http.patch<void>(`${API_URL}/admin/lecciones/${leccionId}/sesion/cancelar`, { motivo });
}
corregirEnlace(leccionId: number, enlaceReunion: string) {
  return this.http.patch<void>(`${API_URL}/admin/lecciones/${leccionId}/sesion/enlace`, { enlaceReunion });
}
corregirAsistencia(asistenciaId: number, body: { nuevoEstado: string; motivoCorreccion: string }) {
  return this.http.patch<void>(`${API_URL}/admin/asistencias/${asistenciaId}`, body);
}
historial(leccionId: number) {
  return this.http.get<HistorialSesionItem[]>(`${API_URL}/admin/lecciones/${leccionId}/sesion/historial`);
}
```

## Pruebas mínimas

- reprogramar una sesión futura: conserva anterior, actualiza vigente, aviso enviado;
- reprogramar con fecha posterior al fin del curso: rechazada hasta ampliar el fin, luego exitosa;
- cancelar una sesión futura: queda CANCELADA, excluida, no reversible desde este endpoint;
- intentar reprogramar o cancelar una sesión ya realizada: rechazado en ambos casos;
- corregir enlace de una realizada: permitido, no cambia fechas;
- corregir una asistencia: cambia estado y guarda motivo, no borra el registro original;
- historial muestra ambas acciones en orden correcto.

## Terminado cuando

Reprogramar, cancelar y corregir una sesión ya realizada funcionan de punta a punta, con el historial
visible y el aviso enviado en los dos primeros casos. El bloqueo por fecha de fin del curso se
demuestra pidiendo ampliarla antes de poder guardar.

## Implementación guiada para copiar y adaptar

### 1. Archivos que se crean en orden

1. `entity/HistorialSesion.java` + `repository/HistorialSesionRepositorio.java`.
2. `dto/ReprogramarSesionPeticion.java`, `dto/ReprogramarSesionResultado.java`,
   `dto/CancelarSesionPeticion.java`, `dto/CorregirEnlaceSesionPeticion.java`,
   `dto/CorregirAsistenciaPeticion.java`, `dto/HistorialSesionRespuesta.java`.
3. `service/AdministrarSesionesServicio.java`.
4. Métodos nuevos en `controller/ContenidoControlador.java`.
5. Angular: `features/admin/cursos/sesiones-admin-api.service.ts` + modales en la pestaña de contenido
   del editor de curso.

### 2. DTOs

```java
public record ReprogramarSesionPeticion(Instant nuevaFechaHoraInicio, Instant nuevaFechaHoraFin, String motivo) {}

public record ReprogramarSesionResultado(
        boolean aplicado,
        boolean requiereAmpliarFinDeCurso,
        LocalDate fechaFinCursoSugerida // = nuevaFechaHoraFin en fecha, solo informativo
) {}

public record CancelarSesionPeticion(String motivo) {}
public record CorregirEnlaceSesionPeticion(String enlaceReunion) {}
public record CorregirAsistenciaPeticion(String nuevoEstado, String motivoCorreccion) {}

public record HistorialSesionRespuesta(
        String accion, Instant inicioAnterior, Instant finAnterior, Instant inicioNuevo, Instant finNuevo,
        String motivo, Long realizadoPorUsuarioId, Instant realizadoEn
) {}
```

### 3. `AdministrarSesionesServicio`

```java
package pe.edu.utp.escuela.app.service;

import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.mail.HtmlMailMessage;
import pe.edu.utp.escuela.app.mail.MailService;
import pe.edu.utp.escuela.app.repository.*;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class AdministrarSesionesServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LeccionRepositorio lecciones;
    private final CursoRepositorio cursos;
    private final MatriculaRepositorio matriculas;
    private final AsistenciaRepositorio asistencias;
    private final HistorialSesionRepositorio historial;
    private final NotificacionRepositorio notificaciones;
    private final MailService mailService;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional
    public ReprogramarSesionResultado reprogramar(Long leccionId, ReprogramarSesionPeticion p) {
        exigirAdministrador();
        Leccion l = leccionSesionEditable(leccionId);

        if (p.motivo() == null || p.motivo().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la reprogramación.");
        }
        if (!p.nuevaFechaHoraFin().isAfter(p.nuevaFechaHoraInicio())) {
            throw new BusinessValidationException("La hora de fin debe ser posterior al inicio.");
        }

        Curso curso = l.getModulo().getCurso();
        LocalDate finNuevo = p.nuevaFechaHoraFin().atZone(LIMA).toLocalDate();
        if (curso.getFechaFin() != null && finNuevo.isAfter(curso.getFechaFin())) {
            return new ReprogramarSesionResultado(false, true, finNuevo);
        }

        Instant inicioAnterior = l.getFechaHoraInicio();
        Instant finAnterior = l.getFechaHoraFin();
        String enlaceAnterior = l.getEnlaceReunion();

        l.setFechaHoraInicio(p.nuevaFechaHoraInicio());
        l.setFechaHoraFin(p.nuevaFechaHoraFin());
        lecciones.save(l);

        guardarHistorial(l, "REPROGRAMACION", inicioAnterior, finAnterior, enlaceAnterior,
                p.nuevaFechaHoraInicio(), p.nuevaFechaHoraFin(), enlaceAnterior, p.motivo());
        avisarCambio(l, "Tu sesión fue reprogramada", p.motivo());

        return new ReprogramarSesionResultado(true, false, null);
    }

    @Transactional
    public void ampliarFinDeCurso(Long cursoId, LocalDate nuevaFechaFin) {
        exigirAdministrador();
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> new BusinessValidationException("El curso no existe."));
        if (curso.getFechaFin() != null && nuevaFechaFin.isBefore(curso.getFechaFin())) {
            throw new BusinessValidationException("La nueva fecha de fin no puede ser anterior a la actual.");
        }
        curso.setFechaFin(nuevaFechaFin);
        cursos.save(curso);
        // recalcularFechasCertificadosPendientes(cursoId, nuevaFechaFin); // ver TODO de EP05
    }

    @Transactional
    public void cancelar(Long leccionId, CancelarSesionPeticion p) {
        exigirAdministrador();
        Leccion l = leccionSesionEditable(leccionId);
        if (p.motivo() == null || p.motivo().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la cancelación.");
        }

        guardarHistorial(l, "CANCELACION", l.getFechaHoraInicio(), l.getFechaHoraFin(),
                l.getEnlaceReunion(), null, null, null, p.motivo());

        l.setEstado("CANCELADA");
        l.setMotivoCancelacion(p.motivo());
        lecciones.save(l);

        avisarCambio(l, "Tu sesión fue cancelada", p.motivo());
    }

    @Transactional
    public void corregirEnlace(Long leccionId, CorregirEnlaceSesionPeticion p) {
        exigirAdministrador();
        Leccion l = lecciones.findById(leccionId)
                .orElseThrow(() -> new BusinessValidationException("La sesión no existe."));
        if (l.getFechaHoraFin() == null || l.getFechaHoraFin().isAfter(clock.instant())) {
            throw new BusinessValidationException("Solo se corrige el enlace de una sesión ya realizada.");
        }
        l.setEnlaceReunion(p.enlaceReunion());
        lecciones.save(l);
    }

    @Transactional
    public void corregirAsistencia(Long asistenciaId, CorregirAsistenciaPeticion p) {
        exigirAdministrador();
        Asistencia a = asistencias.findById(asistenciaId)
                .orElseThrow(() -> new BusinessValidationException("La asistencia no existe."));
        if (p.motivoCorreccion() == null || p.motivoCorreccion().isBlank()) {
            throw new BusinessValidationException("Indica el motivo de la corrección.");
        }
        a.setEstado(p.nuevoEstado());
        a.setMotivoCorreccion(p.motivoCorreccion());
        a.setRegistradaPorUsuarioId(actual.get().userId());
        a.setModificadaEn(clock.instant());
        asistencias.save(a);
    }

    @Transactional(readOnly = true)
    public List<HistorialSesionRespuesta> historial(Long leccionId) {
        return historial.findByLeccion_IdOrderByRealizadoEnDesc(leccionId).stream()
                .map(h -> new HistorialSesionRespuesta(h.getAccion(), h.getInicioAnterior(),
                        h.getFinAnterior(), h.getInicioNuevo(), h.getFinNuevo(), h.getMotivo(),
                        h.getRealizadoPorUsuarioId(), h.getRealizadoEn()))
                .toList();
    }

    private Leccion leccionSesionEditable(Long leccionId) {
        Leccion l = lecciones.findById(leccionId)
                .orElseThrow(() -> new BusinessValidationException("La sesión no existe."));
        if (!"EN_VIVO".equals(l.getTipo())) {
            throw new BusinessValidationException("Esta lección no es una sesión en vivo.");
        }
        if (l.getFechaHoraFin() != null && !l.getFechaHoraFin().isAfter(clock.instant())) {
            throw new BusinessValidationException("No puedes modificar una sesión ya realizada.");
        }
        if ("CANCELADA".equals(l.getEstado())) {
            throw new BusinessValidationException("Esta sesión ya está cancelada.");
        }
        return l;
    }

    private void guardarHistorial(Leccion l, String accion, Instant inicioAnt, Instant finAnt,
            String enlaceAnt, Instant inicioNuevo, Instant finNuevo, String enlaceNuevo, String motivo) {
        HistorialSesion h = new HistorialSesion();
        h.setLeccion(l);
        h.setAccion(accion);
        h.setInicioAnterior(inicioAnt);
        h.setFinAnterior(finAnt);
        h.setEnlaceAnterior(enlaceAnt);
        h.setInicioNuevo(inicioNuevo);
        h.setFinNuevo(finNuevo);
        h.setEnlaceNuevo(enlaceNuevo);
        h.setMotivo(motivo);
        h.setRealizadoPorUsuarioId(actual.get().userId());
        h.setRealizadoEn(clock.instant());
        historial.save(h);
    }

    private void avisarCambio(Leccion l, String asunto, String motivo) {
        Long cursoId = l.getModulo().getCurso().getId();
        List<Matricula> alumnos = matriculas.findByEstadoAndCurso_Id("ACTIVA", cursoId);
        for (Matricula m : alumnos) {
            Notificacion n = new Notificacion();
            n.setUsuario(m.getUsuario());
            n.setTipo("CAMBIO_SESION_" + l.getId() + "_" + clock.instant());
            n.setDestinatario(m.getUsuario().getCorreo());
            n.setAsunto(asunto + ": " + l.getTitulo());
            try {
                mailService.sendHtml(HtmlMailMessage.to(n.getDestinatario(), n.getAsunto(),
                        "mail/cambio-sesion.html", Map.of(
                                "sesion", l.getTitulo(), "motivo", motivo,
                                "inicio", String.valueOf(l.getFechaHoraInicio()))));
                n.setEstadoEnvio("ENVIADO");
                n.setEnviadoEn(clock.instant());
            } catch (Exception e) {
                n.setEstadoEnvio("ERROR");
                n.setUltimoError(e.getMessage());
            }
            notificaciones.save(n);
        }
    }

    private void exigirAdministrador() {
        if (!actual.get().roles().contains("ADMINISTRADOR")) {
            throw new ForbiddenException("Esta acción requiere rol administrador.");
        }
    }
}
```

> El tipo de `Notificacion` para el aviso de cambio incluye `l.getId()` y el instante actual (no un
> string fijo como en HU-026) **a propósito**: HU-027 avisa siempre que hay un cambio real, sin
> deduplicar — a diferencia del recordatorio, acá cada reprogramación/cancelación es un evento nuevo
> que sí debe generar un correo cada vez.

### 4. Controlador: métodos nuevos sobre `ContenidoControlador`

```java
// agregar al ContenidoControlador ya existente (HU-011/HU-012)
private final AdministrarSesionesServicio administrarSesionesServicio;

@PatchMapping("/lecciones/{id}/sesion/reprogramar")
public ReprogramarSesionResultado reprogramar(@PathVariable Long id, @RequestBody ReprogramarSesionPeticion p) {
    return administrarSesionesServicio.reprogramar(id, p);
}

@PatchMapping("/cursos/{cursoId}/fecha-fin")
public void ampliarFinDeCurso(@PathVariable Long cursoId, @RequestParam LocalDate nuevaFechaFin) {
    administrarSesionesServicio.ampliarFinDeCurso(cursoId, nuevaFechaFin);
}

@PatchMapping("/lecciones/{id}/sesion/cancelar")
public void cancelar(@PathVariable Long id, @RequestBody CancelarSesionPeticion p) {
    administrarSesionesServicio.cancelar(id, p);
}

@PatchMapping("/lecciones/{id}/sesion/enlace")
public void corregirEnlace(@PathVariable Long id, @RequestBody CorregirEnlaceSesionPeticion p) {
    administrarSesionesServicio.corregirEnlace(id, p);
}

@PatchMapping("/asistencias/{id}")
public void corregirAsistencia(@PathVariable Long id, @RequestBody CorregirAsistenciaPeticion p) {
    administrarSesionesServicio.corregirAsistencia(id, p);
}

@GetMapping("/lecciones/{id}/sesion/historial")
public List<HistorialSesionRespuesta> historial(@PathVariable Long id) {
    return administrarSesionesServicio.historial(id);
}
```

Estos quedan bajo el mismo `@RequestMapping("/api/admin")` que ya tiene `ContenidoControlador` — no
hace falta repetir el prefijo en cada ruta de arriba.

### 5. Comprobación incremental

1. Probar `reprogramar` con `Clock.fixed(...)`: sesión futura ok, sesión ya realizada rechazada,
   sesión cancelada rechazada.
2. Probar el caso de fecha posterior al fin del curso: `aplicado=false, requiereAmpliarFinDeCurso=true`,
   luego `ampliarFinDeCurso` y reintentar `reprogramar` con éxito.
3. Probar `cancelar`: `HistorialSesion` guardado, `Leccion.estado=CANCELADA`.
4. Probar `corregirEnlace` rechazado en una sesión todavía futura.
5. Probar `corregirAsistencia`: cambia estado, no borra la fila, guarda `motivoCorreccion`.
6. En Swagger, confirmar que un usuario sin rol administrador recibe 403 en los seis endpoints.
7. Conectar Angular y demostrar los tres flujos pedidos: reprogramar, cancelar, corregir una ya
   realizada.
