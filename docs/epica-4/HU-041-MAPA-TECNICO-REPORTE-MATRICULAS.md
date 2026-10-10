# HU-041 — Mapa técnico para consultar el reporte de matrículas

> **Equipo: Ariana y Gabriel** · Rama: `feature/HU-041-reporte-matriculas` (desde `develop`).

> Estado de integración: esta historia **ya estaba implementada y funcionando al 100%** (reporte
> filtrable con exportación a PDF/Excel/CSV, con screenshots reales confirmados). Se retiró
> deliberadamente para convertirla en ejercicio de programación del equipo — esta guía documenta
> exactamente cómo estaba construida, con el código real, para que se reconstruya igual o mejor.
> **Importante:** `MatriculaServicio` y `MatriculaControlador` son archivos **compartidos** con
> HU-017 (matrícula gratuita), HU-019 (matricular administrativamente), HU-020 (controlar
> matrículas y pagos) y HU-021 (mis cursos) — que siguen funcionando exactamente igual, no se tocó
> nada de esos. Lo único que se retiró son los dos métodos del servicio exclusivos de esta
> historia (`reportePaginado`, `reporte`) y sus helpers privados exclusivos, los tres endpoints de
> reporte del controlador, y el campo `exportador`.
>
> La clase `export/ReporteMatriculaExportador.java` es 100% exclusiva de HU-041 (no la usa nada
> más) y se retiró completa — su código íntegro está más abajo. El DTO `ReporteMatriculaRespuesta`
> y las dos consultas del repositorio que lo producen (`MatriculaRepositorio.buscarReporte`,
> `.exportarReporte`) **no se tocaron**: siguen existiendo tal cual, listas para reutilizarse.
>
> En el frontend, el componente `reporte-matriculas` es 100% exclusivo de esta historia y se vació
> a un placeholder. En el archivo compartido `matricula-api.service.ts` solo se retiraron los 4
> métodos de reporte/exportación y sus dos helpers privados — `matricularGratis`, `misCursos`,
> `crearAdministrativa`, `advertenciaAcademica` y `reenviarConfirmacion` (de HU-017/019/020/021)
> siguen funcionando exactamente igual. Las interfaces `ReporteMatricula` y
> `FiltrosReporteMatricula`, definidas en ese mismo archivo, tampoco se tocaron.

## Resultado que debe entregar

Un administrador filtra el historial completo de matrículas (texto, estado, curso, modalidad y
rango de fechas) en una tabla paginada server-side, con acceso y situación académica mostrados por
separado, y puede descargar exactamente lo mismo que está viendo en PDF, Excel o CSV.

## Punto de partida — ya existe, se reutiliza tal cual

- Entidades `Matricula`, `Pago`, `Curso` — no se tocan.
- DTO `dto/ReporteMatriculaRespuesta.java` — ya existe, no hace falta recrearlo:

  ```java
  package pe.edu.utp.escuela.app.dto;

  import java.time.Instant;

  /** Una fila por matricula; separa acceso, origen y situacion academica para HU-041. */
  public record ReporteMatriculaRespuesta(
          Long matriculaId, String alumno, String correo, String curso, String modalidad,
          Instant fechaMatricula, Instant fechaActivacion, String estadoMatricula, String formaIngreso,
          String situacionAcademica, String estadoCertificado) {}
  ```

- `MatriculaRepositorio.buscarReporte(texto, estado, cursoId, modalidad, desde, hasta, pageable)` y
  `.exportarReporte(texto, estado, cursoId, modalidad, desde, hasta)` — ya existen, con la misma
  consulta JPQL (una paginada, otra en lista completa para exportar), incluyendo el `left join` al
  certificado para resolver `estadoCertificado`. No se recrean, solo se llaman desde el servicio.
- `PagoRepositorio.findByMatricula_IdIn(ids)` — ya existe, se usa para recalcular el origen real
  (ver regla no obvia más abajo).
- En `MatriculaServicio` (que se queda, no se borra) ya están disponibles como helpers privados:
  `procesarVencimientos()`, `exigirAdministrador()`. Reutilízalos, no los reescribas.
- En Angular, `MatriculaApiService` (`features/matriculas/matricula-api.service.ts`) ya existe y ya
  tiene `matricularGratis`, `misCursos`, `crearAdministrativa`, `advertenciaAcademica` y
  `reenviarConfirmacion` — solo le faltan los métodos de esta historia.
- Las interfaces `ReporteMatricula` y `FiltrosReporteMatricula`, en ese mismo archivo, ya existen
  tal cual (no hace falta recrearlas ni moverlas a `matricula.model.ts`):

  ```ts
  export interface ReporteMatricula { matriculaId: number; alumno: string; correo: string; curso: string; modalidad: string; fechaMatricula: string; fechaActivacion: string | null; estadoMatricula: string; formaIngreso: string; situacionAcademica: string; estadoCertificado: string; }
  export interface FiltrosReporteMatricula { texto?: string; estado?: string; cursoId?: number | null; modalidad?: string; fechaDesde?: string; fechaHasta?: string; }
  ```

- `CursoAdminApiService.listar(texto, page, size)` — ya existe, se reutiliza para poblar el combo
  de cursos del filtro (se le pide una página grande, `listar('', 0, 100)`, no un endpoint nuevo).
- El componente `features/admin/reportes/reporte-matriculas/*` ya existe (hoy con el placeholder
  `.pantalla-pendiente`) — reemplaza su contenido, no lo muevas de carpeta.

## Dependencias

- Ninguna nueva: requiere que existan matrículas y pagos (HU-017/019/020, ya existen y siguen
  funcionando).

## 🔑 Punto de oro — con quién coordinar

Puedes arrancar ya, sin esperar a nadie. Solo dos avisos puntuales, no bloqueos, con **Joel y Juan**
(HU-020, controlar matrículas y pagos — comparten `MatriculaServicio`, `MatriculaControlador` y
`matricula-api.service.ts` con ustedes):

1. **`normalizar(String)` / `normalizarEstado(String)`** — ninguno de los dos existe hoy en
   `MatriculaServicio`; los necesitas tú (`reportePaginado`/`reporte`) y también HU-020
   (`listarAdministrativas`). **El que los cree primero avisa en el grupo** — el segundo los
   reutiliza, no los vuelve a crear (si los duplica, no compila).
2. **Bloques separados en los archivos compartidos** — agrega tus métodos/endpoints en un bloque
   propio y comentado (`// HU-041 — ...`), no intercalado con los de HU-020, para que el merge de Git
   sea automático.

No hay nada que coordinar con HU-008 (usuarios) ni con HU-027 (sesiones) — son archivos completamente
aparte.

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Consultar el reporte (paginado) | `GET /api/admin/reportes/matriculas?texto=&estado=&cursoId=&modalidad=&fechaDesde=&fechaHasta=&page=&size=` | Página de filas de reporte, filtrada |
| Exportar a CSV | `GET /api/admin/reportes/matriculas/exportar` (mismos filtros, sin paginar) | Archivo `.csv` descargable |
| Exportar a Excel | `GET /api/admin/reportes/matriculas/exportar-excel` (mismos filtros) | Archivo `.xlsx` descargable, con formato |
| Exportar a PDF | `GET /api/admin/reportes/matriculas/exportar-pdf` (mismos filtros) | Archivo `.pdf` descargable, con formato |

Los tres endpoints de exportación nunca paginan: siempre traen **todas** las filas que coinciden
con el filtro (Escenario 2 — "la exportación debe ser consistente con lo que se está viendo
filtrado").

## Trabajo del backend

### Archivos y responsabilidades

- `service/MatriculaServicio.java` (ya existe, se amplía): agregar `reportePaginado` y `reporte`,
  más los helpers privados exclusivos `origenesReporte`, `origenReporte`, `conOrigen`,
  `validarRango`, `inicio`, `finExclusivo`, y los helpers de normalización `normalizar(String)` /
  `normalizarEstado(String)` (si ya existieran por otra historia, reutilízalos; hoy no existen).
- `controller/MatriculaControlador.java` (ya existe, se amplía): agregar los tres endpoints y el
  campo `exportador` (inyectado por constructor, vía `@RequiredArgsConstructor`).
- `export/ReporteMatriculaExportador.java` (se recrea completo, no existe): genera los tres
  formatos a partir de la misma lista de filas que ya filtró el servicio.

### Reglas en orden

1. `reportePaginado` y `reporte` llaman primero a `procesarVencimientos()` (idempotente), exigen
   rol ADMINISTRADOR, validan el rango de fechas (`validarRango`: si `hasta` es anterior a
   `desde`, `BusinessValidationException`) y delegan la consulta en el repositorio — uno paginado
   (`buscarReporte`), el otro en lista completa para exportar (`exportarReporte`).
2. **Regla no obvia — el "origen" del reporte no es `formaIngreso`:** la columna "Origen" que ve
   el administrador no es directamente `matricula.formaIngreso`. Se recalcula a partir de **los
   pagos reales** de cada matrícula (`origenesReporte`/`origenReporte`), porque el reporte exige
   distinguir el origen económico real, no solo cómo entró el alumno. La prioridad, si una
   matrícula tiene varios pagos, es:
   1. `EXONERADO` (si algún pago de esa matrícula tiene `origen = EXONERADO`) — gana siempre.
   2. `REGISTRADO_MANUAL` (si algún pago tiene `origen = MANUAL`) — gana sobre pago en línea.
   3. `PAGO_EN_LINEA` (si algún pago tiene `estado = APROBADO`).
   4. Si nada de lo anterior aplica, se usa el origen del primer pago tal cual.
   Si la matrícula no tiene ningún pago (p. ej. no debería pasar, pero por seguridad), se cae de
   vuelta a `formaIngreso` de la matrícula (`origenes.getOrDefault(f.matriculaId(), f.formaIngreso())`).
   Esto se resuelve en **una sola consulta agrupada** (`pagos.findByMatricula_IdIn(ids)`), nunca
   una consulta por fila — evita N+1 sobre una tabla potencialmente grande.
3. `inicio(fecha)` / `finExclusivo(fecha)`: convierten el filtro de fecha (opcional) a un
   `Instant` **siempre tipado**, nunca `null` — PostgreSQL no puede inferir el tipo de un parámetro
   nulo usado en una comparación opcional (`m.fechaMatricula >= :desde`). Sin fecha, se usa un
   rango que cubre "siempre" (`0001-01-01` a `9999-12-31` exclusivo). `finExclusivo` suma un día y
   usa `<` para incluir el día completo de `hasta` sin depender de la hora.
4. Los endpoints de exportación (`exportar`, `exportar-excel`, `exportar-pdf`) llaman a
   `servicio.reporte(...)` (la versión sin paginar) y pasan el resultado al exportador
   correspondiente — nunca llaman a `reportePaginado` ni truncan la lista.

## Trabajo del frontend

### Pantalla (`features/admin/reportes/reporte-matriculas`)

- Maquetar según `maquetacion-html/HU-041-PF-REPORTE-matriculas.html`.
- Filtros: texto libre, select de estado, select de curso (poblado desde
  `CursoAdminApiService.listar('', 0, 100)`), select de modalidad, y dos campos de fecha
  (desde/hasta), con botones "Buscar" y "Limpiar". El rango de fechas se inicializa por defecto al
  "mes anterior hasta hoy" (no vacío), y valida en el propio formulario que `hasta` no sea anterior
  a `desde` antes de llamar al backend.
- Tabla paginada con columnas **Alumno** (+correo), **Curso**, **Modalidad**, **Matrícula** (fecha
  + badge de estado), **Acceso** (fecha de activación o `—`), **Origen** (badge), **Situación
  académica** (badge) y **Certificado** — "Acceso" y "Situación" son columnas separadas a
  propósito, no se combinan.
- Tres botones de exportación (PDF en rojo, Excel en verde, CSV en azul) que abren la URL de
  exportación correspondiente en una pestaña nueva (`window.open(...)`), con los filtros actuales
  — no hace falta manejar la descarga manualmente, el navegador la gestiona por el
  `Content-Disposition: attachment` que pone el backend.

### Servicio Angular

Ampliar `features/matriculas/matricula-api.service.ts` (ya existe) con:

```ts
reporte(filtros: FiltrosReporteMatricula, page = 0, size = 20): Observable<PageResponse<ReporteMatricula>> {
  const params = this.parametrosReporte(filtros).set('page', page).set('size', size);
  return this.http.get<PageResponse<ReporteMatricula>>(`${API_URL}/admin/reportes/matriculas`, { params });
}
urlExportacion(filtros: FiltrosReporteMatricula): string { return this.url('exportar', filtros); }
urlExportacionPdf(filtros: FiltrosReporteMatricula): string { return this.url('exportar-pdf', filtros); }
urlExportacionExcel(filtros: FiltrosReporteMatricula): string { return this.url('exportar-excel', filtros); }

private parametrosReporte(filtros: FiltrosReporteMatricula): HttpParams {
  let params = new HttpParams().set('texto', filtros.texto ?? '').set('estado', filtros.estado ?? '')
    .set('modalidad', filtros.modalidad ?? '');
  if (filtros.cursoId) params = params.set('cursoId', filtros.cursoId);
  if (filtros.fechaDesde) params = params.set('fechaDesde', filtros.fechaDesde);
  if (filtros.fechaHasta) params = params.set('fechaHasta', filtros.fechaHasta);
  return params;
}

private url(formato: string, filtros: FiltrosReporteMatricula): string {
  return `${API_URL}/admin/reportes/matriculas/${formato}?${this.parametrosReporte(filtros).toString()}`;
}
```

No olvides importar `HttpParams` de `@angular/common/http` y `PageResponse` de
`../../core/api/page-response.model`.

## Pruebas mínimas

- `reporte` filtra correctamente por texto y por estado (delega al repositorio y devuelve solo lo
  que coincide).
- `reportePaginado` delega los filtros y la paginación tal cual al repositorio
  (`buscarReporte(...)`), devolviendo el total de elementos correcto.
- El origen recalculado prioriza `EXONERADO` > `REGISTRADO_MANUAL` > `PAGO_EN_LINEA` cuando una
  matrícula tiene varios pagos.
- `validarRango` lanza `BusinessValidationException` si `fechaHasta` es anterior a `fechaDesde`.
- El CSV/Excel/PDF exportados tienen exactamente una fila por matrícula filtrada, con las mismas
  etiquetas legibles que la tabla del panel (no los códigos crudos).
- Una lista vacía de filas produce un CSV solo con encabezado, y un Excel/PDF válidos (sin
  filas de datos).

Por la instrucción vigente del proyecto de no escribir nuevos tests JUnit, estas verificaciones se
confirman manualmente (Swagger / la UI) salvo que se indique lo contrario.

## Terminado cuando

Un administrador filtra el historial completo de matrículas por texto, estado, curso, modalidad y
rango de fechas, ve una fila por matrícula con acceso y situación académica separados, y puede
descargar exactamente lo mismo que está viendo en PDF, Excel o CSV. Las historias HU-017, HU-019,
HU-020 y HU-021 siguen funcionando exactamente igual que antes.

## Implementación guiada para copiar y adaptar

El código de esta sección es el que **realmente estuvo funcionando en producción** antes de
retirarse — se copia tal cual, no hay que inventar nada.

### 1. Orden sugerido para reconstruir

1. Backend: completar `MatriculaServicio.java` (ya existe) con `reportePaginado`/`reporte` y sus
   helpers del punto 2 de abajo.
2. Backend: `export/ReporteMatriculaExportador.java` (se recrea completo, no existe) — punto 3.
3. Backend: completar `MatriculaControlador.java` (ya existe) con los 3 endpoints y el campo
   `exportador` — punto 4.
4. Frontend: completar `matricula-api.service.ts` (ya existe) con los métodos del punto 5.
5. Frontend: `reporte-matriculas` (ts/html/scss) — reemplazar el placeholder `.pantalla-pendiente`
   por el contenido real del punto 6.

### 2. `MatriculaServicio.java` — métodos y helpers a agregar (ya existe la clase)

```java
@Transactional
public Page<ReporteMatriculaRespuesta> reportePaginado(
        String texto, String estado, Long cursoId, String modalidad,
        LocalDate desde, LocalDate hasta, Pageable pageable) {
    procesarVencimientos();
    exigirAdministrador();
    validarRango(desde, hasta);
    Page<ReporteMatriculaRespuesta> pagina = matriculas.buscarReporte(normalizar(texto), normalizarEstado(estado), cursoId,
            normalizarEstado(modalidad), inicio(desde), finExclusivo(hasta), pageable);
    Map<Long, String> origenes = origenesReporte(pagina.getContent());
    return pagina.map(f -> conOrigen(f, origenes.getOrDefault(f.matriculaId(), f.formaIngreso())));
}

@Transactional
public List<ReporteMatriculaRespuesta> reporte(
        String texto, String estado, Long cursoId, String modalidad, LocalDate desde, LocalDate hasta) {
    procesarVencimientos();
    exigirAdministrador();
    validarRango(desde, hasta);
    List<ReporteMatriculaRespuesta> filas = matriculas.exportarReporte(normalizar(texto), normalizarEstado(estado), cursoId,
            normalizarEstado(modalidad), inicio(desde), finExclusivo(hasta));
    Map<Long, String> origenes = origenesReporte(filas);
    return filas.stream().map(f -> conOrigen(f, origenes.getOrDefault(f.matriculaId(), f.formaIngreso()))).toList();
}

private Map<Long, String> origenesReporte(List<ReporteMatriculaRespuesta> filas) {
    if (filas.isEmpty()) return Map.of();
    List<Long> ids = filas.stream().map(ReporteMatriculaRespuesta::matriculaId).toList();
    Map<Long, List<Pago>> pagosPorMatricula = pagos.findByMatricula_IdIn(ids).stream()
            .collect(Collectors.groupingBy(p -> p.getMatricula().getId()));
    return pagosPorMatricula.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry -> origenReporte(entry.getValue())));
}

/** Prioridad si hay varios pagos: EXONERADO > REGISTRADO_MANUAL > PAGO_EN_LINEA. El reporte
 * exige el origen economico real, no solo como entro el alumno (formaIngreso). */
private String origenReporte(List<Pago> pagosDeMatricula) {
    if (pagosDeMatricula.stream().anyMatch(p -> "EXONERADO".equals(p.getOrigen()))) return "EXONERADO";
    if (pagosDeMatricula.stream().anyMatch(p -> "MANUAL".equals(p.getOrigen()))) return "REGISTRADO_MANUAL";
    if (pagosDeMatricula.stream().anyMatch(p -> "APROBADO".equals(p.getEstado()))) return "PAGO_EN_LINEA";
    return pagosDeMatricula.getFirst().getOrigen();
}

private ReporteMatriculaRespuesta conOrigen(ReporteMatriculaRespuesta f, String origen) {
    return new ReporteMatriculaRespuesta(f.matriculaId(), f.alumno(), f.correo(), f.curso(), f.modalidad(),
            f.fechaMatricula(), f.fechaActivacion(), f.estadoMatricula(), origen,
            f.situacionAcademica(), f.estadoCertificado());
}

private static String normalizar(String valor) {
    return valor == null ? "" : valor.strip().toLowerCase();
}

private static String normalizarEstado(String valor) {
    return valor == null ? "" : valor.strip().toUpperCase();
}

private static void validarRango(LocalDate desde, LocalDate hasta) {
    if (desde != null && hasta != null && hasta.isBefore(desde)) {
        throw new BusinessValidationException("La fecha final no puede ser anterior a la fecha inicial.");
    }
}

private static Instant inicio(LocalDate fecha) {
    // Se envía siempre un Instant tipado: PostgreSQL no puede inferir el tipo
    // de un parámetro nulo usado en una comparación opcional.
    return fecha == null
            ? LocalDate.of(1, 1, 1).atStartOfDay(LIMA).toInstant()
            : fecha.atStartOfDay(LIMA).toInstant();
}

private static Instant finExclusivo(LocalDate fecha) {
    return fecha == null
            ? LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(LIMA).toInstant()
            : fecha.plusDays(1).atStartOfDay(LIMA).toInstant();
}
```

Recuerda agregar los imports correspondientes: `org.springframework.data.domain.Page`,
`org.springframework.data.domain.Pageable`, `pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta`.

### 3. `export/ReporteMatriculaExportador.java` completo (código real original, se recrea)

```java
package pe.edu.utp.escuela.app.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta;

/**
 * Genera los archivos del reporte de matriculas (HU-041) en CSV, Excel y PDF, con la misma
 * paleta de colores y etiquetas que usa la tabla del panel admin (ver badges en
 * reporte-matriculas.ts del frontend) para que el archivo descargado se vea igual de cuidado.
 */
@Component
public class ReporteMatriculaExportador {

    private static final String[] ENCABEZADOS = {
            "Alumno", "Correo", "Curso", "Modalidad", "Fecha matricula", "Fecha activacion",
            "Estado", "Origen", "Situacion academica", "Estado del certificado",
    };

    private static final byte[] COLOR_MARCA = hexARgb("103860");
    private static final byte[] COLOR_BLANCO = hexARgb("FFFFFF");
    private static final byte[] COLOR_ZEBRA = hexARgb("F8FAFC");
    private static final byte[] COLOR_BORDE = hexARgb("EAECF0");
    private static final byte[] COLOR_SUTIL = hexARgb("667085");
    private static final byte[] COLOR_EXITO = hexARgb("027A48");
    private static final byte[] COLOR_ERROR = hexARgb("B42318");
    private static final byte[] COLOR_CELESTE = hexARgb("026AA2");
    private static final byte[] COLOR_VIOLETA = hexARgb("6941C6");

    private static final Color AWT_MARCA = new Color(0x10, 0x38, 0x60);
    private static final Color AWT_ZEBRA = new Color(0xF8, 0xFA, 0xFC);
    private static final Color AWT_BORDE = new Color(0xEA, 0xEC, 0xF0);
    private static final Color AWT_SUTIL = new Color(0x66, 0x70, 0x85);
    private static final Color AWT_EXITO = new Color(0x02, 0x7A, 0x48);
    private static final Color AWT_ERROR = new Color(0xB4, 0x23, 0x18);
    private static final Color AWT_CELESTE = new Color(0x02, 0x6A, 0xA2);
    private static final Color AWT_VIOLETA = new Color(0x69, 0x41, 0xC6);

    // --------------------------------------------------------------------------------- CSV --

    public byte[] csv(List<ReporteMatriculaRespuesta> filas) {
        StringBuilder csv = new StringBuilder(String.join(";", ENCABEZADOS)).append('\n');
        for (ReporteMatriculaRespuesta fila : filas) {
            csv.append(valorCsv(fila.alumno())).append(';').append(valorCsv(fila.correo())).append(';')
                    .append(valorCsv(fila.curso())).append(';').append(valorCsv(fila.modalidad())).append(';')
                    .append(formatoFecha(fila.fechaMatricula())).append(';').append(formatoFecha(fila.fechaActivacion())).append(';')
                    .append(valorCsv(etiquetaEstado(fila.estadoMatricula()))).append(';')
                    .append(valorCsv(etiquetaIngreso(fila.formaIngreso()))).append(';')
                    .append(valorCsv(etiquetaSituacion(fila.situacionAcademica()))).append(';')
                    .append(valorCsv(etiquetaCertificado(fila.estadoCertificado()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String valorCsv(String valor) {
        return "\"" + (valor == null ? "" : valor.replace("\"", "\"\"")) + "\"";
    }

    // ------------------------------------------------------------------------------- Excel --

    public byte[] excel(List<ReporteMatriculaRespuesta> filas, String textoFiltro, String estadoFiltro) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Matriculas");
            Map<String, CellStyle> cacheEstilos = new HashMap<>();

            Row filaTitulo = hoja.createRow(0);
            Cell celdaTitulo = filaTitulo.createCell(0);
            celdaTitulo.setCellValue("Reporte de matrículas · Escuela Jurídica");
            celdaTitulo.setCellStyle(estiloTexto(libro, 15, true, COLOR_MARCA, null));
            hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ENCABEZADOS.length - 1));

            Row filaSubtitulo = hoja.createRow(1);
            Cell celdaSubtitulo = filaSubtitulo.createCell(0);
            celdaSubtitulo.setCellValue(subtitulo(filas.size(), textoFiltro, estadoFiltro));
            celdaSubtitulo.setCellStyle(estiloTexto(libro, 9, false, COLOR_SUTIL, null));
            hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, ENCABEZADOS.length - 1));

            int filaEncabezadoIdx = 3;
            Row filaEncabezado = hoja.createRow(filaEncabezadoIdx);
            filaEncabezado.setHeightInPoints(22f);
            CellStyle estiloEncabezado = estiloEncabezado(libro);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = filaEncabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            int numeroFila = filaEncabezadoIdx + 1;
            for (ReporteMatriculaRespuesta fila : filas) {
                boolean par = (numeroFila - filaEncabezadoIdx) % 2 == 0;
                Row filaDatos = hoja.createRow(numeroFila++);
                filaDatos.setHeightInPoints(17f);
                escribirCelda(libro, cacheEstilos, filaDatos, 0, fila.alumno(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 1, fila.correo(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 2, fila.curso(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 3, fila.modalidad(), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 4, formatoFecha(fila.fechaMatricula()), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 5, formatoFecha(fila.fechaActivacion()), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 6, etiquetaEstado(fila.estadoMatricula()), par,
                        "ACTIVA".equals(fila.estadoMatricula()) ? COLOR_EXITO : COLOR_ERROR);
                escribirCelda(libro, cacheEstilos, filaDatos, 7, etiquetaIngreso(fila.formaIngreso()), par,
                        colorIngreso(fila.formaIngreso()));
                escribirCelda(libro, cacheEstilos, filaDatos, 8, etiquetaSituacion(fila.situacionAcademica()), par, null);
                escribirCelda(libro, cacheEstilos, filaDatos, 9, etiquetaCertificado(fila.estadoCertificado()), par, null);
            }

            int[] anchos = { 7500, 8500, 9000, 4500, 6500, 6500, 4500, 6000, 6000, 6500 };
            for (int i = 0; i < anchos.length; i++) {
                hoja.setColumnWidth(i, anchos[i]);
            }
            hoja.createFreezePane(0, filaEncabezadoIdx + 1);
            hoja.setAutoFilter(new CellRangeAddress(filaEncabezadoIdx, filaEncabezadoIdx, 0, ENCABEZADOS.length - 1));

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el Excel del reporte.", e);
        }
    }

    private void escribirCelda(XSSFWorkbook libro, Map<String, CellStyle> cache, Row fila, int indice,
            String valor, boolean par, byte[] colorFuente) {
        Cell celda = fila.createCell(indice);
        celda.setCellValue(valor == null ? "" : valor);
        celda.setCellStyle(estiloFila(libro, cache, par, colorFuente));
    }

    private CellStyle estiloEncabezado(XSSFWorkbook libro) {
        XSSFCellStyle estilo = libro.createCellStyle();
        estilo.setFillForegroundColor(new XSSFColor(COLOR_MARCA, null));
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setAlignment(HorizontalAlignment.LEFT);
        XSSFFont fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setColor(new XSSFColor(COLOR_BLANCO, null));
        fuente.setFontHeightInPoints((short) 10);
        estilo.setFont(fuente);
        return estilo;
    }

    private CellStyle estiloFila(XSSFWorkbook libro, Map<String, CellStyle> cache, boolean par, byte[] colorFuente) {
        String clave = par + "|" + (colorFuente == null ? "default" : new String(colorFuente));
        return cache.computeIfAbsent(clave, k -> {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.setFillForegroundColor(new XSSFColor(par ? COLOR_ZEBRA : COLOR_BLANCO, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBottomBorderColor(new XSSFColor(COLOR_BORDE, null));
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont fuente = libro.createFont();
            fuente.setFontHeightInPoints((short) 10);
            if (colorFuente != null) {
                fuente.setColor(new XSSFColor(colorFuente, null));
                fuente.setBold(true);
            }
            estilo.setFont(fuente);
            return estilo;
        });
    }

    private CellStyle estiloTexto(XSSFWorkbook libro, int tamano, boolean negrita, byte[] colorFuente, byte[] colorFondo) {
        XSSFCellStyle estilo = libro.createCellStyle();
        XSSFFont fuente = libro.createFont();
        fuente.setBold(negrita);
        fuente.setFontHeightInPoints((short) tamano);
        fuente.setColor(new XSSFColor(colorFuente, null));
        estilo.setFont(fuente);
        if (colorFondo != null) {
            estilo.setFillForegroundColor(new XSSFColor(colorFondo, null));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return estilo;
    }

    private byte[] colorIngreso(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> COLOR_CELESTE;
            case "ADMINISTRADOR", "EXONERADA", "EXONERADO" -> COLOR_VIOLETA;
            case "MANUAL", "REGISTRADO_MANUAL" -> COLOR_CELESTE;
            case "PAGO_EN_LINEA" -> COLOR_EXITO;
            default -> null;
        };
    }

    private static byte[] hexARgb(String hex) {
        return new byte[] {
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16),
        };
    }

    // --------------------------------------------------------------------------------- PDF --

    public byte[] pdf(List<ReporteMatriculaRespuesta> filas, String textoFiltro, String estadoFiltro) {
        float[] anchos = { 90, 105, 105, 52, 62, 62, 55, 72, 68, 65 };
        float margen = 25f;
        float altoFila = 17f;
        float altoEncabezadoTabla = 20f;
        float anchoTotal = sumaAnchos(anchos);
        PDRectangle tamano = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        PDType1Font fuenteNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fuenteNegrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fuenteCursiva = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            int numeroPagina = 1;
            PDPage pagina = new PDPage(tamano);
            documento.addPage(pagina);
            PDPageContentStream flujo = new PDPageContentStream(documento, pagina);
            float y = tamano.getHeight() - margen;
            y = dibujarTitulo(flujo, margen, y, filas.size(), textoFiltro, estadoFiltro, fuenteNegrita, fuenteCursiva);
            y = dibujarEncabezadoTabla(flujo, anchos, margen, y, altoEncabezadoTabla, anchoTotal, fuenteNegrita);
            dibujarPie(flujo, margen, tamano.getWidth(), numeroPagina, fuenteNormal);

            int indiceFila = 0;
            for (ReporteMatriculaRespuesta fila : filas) {
                if (y - altoFila < margen) {
                    flujo.close();
                    numeroPagina++;
                    pagina = new PDPage(tamano);
                    documento.addPage(pagina);
                    flujo = new PDPageContentStream(documento, pagina);
                    y = tamano.getHeight() - margen;
                    y = dibujarEncabezadoTabla(flujo, anchos, margen, y, altoEncabezadoTabla, anchoTotal, fuenteNegrita);
                    dibujarPie(flujo, margen, tamano.getWidth(), numeroPagina, fuenteNormal);
                    indiceFila = 0;
                }
                y = dibujarFila(flujo, fila, anchos, margen, y, altoFila, anchoTotal, indiceFila % 2 == 0,
                        fuenteNormal, fuenteNegrita);
                indiceFila++;
            }
            flujo.close();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el PDF del reporte.", e);
        }
    }

    private float dibujarTitulo(PDPageContentStream flujo, float margen, float y, int totalFilas,
            String textoFiltro, String estadoFiltro, PDType1Font fuenteNegrita, PDType1Font fuenteCursiva) throws IOException {
        flujo.setNonStrokingColor(AWT_MARCA);
        flujo.beginText();
        flujo.setFont(fuenteNegrita, 14);
        flujo.newLineAtOffset(margen, y - 12);
        flujo.showText("Reporte de matriculas - Escuela Juridica");
        flujo.endText();

        flujo.setNonStrokingColor(AWT_SUTIL);
        flujo.beginText();
        flujo.setFont(fuenteCursiva, 8);
        flujo.newLineAtOffset(margen, y - 27);
        flujo.showText(subtitulo(totalFilas, textoFiltro, estadoFiltro));
        flujo.endText();

        return y - 42;
    }

    private String subtitulo(int totalFilas, String textoFiltro, String estadoFiltro) {
        String filtroTexto = (textoFiltro == null || textoFiltro.isBlank()) ? "todos" : textoFiltro;
        String filtroEstado = (estadoFiltro == null || estadoFiltro.isBlank()) ? "todos" : estadoFiltro;
        return "Generado el " + formatoFecha(Instant.now()) + "  |  Busqueda: " + filtroTexto
                + "  |  Estado: " + filtroEstado + "  |  " + totalFilas + " matricula(s)";
    }

    private float dibujarEncabezadoTabla(PDPageContentStream flujo, float[] anchos, float margen, float y,
            float altoEncabezado, float anchoTotal, PDType1Font fuenteNegrita) throws IOException {
        flujo.setNonStrokingColor(AWT_MARCA);
        flujo.addRect(margen, y - altoEncabezado, anchoTotal, altoEncabezado);
        flujo.fill();

        flujo.setNonStrokingColor(Color.WHITE);
        float x = margen;
        for (int i = 0; i < ENCABEZADOS.length; i++) {
            flujo.beginText();
            flujo.setFont(fuenteNegrita, 9);
            flujo.newLineAtOffset(x + 4, y - altoEncabezado + 7);
            flujo.showText(ENCABEZADOS[i]);
            flujo.endText();
            x += anchos[i];
        }
        return y - altoEncabezado;
    }

    private float dibujarFila(PDPageContentStream flujo, ReporteMatriculaRespuesta fila, float[] anchos,
            float margen, float y, float altoFila, float anchoTotal, boolean par,
            PDType1Font fuenteNormal, PDType1Font fuenteNegrita) throws IOException {
        float yInferior = y - altoFila;

        flujo.setNonStrokingColor(par ? Color.WHITE : AWT_ZEBRA);
        flujo.addRect(margen, yInferior, anchoTotal, altoFila);
        flujo.fill();
        flujo.setStrokingColor(AWT_BORDE);
        flujo.setLineWidth(0.5f);
        flujo.moveTo(margen, yInferior);
        flujo.lineTo(margen + anchoTotal, yInferior);
        flujo.stroke();

        String[] valores = {
                recorte(fila.alumno(), 42), recorte(fila.correo(), 48), recorte(fila.curso(), 44),
                recorte(fila.modalidad(), 14), formatoFecha(fila.fechaMatricula()), formatoFecha(fila.fechaActivacion()),
                etiquetaEstado(fila.estadoMatricula()), etiquetaIngreso(fila.formaIngreso()),
                etiquetaSituacion(fila.situacionAcademica()), etiquetaCertificado(fila.estadoCertificado()),
        };
        Color colorEstado = "ACTIVA".equals(fila.estadoMatricula()) ? AWT_EXITO : AWT_ERROR;
        Color[] colores = { null, null, null, null, null, null, colorEstado,
                colorIngresoPdf(fila.formaIngreso()), null, null };

        float x = margen;
        for (int i = 0; i < valores.length; i++) {
            flujo.setNonStrokingColor(colores[i] != null ? colores[i] : Color.BLACK);
            flujo.beginText();
            flujo.setFont(colores[i] != null ? fuenteNegrita : fuenteNormal, 8);
            flujo.newLineAtOffset(x + 4, yInferior + 6);
            flujo.showText(valores[i] == null ? "" : valores[i]);
            flujo.endText();
            x += anchos[i];
        }
        return yInferior;
    }

    private void dibujarPie(PDPageContentStream flujo, float margen, float anchoPagina, int numeroPagina,
            PDType1Font fuenteNormal) throws IOException {
        flujo.setNonStrokingColor(AWT_SUTIL);
        flujo.beginText();
        flujo.setFont(fuenteNormal, 8);
        flujo.newLineAtOffset(anchoPagina - margen - 45, 14);
        flujo.showText("Pagina " + numeroPagina);
        flujo.endText();
    }

    private Color colorIngresoPdf(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> AWT_CELESTE;
            case "ADMINISTRADOR", "EXONERADA", "EXONERADO" -> AWT_VIOLETA;
            case "MANUAL", "REGISTRADO_MANUAL" -> AWT_CELESTE;
            case "PAGO_EN_LINEA" -> AWT_EXITO;
            default -> null;
        };
    }

    private float sumaAnchos(float[] anchos) {
        float total = 0;
        for (float ancho : anchos) {
            total += ancho;
        }
        return total;
    }

    private String recorte(String valor, int maximo) {
        if (valor == null) return "";
        return valor.length() > maximo ? valor.substring(0, maximo - 1) + "..." : valor;
    }

    // ------------------------------------------------------------------------- Compartido --

    private String formatoFecha(Instant instante) {
        if (instante == null) return "-";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima")).format(instante);
    }

    private String etiquetaEstado(String estado) {
        return switch (estado) {
            case "ACTIVA" -> "Activa";
            case "CANCELADA" -> "Cancelada";
            case "VENCIDA" -> "Vencida";
            case "PENDIENTE_PAGO" -> "Pendiente de pago";
            case "FINALIZADA" -> "Finalizada";
            default -> estado == null ? "Sin estado" : estado;
        };
    }

    private String etiquetaIngreso(String formaIngreso) {
        return switch (formaIngreso) {
            case "GRATUITA" -> "Gratuita";
            case "ADMINISTRADOR" -> "Asignacion manual";
            case "MANUAL", "REGISTRADO_MANUAL" -> "Pago manual registrado";
            case "PAGO_EN_LINEA" -> "Pago en linea";
            case "EXONERADA", "EXONERADO" -> "Exonerada";
            default -> formaIngreso;
        };
    }

    private String etiquetaSituacion(String situacion) {
        return "EN_CURSO".equals(situacion) ? "En curso" : "Finalizado";
    }

    private String etiquetaCertificado(String estado) {
        return switch (estado == null ? "NO_EMITIDO" : estado) {
            case "VIGENTE" -> "Vigente";
            case "ANULADO" -> "Anulado";
            case "NO_EMITIDO" -> "No emitido";
            default -> estado;
        };
    }
}
```

### 4. `MatriculaControlador.java` — endpoints y campo a agregar (ya existe la clase)

```java
// Campo, inyectado por constructor junto al resto (Lombok @RequiredArgsConstructor):
private final ReporteMatriculaExportador exportador;

@GetMapping("/admin/reportes/matriculas")
public ResponseEntity<PageResponse<ReporteMatriculaRespuesta>> reporte(
        @RequestParam(defaultValue = "") String texto,
        @RequestParam(defaultValue = "") String estado,
        @RequestParam(required = false) Long cursoId,
        @RequestParam(defaultValue = "") String modalidad,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    int tamano = Math.min(Math.max(size, 1), 50);
    Page<ReporteMatriculaRespuesta> pagina = servicio.reportePaginado(texto, estado, cursoId,
            modalidad, fechaDesde, fechaHasta, PageRequest.of(Math.max(page, 0), tamano));
    return ResponseEntity.ok(PageResponse.from(pagina));
}

@GetMapping("/admin/reportes/matriculas/exportar")
public ResponseEntity<byte[]> exportarReporte(
        @RequestParam(defaultValue = "") String texto,
        @RequestParam(defaultValue = "") String estado,
        @RequestParam(required = false) Long cursoId,
        @RequestParam(defaultValue = "") String modalidad,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
    byte[] csv = exportador.csv(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta));
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.csv")
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)).body(csv);
}

@GetMapping("/admin/reportes/matriculas/exportar-excel")
public ResponseEntity<byte[]> exportarExcel(
        @RequestParam(defaultValue = "") String texto,
        @RequestParam(defaultValue = "") String estado,
        @RequestParam(required = false) Long cursoId,
        @RequestParam(defaultValue = "") String modalidad,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
    byte[] excel = exportador.excel(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta), texto, estado);
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel);
}

@GetMapping("/admin/reportes/matriculas/exportar-pdf")
public ResponseEntity<byte[]> exportarPdf(
        @RequestParam(defaultValue = "") String texto,
        @RequestParam(defaultValue = "") String estado,
        @RequestParam(required = false) Long cursoId,
        @RequestParam(defaultValue = "") String modalidad,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
    byte[] pdf = exportador.pdf(servicio.reporte(texto, estado, cursoId, modalidad, fechaDesde, fechaHasta), texto, estado);
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reportes-matriculas.pdf")
            .contentType(MediaType.APPLICATION_PDF).body(pdf);
}
```

Imports que hacen falta en el controlador: `java.time.LocalDate`,
`org.springframework.data.domain.Page`, `org.springframework.data.domain.PageRequest`,
`org.springframework.format.annotation.DateTimeFormat`, `org.springframework.http.HttpHeaders`,
`org.springframework.http.MediaType`, `pe.edu.utp.escuela.app.dto.PageResponse`,
`pe.edu.utp.escuela.app.dto.ReporteMatriculaRespuesta`,
`pe.edu.utp.escuela.app.export.ReporteMatriculaExportador`.

### 5. Frontend — métodos a agregar en `matricula-api.service.ts` (ya existe la clase)

```ts
reporte(filtros: FiltrosReporteMatricula, page = 0, size = 20): Observable<PageResponse<ReporteMatricula>> {
  const params = this.parametrosReporte(filtros).set('page', page).set('size', size);
  return this.http.get<PageResponse<ReporteMatricula>>(`${API_URL}/admin/reportes/matriculas`, { params });
}
urlExportacion(filtros: FiltrosReporteMatricula): string { return this.url('exportar', filtros); }
urlExportacionPdf(filtros: FiltrosReporteMatricula): string { return this.url('exportar-pdf', filtros); }
urlExportacionExcel(filtros: FiltrosReporteMatricula): string { return this.url('exportar-excel', filtros); }

private parametrosReporte(filtros: FiltrosReporteMatricula): HttpParams {
  let params = new HttpParams().set('texto', filtros.texto ?? '').set('estado', filtros.estado ?? '')
    .set('modalidad', filtros.modalidad ?? '');
  if (filtros.cursoId) params = params.set('cursoId', filtros.cursoId);
  if (filtros.fechaDesde) params = params.set('fechaDesde', filtros.fechaDesde);
  if (filtros.fechaHasta) params = params.set('fechaHasta', filtros.fechaHasta);
  return params;
}

private url(formato: string, filtros: FiltrosReporteMatricula): string {
  return `${API_URL}/admin/reportes/matriculas/${formato}?${this.parametrosReporte(filtros).toString()}`;
}
```

### 6. Frontend — `ReporteMatriculas` (componente completo, código real original)

`reporte-matriculas.ts`:

```ts
import { DatePipe, LowerCasePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatriculaApiService, ReporteMatricula, FiltrosReporteMatricula } from '../../../matriculas/matricula-api.service';
import { CursoAdminApiService } from '../../cursos/curso-admin-api.service';
import { CursoResumenRespuesta } from '../../cursos/curso-admin.model';

@Component({
  selector: 'app-reporte-matriculas',
  imports: [FormsModule, DatePipe, LowerCasePipe],
  templateUrl: './reporte-matriculas.html',
  styleUrl: './reporte-matriculas.scss',
})
export class ReporteMatriculas {
  private readonly api = inject(MatriculaApiService);
  private readonly cursosApi = inject(CursoAdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly filas = signal<ReporteMatricula[]>([]);
  protected readonly cursos = signal<CursoResumenRespuesta[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected texto = '';
  protected estado = '';
  protected cursoId: number | null = null;
  protected modalidad = '';
  protected fechaDesde = '';
  protected fechaHasta = '';
  protected readonly fechaActual = this.formatoFechaLocal(new Date());
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);

  constructor() {
    this.inicializarRangoFechas();
    this.cursosApi.listar('', 0, 100).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => this.cursos.set(r.items),
    });
    this.cargar();
  }

  protected buscar(): void {
    if (this.fechaDesde && this.fechaHasta && this.fechaHasta < this.fechaDesde) {
      this.error.set('La fecha final no puede ser anterior a la inicial.');
      return;
    }
    this.pagina.set(0);
    this.cargar();
  }

  protected limpiarFiltros(): void {
    this.texto = '';
    this.estado = '';
    this.cursoId = null;
    this.modalidad = '';
    this.inicializarRangoFechas();
    this.buscar();
  }

  protected irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginas()) return;
    this.pagina.set(pagina);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.reporte(this.filtros(), this.pagina(), 20).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => {
        this.filas.set(r.items);
        this.totalPaginas.set(r.totalPages);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No pudimos cargar el reporte. Inténtalo nuevamente.');
        this.cargando.set(false);
      },
    });
  }

  protected exportarPdf(): void { window.open(this.api.urlExportacionPdf(this.filtros()), '_blank'); }
  protected exportarExcel(): void { window.open(this.api.urlExportacionExcel(this.filtros()), '_blank'); }
  protected exportar(): void { window.open(this.api.urlExportacion(this.filtros()), '_blank'); }

  protected etiquetaEstado(estado: string): string {
    switch (estado) {
      case 'ACTIVA': return 'Activa';
      case 'CANCELADA': return 'Cancelada';
      case 'VENCIDA': return 'Vencida';
      case 'PENDIENTE_PAGO': return 'Pendiente de pago';
      case 'FINALIZADA': return 'Finalizada';
      default: return estado;
    }
  }

  protected claseEstado(estado: string): string {
    return estado === 'ACTIVA' ? 'badge--disp-inmediato'
      : estado === 'VENCIDA' ? 'badge--disp-proximo' : 'badge--disp-cancelado';
  }

  protected etiquetaIngreso(formaIngreso: string): string {
    switch (formaIngreso) {
      case 'GRATUITA': return 'Gratuita';
      case 'ADMINISTRADOR': return 'Asignación manual';
      case 'REGISTRADO_MANUAL': return 'Pago manual registrado';
      case 'PAGO_EN_LINEA': return 'Pago en línea';
      case 'EXONERADA': case 'EXONERADO': return 'Exonerada';
      default: return formaIngreso;
    }
  }

  protected claseIngreso(formaIngreso: string): string {
    switch (formaIngreso) {
      case 'GRATUITA': return 'badge--ingreso-gratuito';
      case 'ADMINISTRADOR': return 'badge--ingreso-manual';
      case 'REGISTRADO_MANUAL': return 'badge--ingreso-pago';
      case 'PAGO_EN_LINEA': return 'badge--ingreso-pago';
      case 'EXONERADA': case 'EXONERADO': return 'badge--ingreso-manual';
      default: return 'badge--disp-cerrado';
    }
  }

  protected etiquetaSituacion(situacion: string): string {
    return situacion === 'EN_CURSO' ? 'En curso' : 'Finalizado';
  }

  protected claseSituacion(situacion: string): string {
    return situacion === 'EN_CURSO' ? 'badge--disp-proximo' : 'badge--disp-cerrado';
  }

  protected etiquetaCertificado(estado: string): string {
    switch (estado) {
      case 'VIGENTE': return 'Vigente';
      case 'ANULADO': return 'Anulado';
      default: return 'No emitido';
    }
  }

  private filtros(): FiltrosReporteMatricula {
    return {
      texto: this.texto,
      estado: this.estado,
      cursoId: this.cursoId,
      modalidad: this.modalidad,
      fechaDesde: this.fechaDesde,
      fechaHasta: this.fechaHasta,
    };
  }

  private inicializarRangoFechas(): void {
    const hoy = new Date();
    const dia = hoy.getDate();
    const mesAnterior = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1);
    const ultimoDiaMesAnterior = new Date(mesAnterior.getFullYear(), mesAnterior.getMonth() + 1, 0).getDate();
    mesAnterior.setDate(Math.min(dia, ultimoDiaMesAnterior));
    this.fechaDesde = this.formatoFechaLocal(mesAnterior);
    this.fechaHasta = this.formatoFechaLocal(hoy);
  }

  private formatoFechaLocal(fecha: Date): string {
    const anio = fecha.getFullYear();
    const mes = String(fecha.getMonth() + 1).padStart(2, '0');
    const dia = String(fecha.getDate()).padStart(2, '0');
    return `${anio}-${mes}-${dia}`;
  }
}
```

`reporte-matriculas.html`:

```html
<section class="reporte">
  <div class="reporte__cabecera">
    <div>
      <h1 class="reporte__titulo">Reporte de matrículas</h1>
      <p>Una fila por matrícula, con acceso y situación académica separados.</p>
    </div>
    <div class="reporte__acciones-exportar">
      <button type="button" class="btn btn--peligro reporte__btn-exportar" (click)="exportarPdf()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <path d="M14 2v6h6" />
          <path d="M9 15h6" />
          <path d="M9 11h6" />
        </svg>
        Exportar PDF
      </button>
      <button type="button" class="btn btn--exito reporte__btn-exportar" (click)="exportarExcel()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <line x1="3" y1="9" x2="21" y2="9" />
          <line x1="3" y1="15" x2="21" y2="15" />
          <line x1="9" y1="3" x2="9" y2="21" />
          <line x1="15" y1="3" x2="15" y2="21" />
        </svg>
        Exportar Excel
      </button>
      <button type="button" class="btn btn--primario reporte__btn-exportar" (click)="exportar()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3v12" />
          <path d="M7 10l5 5 5-5" />
          <path d="M5 21h14" />
        </svg>
        Exportar CSV
      </button>
    </div>
  </div>

  <div class="reporte__filtros">
    <input class="input" [(ngModel)]="texto" (keyup.enter)="buscar()" placeholder="Alumno, correo o curso" />
    <select class="input" [(ngModel)]="estado" (change)="buscar()">
      <option value="">Todos los estados</option>
      <option value="ACTIVA">Activa</option>
      <option value="CANCELADA">Cancelada</option>
      <option value="VENCIDA">Vencida</option>
      <option value="PENDIENTE_PAGO">Pendiente de pago</option>
    </select>
    <select class="input" [(ngModel)]="cursoId">
      <option [ngValue]="null">Todos los cursos</option>
      @for (curso of cursos(); track curso.id) { <option [ngValue]="curso.id">{{ curso.titulo }}</option> }
    </select>
    <select class="input" [(ngModel)]="modalidad">
      <option value="">Todas las modalidades</option>
      <option value="VIRTUAL">Virtual</option>
      <option value="EN_VIVO">En vivo</option>
      <option value="HIBRIDO">Híbrido</option>
    </select>
    <label class="reporte__filtro-fecha">Desde<input class="input" type="date" [(ngModel)]="fechaDesde" [max]="fechaActual" /></label>
    <label class="reporte__filtro-fecha">Hasta<input class="input" type="date" [(ngModel)]="fechaHasta" [min]="fechaDesde" [max]="fechaActual" /></label>
    <div class="reporte__filtro-acciones">
      <button class="btn btn--acento reporte__btn-filtro" type="button" (click)="buscar()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="7" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        Buscar
      </button>
      <button class="btn reporte__btn-limpiar reporte__btn-filtro" type="button" (click)="limpiarFiltros()">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
        Limpiar
      </button>
    </div>
  </div>

  @if (cargando()) {
    <p>Cargando reporte…</p>
  } @else if (error(); as mensaje) {
    <div class="alerta alerta--error">
      <span class="alerta__icono">!</span>
      <div class="alerta__titulo">{{ mensaje }}</div>
    </div>
  } @else if (filas().length === 0) {
    <div class="estado-vacio">
      <h3 class="estado-vacio__titulo">No encontramos matrículas</h3>
      <p class="estado-vacio__texto">Prueba con otros filtros.</p>
    </div>
  } @else {
    <div class="reporte__tabla">
      <table>
        <thead>
          <tr>
            <th>Alumno</th>
            <th>Curso</th>
            <th>Modalidad</th>
            <th>Matrícula</th>
            <th>Acceso</th>
            <th>Origen</th>
            <th>Situación académica</th>
            <th>Certificado</th>
          </tr>
        </thead>
        <tbody>
          @for (fila of filas(); track fila.matriculaId) {
            <tr>
              <td><strong>{{ fila.alumno }}</strong><small>{{ fila.correo }}</small></td>
              <td>{{ fila.curso }}</td>
              <td>{{ fila.modalidad }}</td>
              <td>
                <div class="reporte__celda-matricula">
                  <span>{{ fila.fechaMatricula | date:'dd/MM/yyyy, h:mm a' | lowercase }}</span>
                  <span [class]="'badge ' + claseEstado(fila.estadoMatricula)">{{ etiquetaEstado(fila.estadoMatricula) }}</span>
                </div>
              </td>
              <td>{{ fila.fechaActivacion ? (fila.fechaActivacion | date:'dd/MM/yyyy, h:mm a' | lowercase) : '—' }}</td>
              <td><span [class]="'badge ' + claseIngreso(fila.formaIngreso)">{{ etiquetaIngreso(fila.formaIngreso) }}</span></td>
              <td><span [class]="'badge ' + claseSituacion(fila.situacionAcademica)">{{ etiquetaSituacion(fila.situacionAcademica) }}</span></td>
              <td>{{ etiquetaCertificado(fila.estadoCertificado) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (totalPaginas() > 1) {
      <div class="reporte__paginacion">
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
</section>
```

`reporte-matriculas.scss`:

```scss
// Sin padding propio: .admin-contenido (layout-admin) ya da los 32px, igual que en Matrículas.

.reporte__titulo {
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: var(--neutral-950);
  margin: 0;
}

.reporte__cabecera {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.reporte__cabecera p {
  margin: 4px 0 0;
  color: var(--neutral-600);
}

.reporte__acciones-exportar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.reporte__btn-exportar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.reporte__filtros {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 24px 0;
}

.reporte__filtros .input {
  min-width: 0;
  width: 100%;
}

.reporte__filtros select.input {
  width: 100%;
}

.reporte__filtro-fecha {
  color: var(--neutral-600);
  display: grid;
  font-size: 12px;
  font-weight: 600;
  gap: 5px;
}

.reporte__filtro-acciones {
  display: flex;
  align-items: end;
  gap: 12px;
}

.reporte__btn-filtro {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

// Gris sólido (no blanco), icono "X" simple: limpiar filtros no es una acción destructiva.
.reporte__btn-limpiar {
  background: var(--neutral-600);
  color: var(--blanco);
  transition: background-color 0.15s ease;
}

.reporte__btn-limpiar:hover {
  background: var(--neutral-800);
}

.reporte__tabla {
  overflow: auto;
  border: 1px solid var(--neutral-200);
  border-radius: 10px;
  background: var(--blanco);
}

.reporte__tabla table {
  border-collapse: collapse;
  width: 100%;
}

.reporte__tabla th,
.reporte__tabla td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--neutral-100);
  font-size: 14px;
  white-space: nowrap;
}

.reporte__tabla th {
  color: var(--neutral-500);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.reporte__tabla td strong {
  color: var(--neutral-950);
  font-weight: 600;
}

.reporte__tabla small {
  display: block;
  color: var(--neutral-500);
  font-size: 12px;
  margin-top: 2px;
}

.reporte__paginacion {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  font-size: 14px;
  color: var(--neutral-600);
}

.reporte__celda-matricula {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

@media (max-width: 700px) {
  .reporte__cabecera {
    flex-direction: column;
    align-items: stretch;
  }

  .reporte__filtros {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .reporte__filtros {
    grid-template-columns: minmax(0, 1fr);
  }
}
```
