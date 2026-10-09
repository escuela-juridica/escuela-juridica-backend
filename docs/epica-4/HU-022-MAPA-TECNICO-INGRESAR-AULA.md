# HU-022 — Mapa técnico para ingresar y continuar el curso

> Estado de integración: no existe ningún controlador ni servicio de "aula" todavía — esta historia
> crea esa pieza desde cero. `MatriculaServicio` ya calcula una versión simple de progreso dentro de
> `respuesta()` (porcentaje, lecciones completadas, siguiente lección) para la lista "Mis cursos"; esa
> lógica se **extrae a un componente compartido** para no duplicarla entre el panel y el aula.
>
> El estado "curso completo CANCELADO" que describe la historia **no existe todavía en el backend**
> (`CicloVidaCursoServicio` lo deja explícitamente para una historia futura de EP06). Este mapa deja el
> enganche listo (un método que siempre devuelve `false` hoy) para no bloquear el resto de la historia.

## Resultado que debe entregar

Un alumno con matrícula activa abre su curso y llega directo al temario, posicionado en la última
lección que usó o en la siguiente pendiente. Ve el estado de cada módulo/lección (completada,
disponible, bloqueada) y el porcentaje general. La tarjeta de sesión en vivo vive en esta misma
pantalla (se detalla en HU-026, pero el espacio y el estado "antes de la ventana" se arman aquí).

## Punto de partida

- Backend: `ProgresoLeccion` (entidad + repositorio) ya existe, pero el repositorio solo tiene
  `findByMatricula_Id`. `Matricula`, `Modulo`, `Leccion`, `ReglaCurso` ya existen con todos los campos
  que hacen falta.
- La lógica de cálculo de progreso ya existe, pero **privada** dentro de
  `MatriculaServicio.respuesta(...)` — hay que extraerla, no reinventarla.
- No existe ningún paquete `features/aula` en Angular. Se crea desde cero.
- Tablas: `matricula`, `modulo`, `leccion`, `progreso_leccion`, `regla_curso`.

## Dependencias

- Ninguna dentro de EP04; requiere curso publicado de EP02 y matrícula con acceso de EP03 (ya
  existen).
- HU-023, HU-024 y HU-025 se construyen **sobre esta pantalla** (reutilizan el mismo temario lateral).
- La tarjeta de sesión en vivo que aparece aquí comparte diseño con la de HU-026 — coordinar con ese
  equipo antes de nombrar las clases CSS (ver la guía de maquetación).

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Resumen del aula | `GET /api/aula/{cursoId}/resumen` | Progreso general y siguiente actividad |
| Ruta del curso | `GET /api/aula/{cursoId}/ruta` | Módulos y lecciones con su estado individual |

Ambos endpoints resuelven la matrícula a partir del usuario autenticado + el `cursoId` de la URL;
nunca reciben `matriculaId` directamente (un alumno no debe poder adivinar el id de la matrícula de
otra persona).

## Trabajo del backend

### Archivos y responsabilidades

- `ProgresoCursoCalculador` (nuevo, `@Component`): recibe una `Matricula` y calcula porcentaje,
  lecciones completadas/totales y la siguiente lección pendiente. `MatriculaServicio` y el nuevo
  `AulaServicio` lo inyectan los dos — se elimina el cálculo duplicado de `MatriculaServicio`.
- `dto/AulaResumenRespuesta.java`, `dto/AulaModuloRespuesta.java`, `dto/AulaLeccionRespuesta.java`.
- `ProgresoLeccionRepositorio`: agregar `findByMatricula_IdAndLeccion_Id`.
- `service/AulaServicio.java` (nuevo).
- `controller/AulaControlador.java` (nuevo).

### Reglas en orden

1. Resolver la matrícula del usuario actual para ese `cursoId` (`matriculaRepositorio
   .findByUsuario_IdAndCurso_Id`). Si no existe, `ResourceNotFoundException`.
2. Calcular `accesoEfectivo` con las mismas condiciones que ya usa `MatriculaServicio` (estado ACTIVA,
   vigencia, inicio alcanzado, cuenta habilitada, correo verificado, sin cambio de contraseña
   pendiente). Si no es efectivo, `BusinessValidationException` con el motivo real (vencida,
   pendiente, cancelada, inicio futuro) — nunca un mensaje genérico.
3. Si la matrícula es válida, cargar módulos activos (`moduloRepositorio
   .findByCursoIdAndActivoTrueOrderByOrdenAsc`) y sus lecciones activas obligatorias y no
   obligatorias (`leccionRepositorio.buscarActivasDeModulos`).
4. Cruzar con `progresoLeccionRepositorio.findByMatricula_Id` para marcar cada lección:
   - `COMPLETADA` si existe un `ProgresoLeccion.completada = true`.
   - `BLOQUEADA` si el curso tiene secuencia obligatoria (`ReglaCurso.secuenciaObligatoria`) y la
     lección anterior obligatoria del mismo orden no está completada. **Nota:** el cálculo fino de
     bloqueo por requisitos cruzados (exámenes, otros módulos) es el alcance de HU-025; aquí solo se
     bloquea por orden secuencial simple dentro del mismo módulo.
   - `DISPONIBLE` en cualquier otro caso.
5. La "siguiente actividad" es la primera lección obligatoria, en orden, que no esté completada. Si
   todas están completas, apunta a la última lección (modo repaso) y el frontend ofrece ir al
   certificado si corresponde.
6. Modo solo-lectura por cancelación de curso: exponer `cursoServicio.estaCanceladoParaLectura(Curso)`
   que hoy **siempre devuelve `false`** (comentario explícito `// TODO: EP06/HU-038 define el estado
   CANCELADO del curso`), para no bloquear la demostración de esta historia mientras esa pieza no
   existe.

### Consulta que debe resolver `ProgresoCursoCalculador`

Por matrícula, en una sola pasada: total de lecciones obligatorias activas del curso, cuántas de esas
tiene completadas el alumno, y el título de la primera obligatoria incompleta en orden de
módulo→lección. Si el curso no tiene lecciones obligatorias activas, el porcentaje es `0` sin dividir
entre cero.

## Trabajo del frontend

### Pantalla de aula (`features/aula/aula`)

- Maquetar según `maquetacion-html/HU-022-PF-AULA-ingresar.html` (ver guía de maquetación más abajo).
- Temario lateral con tres estilos visuales por estado (completada con check, disponible, bloqueada
  con candado y el motivo en `title`/tooltip).
- Al cargar, hacer scroll/seleccionar automáticamente la `siguienteLeccionId` que entrega el resumen.
- Botón "Continuar" en el panel ya existente (`cuenta/panel`) apunta a `/aula/:cursoId`, no a
  `/aula/:matriculaId` — el id de matrícula nunca viaja en la URL.
- Pantallas de rechazo (matrícula vencida/pendiente/cancelada/inicio futuro) reutilizan el componente
  `.estado-vacio` ya existente en el sistema de diseño.

### Servicio Angular

- Nuevo `features/aula/aula-api.service.ts` con `obtenerResumen(cursoId)` y `obtenerRuta(cursoId)`,
  mismo patrón que `MatriculaApiService` (inyecta `HttpClient`, usa `API_URL`).
- Modelos TypeScript en `features/aula/aula.model.ts` que reflejen exactamente los DTOs de abajo —
  copiar los nombres de campo tal cual, no traducirlos.

### Destinos

- Matrícula inválida: no se abre el aula, se explica el motivo con un botón de vuelta a "Mis cursos".
- Lección bloqueada seleccionada manualmente: se muestra el contenido de todas formas bloqueado con el
  requisito pendiente, nunca un error genérico.

## Pruebas mínimas

- acceso efectivo con progreso parcial;
- acceso efectivo con progreso en cero (sin dividir entre cero);
- matrícula vencida, pendiente y cancelada individualmente (tres motivos distintos);
- inicio futuro;
- lección bloqueada por secuencia y su motivo visible;
- reapertura de una lección completada (no pierde el check);
- curso sin lecciones obligatorias activas.

## Terminado cuando

Un alumno con acceso ve su temario completo con los tres estados correctos y llega directo a su
siguiente lección pendiente. Un alumno sin acceso ve el motivo real, nunca contenido. El cálculo de
progreso vive en un solo lugar (`ProgresoCursoCalculador`) y `MatriculaServicio` ya lo reutiliza en vez
de tener su propia copia.

## Implementación guiada para copiar y adaptar

### 1. Archivos que se crean en orden

1. `dto/AulaLeccionRespuesta.java`, `dto/AulaModuloRespuesta.java`, `dto/AulaResumenRespuesta.java`.
2. `service/ProgresoCursoCalculador.java` (extraído de `MatriculaServicio`).
3. Ajuste de `ProgresoLeccionRepositorio` (un método nuevo).
4. `service/AulaServicio.java`.
5. `controller/AulaControlador.java`.
6. Refactor puntual de `MatriculaServicio` para inyectar y usar `ProgresoCursoCalculador`.
7. En Angular: `features/aula/aula.model.ts`, `features/aula/aula-api.service.ts`,
   `features/aula/aula/aula.ts` + `.html` + `.scss`.

### 2. DTOs

```java
package pe.edu.utp.escuela.app.dto;

public record AulaLeccionRespuesta(
        Long id,
        String titulo,
        int orden,
        String tipo,            // "GRABADA" | "EN_VIVO"
        boolean esObligatoria,
        String estadoAula,      // "COMPLETADA" | "DISPONIBLE" | "BLOQUEADA"
        String motivoBloqueo,   // null cuando no está bloqueada
        Instant fechaHoraInicio,
        Instant fechaHoraFin
) {}
```

```java
public record AulaModuloRespuesta(Long id, String titulo, int orden, List<AulaLeccionRespuesta> lecciones) {}
```

```java
public record AulaResumenRespuesta(
        Long cursoId,
        String cursoTitulo,
        String modalidad,
        boolean accesoEfectivo,
        String mensajeAcceso,       // null cuando accesoEfectivo = true
        boolean soloLectura,
        int porcentajeProgreso,
        int leccionesCompletadas,
        int totalLecciones,
        Long siguienteLeccionId,
        String siguienteLeccionTitulo
) {}
```

### 3. `ProgresoCursoCalculador` (lógica extraída, no duplicada)

```java
package pe.edu.utp.escuela.app.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.ProgresoLeccion;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.ModuloRepositorio;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepositorio;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProgresoCursoCalculador {
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final ProgresoLeccionRepositorio progresoLecciones;

    public record Resultado(int porcentaje, int completadas, int total, Leccion siguiente) {}

    public Resultado calcular(Matricula matricula) {
        List<Modulo> modulosActivos =
                modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(matricula.getCurso().getId());
        List<Long> idsModulos = modulosActivos.stream().map(Modulo::getId).toList();
        List<Leccion> obligatorias = lecciones.buscarActivasDeModulos(idsModulos).stream()
                .filter(Leccion::isEsObligatoria)
                .toList();

        if (obligatorias.isEmpty()) {
            return new Resultado(0, 0, 0, null);
        }

        Map<Long, ProgresoLeccion> progresoPorLeccion = progresoLecciones
                .findByMatricula_Id(matricula.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLeccion().getId(), p -> p));

        long completadas = obligatorias.stream()
                .filter(l -> {
                    ProgresoLeccion p = progresoPorLeccion.get(l.getId());
                    return p != null && p.isCompletada();
                })
                .count();

        Leccion siguiente = obligatorias.stream()
                .filter(l -> {
                    ProgresoLeccion p = progresoPorLeccion.get(l.getId());
                    return p == null || !p.isCompletada();
                })
                .findFirst()
                .orElse(obligatorias.getLast());

        int porcentaje = (int) Math.round(completadas * 100.0 / obligatorias.size());
        return new Resultado(porcentaje, (int) completadas, obligatorias.size(), siguiente);
    }
}
```

`MatriculaServicio.respuesta(...)` pasa a inyectar `ProgresoCursoCalculador` y usar `calcular(m)` en
vez de su bloque de cálculo propio — eliminar ese código duplicado al integrar esta historia.

### 4. Repositorio: un método nuevo

```java
public interface ProgresoLeccionRepositorio extends JpaRepository<ProgresoLeccion, Long> {
    @EntityGraph(attributePaths = {"leccion", "leccion.modulo"})
    List<ProgresoLeccion> findByMatricula_Id(Long matriculaId);

    Optional<ProgresoLeccion> findByMatricula_IdAndLeccion_Id(Long matriculaId, Long leccionId);
}
```

### 5. `AulaServicio`

```java
package pe.edu.utp.escuela.app.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import pe.edu.utp.escuela.app.dto.*;
import pe.edu.utp.escuela.app.entity.*;
import pe.edu.utp.escuela.app.exception.BusinessValidationException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.*;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class AulaServicio {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MatriculaRepositorio matriculas;
    private final ModuloRepositorio modulos;
    private final LeccionRepositorio lecciones;
    private final ProgresoLeccionRepositorio progresoLecciones;
    private final ProgresoCursoCalculador calculador;
    private final CurrentUserService actual;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AulaResumenRespuesta obtenerResumen(Long cursoId) {
        Matricula m = matricula(cursoId);
        String motivo = motivoSinAcceso(m);
        boolean efectivo = motivo == null;

        if (!efectivo) {
            return new AulaResumenRespuesta(cursoId, m.getCurso().getTitulo(),
                    m.getCurso().getModalidad(), false, motivo, soloLectura(m),
                    0, 0, 0, null, null);
        }

        var r = calculador.calcular(m);
        return new AulaResumenRespuesta(cursoId, m.getCurso().getTitulo(), m.getCurso().getModalidad(),
                true, null, false, r.porcentaje(), r.completadas(), r.total(),
                r.siguiente() == null ? null : r.siguiente().getId(),
                r.siguiente() == null ? null : r.siguiente().getTitulo());
    }

    @Transactional(readOnly = true)
    public List<AulaModuloRespuesta> obtenerRuta(Long cursoId) {
        Matricula m = matricula(cursoId);
        if (motivoSinAcceso(m) != null) {
            throw new BusinessValidationException(motivoSinAcceso(m));
        }

        List<Modulo> modulosActivos =
                modulos.findByCursoIdAndActivoTrueOrderByOrdenAsc(cursoId);
        List<Long> idsModulos = modulosActivos.stream().map(Modulo::getId).toList();
        List<Leccion> todas = lecciones.buscarActivasDeModulos(idsModulos);
        Map<Long, ProgresoLeccion> progreso = progresoLecciones.findByMatricula_Id(m.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getLeccion().getId(), p -> p));

        boolean secuenciaObligatoria = m.getCurso().getReglaCurso() != null
                && m.getCurso().getReglaCurso().isSecuenciaObligatoria();

        return modulosActivos.stream().map(mod -> {
            List<Leccion> deEsteModulo = todas.stream()
                    .filter(l -> l.getModulo().getId().equals(mod.getId()))
                    .sorted(java.util.Comparator.comparingInt(Leccion::getOrden))
                    .toList();
            boolean anteriorCompletada = true;
            List<AulaLeccionRespuesta> respuestas = new java.util.ArrayList<>();
            for (Leccion l : deEsteModulo) {
                ProgresoLeccion p = progreso.get(l.getId());
                boolean completada = p != null && p.isCompletada();
                String estado;
                String motivoBloqueo = null;
                if (completada) {
                    estado = "COMPLETADA";
                } else if (secuenciaObligatoria && l.isEsObligatoria() && !anteriorCompletada) {
                    estado = "BLOQUEADA";
                    motivoBloqueo = "Completa la lección anterior para desbloquear esta.";
                } else {
                    estado = "DISPONIBLE";
                }
                respuestas.add(new AulaLeccionRespuesta(l.getId(), l.getTitulo(), l.getOrden(),
                        l.getTipo(), l.isEsObligatoria(), estado, motivoBloqueo,
                        l.getFechaHoraInicio(), l.getFechaHoraFin()));
                if (l.isEsObligatoria()) {
                    anteriorCompletada = completada;
                }
            }
            return new AulaModuloRespuesta(mod.getId(), mod.getTitulo(), mod.getOrden(), respuestas);
        }).toList();
    }

    private Matricula matricula(Long cursoId) {
        return matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una matrícula en este curso."));
    }

    private String motivoSinAcceso(Matricula m) {
        if ("CANCELADA".equals(m.getEstado())) return "Tu matrícula fue cancelada.";
        if ("VENCIDA".equals(m.getEstado())) return "Tu acceso a este curso venció.";
        if (!"ACTIVA".equals(m.getEstado())) return "Tu matrícula no está activa.";
        var inicio = m.getCurso().getFechaInicio();
        if (inicio != null && inicio.isAfter(java.time.LocalDate.now(clock.withZone(LIMA)))) {
            return "El curso todavía no inicia.";
        }
        return null;
    }

    // TODO: EP06/HU-038 define el estado CANCELADO del curso completo; hasta entonces
    // ningún curso llega aquí marcado como cancelado y el modo solo-lectura nunca se activa.
    private boolean soloLectura(Matricula m) {
        return false;
    }
}
```

### 6. `AulaControlador`

```java
package pe.edu.utp.escuela.app.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.escuela.app.dto.AulaModuloRespuesta;
import pe.edu.utp.escuela.app.dto.AulaResumenRespuesta;
import pe.edu.utp.escuela.app.service.AulaServicio;

@RestController
@RequestMapping("/api/aula")
@RequiredArgsConstructor
public class AulaControlador {
    private final AulaServicio aulaServicio;

    @GetMapping("/{cursoId}/resumen")
    public AulaResumenRespuesta resumen(@PathVariable Long cursoId) {
        return aulaServicio.obtenerResumen(cursoId);
    }

    @GetMapping("/{cursoId}/ruta")
    public List<AulaModuloRespuesta> ruta(@PathVariable Long cursoId) {
        return aulaServicio.obtenerRuta(cursoId);
    }
}
```

No hace falta agregar `/api/aula/**` a `RUTAS_PUBLICAS` de `SecurityConfig` — como todo lo demás que
exige sesión, basta con que el endpoint quede fuera de esa lista (ya protegido por `authenticated()`).

### 7. Angular: modelo y servicio

```ts
// features/aula/aula.model.ts
export interface AulaLeccion {
  id: number;
  titulo: string;
  orden: number;
  tipo: 'GRABADA' | 'EN_VIVO';
  esObligatoria: boolean;
  estadoAula: 'COMPLETADA' | 'DISPONIBLE' | 'BLOQUEADA';
  motivoBloqueo: string | null;
  fechaHoraInicio: string | null;
  fechaHoraFin: string | null;
}

export interface AulaModulo {
  id: number;
  titulo: string;
  orden: number;
  lecciones: AulaLeccion[];
}

export interface AulaResumen {
  cursoId: number;
  cursoTitulo: string;
  modalidad: string;
  accesoEfectivo: boolean;
  mensajeAcceso: string | null;
  soloLectura: boolean;
  porcentajeProgreso: number;
  leccionesCompletadas: number;
  totalLecciones: number;
  siguienteLeccionId: number | null;
  siguienteLeccionTitulo: string | null;
}
```

```ts
// features/aula/aula-api.service.ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { API_URL } from '../../core/api/api.config';
import { AulaModulo, AulaResumen } from './aula.model';

@Injectable({ providedIn: 'root' })
export class AulaApiService {
  private readonly http = inject(HttpClient);

  obtenerResumen(cursoId: number) {
    return this.http.get<AulaResumen>(`${API_URL}/aula/${cursoId}/resumen`);
  }

  obtenerRuta(cursoId: number) {
    return this.http.get<AulaModulo[]>(`${API_URL}/aula/${cursoId}/ruta`);
  }
}
```

En `aula.ts` (componente standalone, `signal` para `resumen`/`ruta`/`leccionSeleccionadaId`), al
cargar: pedir `resumen`, si `accesoEfectivo` pedir `ruta`, seleccionar `siguienteLeccionId` por
defecto. Si `accesoEfectivo` es `false`, renderizar `.estado-vacio` con `mensajeAcceso` y un botón de
vuelta al panel — no pedir `ruta` en ese caso.

### 8. Comprobación incremental

1. Probar `ProgresoCursoCalculador` con cero, algunas y todas las lecciones completadas.
2. Probar `AulaServicio.obtenerResumen` para los 4 motivos de rechazo distintos.
3. Confirmar en Swagger que `/api/aula/{cursoId}/resumen` y `/ruta` responden con una matrícula propia
   y fallan con `404` para un curso donde el usuario no está matriculado.
4. Conectar Angular y comprobar que "Continuar" del panel abre la lección correcta.
5. Verificar que `MatriculaServicio` sigue devolviendo los mismos números que antes del refactor
   (ejecutar la suite existente de `MatriculaServicioTests`).
