# HU-023 — Mapa técnico para consultar materiales protegidos

> Estado de integración: el propio código ya avisa que esta protección falta. En
> `config/WebConfig.java` el comentario de la clase dice textual: *"La protección real de materiales
> (que un visitante sin matrícula no pueda abrir el enlace directo de un curso de pago) es trabajo de
> HU-022/HU-023, no de esta historia; por ahora la ruta es pública."* Esta historia es exactamente esa
> deuda: hoy cualquiera con la URL `/uploads/cursos/{cursoId}/{archivo}` descarga el material sin
> sesión ni matrícula. Esta historia cierra ese hueco.

## Resultado que debe entregar

Un alumno con acceso ve los materiales de una lección en el orden administrativo y puede descargar
solo los que lo permiten. Un enlace copiado (a un archivo subido) no funciona para un tercero sin
sesión o sin matrícula — cada solicitud se valida de nuevo, no solo la primera.

## Punto de partida

- `MaterialLeccion` y `Recurso` ya existen con todos los campos necesarios (`permiteDescarga`,
  `origen`, `referencia`, `tipoMime`, `nombreArchivo`).
- `ArchivoAlmacenamientoServicio.guardar(...)` ya sube archivos a disco y devuelve una `referencia`
  tipo `/uploads/cursos/{cursoId}/{uuid}.ext` — **esa ruta sigue sirviendo para que el admin suba
  materiales (HU-011), no se toca**. Lo que cambia es que el **alumno** nunca recibe esa ruta cruda:
  recibe la ruta de un endpoint nuevo que sí valida acceso en cada llamada.
- `MaterialLeccionRepositorio.findByLeccion_IdOrderByOrdenAsc` ya trae el recurso con `@EntityGraph`
  (recurso + tipoMaterial) — reutilizable tal cual para el listado del alumno.
- Vista previa pública (sin matrícula) de una lección marcada `esVistaPrevia` ya existe en
  `CatalogoControlador` vía `MaterialLeccionRepositorio.buscarVistaPrevia` — **no se toca, es la única
  excepción legítima a "todo exige matrícula"**.

## Dependencias

- Depende de HU-022 para el acceso integrado al aula (de dónde sale el `cursoId`/matrícula del
  alumno). Puede adelantarse asumiendo una matrícula activa de prueba mientras HU-022 no esté lista.
- No depende de HU-024 ni HU-025.

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Listar materiales de una lección | `GET /api/aula/lecciones/{leccionId}/materiales` | Materiales en orden, sin exponer rutas crudas de archivo |
| Ver en línea (streaming inline) | `GET /api/aula/materiales/{materialId}/visualizar` | Bytes del archivo si hay acceso válido |
| Descargar (solo si está permitido) | `GET /api/aula/materiales/{materialId}/descarga` | Bytes con `Content-Disposition: attachment` solo si `permiteDescarga=true` |

El listado nunca incluye la `referencia` cruda de un material `origen=SUBIDO`: en su lugar entrega la
URL de `/visualizar` (y `/descarga` cuando aplica). Para `origen=YOUTUBE` o `ENLACE` sí se entrega la
URL externa tal cual, porque esas plataformas no son del backend — pero solo después de validar
acceso en esa misma solicitud de listado.

## Trabajo del backend

### Archivos y responsabilidades

- `dto/AulaMaterialRespuesta.java` (nuevo).
- `ArchivoAlmacenamientoServicio`: agregar `Resource cargar(String referencia)` que resuelva la ruta
  física a partir de la `referencia` guardada, validando que quede dentro de `raiz` (evitar
  path traversal con `..`).
- `service/MaterialesAulaServicio.java` (nuevo): valida acceso y arma las respuestas/streams.
- `controller/AulaControlador.java`: se amplía con los tres endpoints de esta historia (ya existe
  desde HU-022, agregar métodos ahí, no crear un controlador paralelo).

### Reglas en orden

1. Resolver la matrícula del usuario autenticado a partir de `leccionId` → `modulo.curso` (nunca se
   confía en un `cursoId` separado del que realmente corresponde al material).
2. Si la lección es `esVistaPrevia = true`, permitir sin matrícula (mismo criterio que
   `CatalogoControlador`). En cualquier otro caso, exigir matrícula con acceso efectivo (misma
   regla de `AulaServicio.motivoSinAcceso`, reutilizada, no reescrita).
3. Confirmar que el material pertenece a esa lección (nunca aceptar un `materialId` de otro curso
   solo porque el usuario tiene *alguna* matrícula válida en otro lado).
4. `/visualizar`: permitido siempre que el acceso sea válido, sin mirar `permiteDescarga`.
5. `/descarga`: además de lo anterior, exige `material.isPermiteDescarga() == true`; si no,
   `ForbiddenException` con mensaje claro ("Este material no permite descarga").
6. Cada una de las tres rutas repite la validación completa — no hay caché de "ya lo validé antes" ni
   un token de un solo uso: así un enlace copiado deja de servir en cuanto cambia el acceso del
   alumno (vencimiento, cancelación).

## Trabajo del frontend

### Visor dentro de la lección (mismo componente de aula de HU-022)

- Maquetar según `maquetacion-html/HU-023-PF-AULA-materiales.html` — es el panel derecho del aula
  cuando hay una lección seleccionada, ver guía de maquetación.
- Lista de materiales en el orden que entrega el backend (no reordenar en el cliente).
- Botón "Descargar" solo se pinta si el material lo trae habilitado; no se oculta con CSS un botón que
  igual podría clickearse — directamente no se renderiza.
- Para `SUBIDO`: el `<a>`/botón apunta a la URL de `/visualizar` o `/descarga` que ya entrega el
  backend (no se arma la URL a mano en el frontend).
- Para `YOUTUBE`: embeber con el parámetro de no listado tal cual viene; para `ENLACE`: abrir en
  pestaña nueva.

### Servicio Angular

- Ampliar `features/aula/aula-api.service.ts` con `materialesDeLeccion(leccionId)`. Las URLs de
  `/visualizar` y `/descarga` se usan directo como `src`/`href`, no hace falta un método HTTP aparte
  para ellas (el navegador las pide solas al renderizar o al hacer clic).

## Pruebas mínimas

- material visible con matrícula válida;
- descarga rechazada cuando `permiteDescarga=false` aunque el acceso sea válido;
- rechazo con matrícula vencida o cancelada individualmente;
- un `materialId` de otro curso devuelve 404, no 403 (no confirmar su existencia a quien no tiene
  acceso);
- copiar la URL de `/visualizar` y abrirla sin cookie de sesión → rechazada;
- lección `esVistaPrevia` accesible sin matrícula, cualquier otra lección no.

## Terminado cuando

Ningún material `SUBIDO` es alcanzable desde una URL `/uploads/...` compartida sin pasar por
`/visualizar` o `/descarga`, y esos dos endpoints rechazan correctamente acceso vencido, cancelado o
ajeno. La demostración incluye copiar un enlace y abrirlo en una ventana sin sesión.

## Implementación guiada para copiar y adaptar

### 1. Archivos que se crean o tocan, en orden

1. `dto/AulaMaterialRespuesta.java`.
2. `ArchivoAlmacenamientoServicio.cargar(String referencia)`.
3. `service/MaterialesAulaServicio.java`.
4. Métodos nuevos en `controller/AulaControlador.java`.
5. En Angular: ampliar `aula.model.ts` y `aula-api.service.ts`; maquetar el panel de materiales.

### 2. DTO

```java
package pe.edu.utp.escuela.app.dto;

public record AulaMaterialRespuesta(
        Long id,
        String titulo,
        int orden,
        boolean permiteDescarga,
        String origen,        // "SUBIDO" | "YOUTUBE" | "ENLACE"
        String urlVisualizar, // siempre presente
        String urlDescarga,   // null cuando permiteDescarga = false
        String tipoMime,      // null para YOUTUBE/ENLACE
        String nombreArchivo  // null para YOUTUBE/ENLACE
) {}
```

### 3. `ArchivoAlmacenamientoServicio`: leer de vuelta lo que ya se guardó

```java
// agregar a ArchivoAlmacenamientoServicio.java
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public Resource cargar(String referencia) {
    // referencia llega como "/uploads/cursos/12/uuid.pdf"; se descarta el prefijo "/uploads/"
    String relativo = referencia.startsWith("/uploads/") ? referencia.substring("/uploads/".length()) : referencia;
    Path destino = raiz.resolve(relativo).normalize();
    if (!destino.startsWith(raiz)) {
        throw new BusinessValidationException("Referencia de archivo inválida.");
    }
    if (!Files.exists(destino)) {
        throw new ResourceNotFoundException("El archivo ya no está disponible.");
    }
    return new FileSystemResource(destino);
}
```

`raiz.resolve(...).normalize()` seguido de `startsWith(raiz)` es lo que bloquea un intento de
`../../../application.properties` disfrazado de referencia.

### 4. `MaterialesAulaServicio`

```java
package pe.edu.utp.escuela.app.service;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import pe.edu.utp.escuela.app.dto.AulaMaterialRespuesta;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.MaterialLeccion;
import pe.edu.utp.escuela.app.entity.Matricula;
import pe.edu.utp.escuela.app.exception.ForbiddenException;
import pe.edu.utp.escuela.app.exception.ResourceNotFoundException;
import pe.edu.utp.escuela.app.repository.LeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MaterialLeccionRepositorio;
import pe.edu.utp.escuela.app.repository.MatriculaRepositorio;
import pe.edu.utp.escuela.app.security.CurrentUserService;

@Service
@RequiredArgsConstructor
public class MaterialesAulaServicio {
    private final LeccionRepositorio lecciones;
    private final MaterialLeccionRepositorio materiales;
    private final MatriculaRepositorio matriculas;
    private final ArchivoAlmacenamientoServicio almacenamiento;
    private final AulaServicio aulaServicio; // reutiliza motivoSinAcceso vía un metodo publico
    private final CurrentUserService actual;

    @Transactional(readOnly = true)
    public List<AulaMaterialRespuesta> listar(Long leccionId) {
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("La lección no existe."));
        exigirAccesoALeccion(leccion);

        return materiales.findByLeccion_IdOrderByOrdenAsc(leccionId).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource visualizar(Long materialId) {
        MaterialLeccion m = materialVerificado(materialId);
        return almacenamiento.cargar(m.getRecurso().getReferencia());
    }

    @Transactional(readOnly = true)
    public Resource descargar(Long materialId) {
        MaterialLeccion m = materialVerificado(materialId);
        if (!m.isPermiteDescarga()) {
            throw new ForbiddenException("Este material no permite descarga.");
        }
        return almacenamiento.cargar(m.getRecurso().getReferencia());
    }

    private MaterialLeccion materialVerificado(Long materialId) {
        MaterialLeccion m = materiales.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("El material no existe."));
        exigirAccesoALeccion(m.getLeccion());
        if (!"SUBIDO".equals(m.getRecurso().getOrigen())) {
            throw new ResourceNotFoundException("Este material no se sirve desde el backend.");
        }
        return m;
    }

    private void exigirAccesoALeccion(Leccion leccion) {
        if (leccion.isEsVistaPrevia()) {
            return;
        }
        Long cursoId = leccion.getModulo().getCurso().getId();
        Matricula m = matriculas.findByUsuario_IdAndCurso_Id(actual.get().userId(), cursoId)
                .orElseThrow(() -> new ForbiddenException("No tienes acceso a este contenido."));
        String motivo = aulaServicio.motivoSinAccesoPublico(m);
        if (motivo != null) {
            throw new ForbiddenException(motivo);
        }
    }

    private AulaMaterialRespuesta aRespuesta(MaterialLeccion m) {
        var r = m.getRecurso();
        boolean subido = "SUBIDO".equals(r.getOrigen());
        return new AulaMaterialRespuesta(
                m.getId(), m.getTitulo(), m.getOrden(), m.isPermiteDescarga(), r.getOrigen(),
                subido ? "/api/aula/materiales/" + m.getId() + "/visualizar" : r.getReferencia(),
                subido && m.isPermiteDescarga() ? "/api/aula/materiales/" + m.getId() + "/descarga" : null,
                subido ? r.getTipoMime() : null,
                subido ? r.getNombreArchivo() : null
        );
    }
}
```

`motivoSinAcceso` en `AulaServicio` (de HU-022) se vuelve `motivoSinAccesoPublico` (cambia de `private`
a paquete/`public`) para que `MaterialesAulaServicio` lo reutilice sin copiar la regla.

### 5. Controlador: métodos nuevos sobre `AulaControlador`

```java
// agregar al AulaControlador ya creado en HU-022
private final MaterialesAulaServicio materialesAulaServicio;

@GetMapping("/lecciones/{leccionId}/materiales")
public List<AulaMaterialRespuesta> materiales(@PathVariable Long leccionId) {
    return materialesAulaServicio.listar(leccionId);
}

@GetMapping("/materiales/{materialId}/visualizar")
public ResponseEntity<Resource> visualizar(@PathVariable Long materialId) {
    Resource recurso = materialesAulaServicio.visualizar(materialId);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(recurso);
}

@GetMapping("/materiales/{materialId}/descarga")
public ResponseEntity<Resource> descarga(@PathVariable Long materialId) {
    Resource recurso = materialesAulaServicio.descargar(materialId);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
            .body(recurso);
}
```

### 6. Angular: ampliar el servicio existente

```ts
// agregar a aula-api.service.ts
materialesDeLeccion(leccionId: number) {
  return this.http.get<AulaMaterial[]>(`${API_URL}/aula/lecciones/${leccionId}/materiales`);
}
```

```ts
// agregar a aula.model.ts
export interface AulaMaterial {
  id: number;
  titulo: string;
  orden: number;
  permiteDescarga: boolean;
  origen: 'SUBIDO' | 'YOUTUBE' | 'ENLACE';
  urlVisualizar: string;
  urlDescarga: string | null;
  tipoMime: string | null;
  nombreArchivo: string | null;
}
```

Las URLs que entrega `urlVisualizar`/`urlDescarga` ya son absolutas al backend (`/api/aula/...`); el
componente las usa directo en `src`/`href` — como el navegador manda la cookie `ESEJUR_SESION`
automáticamente en la misma pestaña, no hace falta token extra en la URL.

### 7. Comprobación incremental

1. Probar `ArchivoAlmacenamientoServicio.cargar` con una referencia válida y con un intento de
   `../` (debe lanzar `BusinessValidationException`, nunca leer fuera de `raiz`).
2. Probar `MaterialesAulaServicio` con matrícula válida, vencida y de otro curso.
3. En Swagger: pedir `/visualizar` con una cookie válida y sin cookie — confirmar 200 vs 401/403.
4. Copiar la URL de `/visualizar` que devuelve el navegador y abrirla en una ventana nueva sin sesión
   — debe rechazarse.
5. Conectar Angular y confirmar que el botón "Descargar" no aparece cuando `permiteDescarga=false`.
