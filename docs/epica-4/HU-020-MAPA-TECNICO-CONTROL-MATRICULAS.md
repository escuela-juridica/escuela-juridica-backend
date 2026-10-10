# HU-020 — Mapa técnico para controlar matrículas y pagos

> **Equipo: Joel y Juan** · Rama: `feature/HU-020-matriculas` (desde `develop`).

> Estado de integración: esta historia **ya estaba implementada y funcionando** (listado, detalle y
> cancelación de matrículas, con datos reales). Se retiró deliberadamente para convertirla en ejercicio
> de programación del equipo — esta guía documenta exactamente cómo estaba construida, con el código
> real, para que se reconstruya igual o mejor. **Importante:** `MatriculaServicio` y
> `MatriculaControlador` son archivos **compartidos** con HU-017 (matrícula gratuita), HU-019 (matricular
> administrativamente) y HU-021 (mis cursos), que siguen funcionando — no se tocó nada de esos. Tampoco
> se tocó HU-041 (reporte de matrículas), que vive en los mismos archivos pero es harina de otro costal
> (otra guía aparte). Lo único que se retiró son los tres métodos/endpoints exclusivos de esta historia.
>
> En el frontend, el componente `matriculas-listado` también es compartido: el botón "+ Nueva matrícula"
> y su modal (HU-019) **se quedaron funcionando tal cual** en la misma pantalla. Lo que se retiró es el
> listado/filtros/paginación y los modales "Detalle de matrícula" y "Cancelar matrícula".

## Resultado que debe entregar

Un administrador busca y filtra matrículas (por texto y por estado), ve el detalle completo de una
(acceso, historial de pagos separado del historial de cambios de estado) y puede cancelarla exigiendo un
motivo. El vencimiento se recalcula de forma idempotente antes de cada consulta.

## Punto de partida — ya existe, se reutiliza tal cual

- Entidades `Matricula`, `HistorialEstadoMatricula`, `Pago` — no se tocan.
- `MatriculaRepositorio.findWithDetalleById`, `.buscarAdministrativas(texto, estado, pageable)` — ya
  existen y siguen ahí.
- `HistorialEstadoMatriculaRepositorio`, `PagoRepositorio.findByMatricula_IdOrderByResultadoEnDesc` — ya
  existen.
- DTOs ya existen en `dto/`, no hace falta recrearlos: `MatriculaAdministrativaRespuesta`,
  `MatriculaDetalleAdministrativaRespuesta`, `PagoMatriculaDetalleRespuesta`,
  `HistorialEstadoMatriculaRespuesta`, `CancelarMatriculaPeticion`.
- En `MatriculaServicio` (que se queda, no se borra) ya están disponibles como helpers privados:
  `procesarVencimientos()`, `exigirAdministrador()`, `registrarCambio(...)`, `respuesta(Matricula, String)`.
  Reutilízalos, no los reescribas.
- **`normalizar(String)` y `normalizarEstado(String)` NO están** (los usaba también HU-041, que se
  retiró después y se los llevó porque ya no los usaba nadie). Son triviales, créalos de nuevo:
  ```java
  private static String normalizar(String valor) {
      return valor == null ? "" : valor.strip().toLowerCase();
  }

  private static String normalizarEstado(String valor) {
      return valor == null ? "" : valor.strip().toUpperCase();
  }
  ```
  Si para cuando implementes esto HU-041 ya está reconstruida y los volvió a crear, no los dupliques —
  solo revisa que existan antes de agregarlos tú.
- En Angular, `MatriculaApiService` (`features/matriculas/matricula-api.service.ts`) ya existe y ya tiene
  `matricularGratis`, `misCursos`, `crearAdministrativa`, `advertenciaAcademica`, `reenviarConfirmacion`
  — solo le faltan los 3 métodos de esta historia (los métodos de reporte son de HU-041, otra guía
  aparte, no te conciernen).
- El componente `features/admin/matriculas/matriculas-listado/*` ya existe con el botón "+ Nueva
  matrícula" y su modal completos y funcionando (HU-019) — amplíalo, no lo reescribas desde cero.

## Dependencias

- Ninguna nueva: requiere que existan matrículas (HU-017 o HU-019, ya existen y siguen funcionando).

## 🔑 Punto de oro — con quién coordinar

Puedes arrancar ya, sin esperar a nadie. Solo dos avisos puntuales, no bloqueos, con **Ariana y
Gabriel** (HU-041, reporte de matrículas — comparten `MatriculaServicio`, `MatriculaControlador` y
`matricula-api.service.ts` con ustedes):

1. **`normalizar(String)` / `normalizarEstado(String)`** — ninguno de los dos existe hoy en
   `MatriculaServicio`; los necesitan tanto tú (`listarAdministrativas`) como HU-041
   (`reportePaginado`/`reporte`). **El que los cree primero avisa en el grupo** — el segundo los
   reutiliza, no los vuelve a crear (si los duplica, no compila).
2. **Bloques separados en los archivos compartidos** — agrega tus 3 métodos/endpoints en un bloque
   propio y comentado (`// HU-020 — ...`), no intercalado con los de HU-041, para que el merge de Git
   sea automático.

No hay nada que coordinar con HU-008 (usuarios) ni con HU-027 (sesiones) — son archivos completamente
aparte.

## Contratos que deben acordarse

| Operación | Método y ruta | Resultado conceptual |
|---|---|---|
| Listar matrículas (admin) | `GET /api/admin/matriculas?texto=&estado=&page=&size=` | Página de matrículas con filtros |
| Detalle de una matrícula | `GET /api/admin/matriculas/{matriculaId}` | Acceso, historial de pagos e historial de estados, por separado |
| Cancelar una matrícula | `PATCH /api/admin/matriculas/{matriculaId}/cancelacion` | Exige motivo; solo si está ACTIVA |

## Trabajo del backend

### Archivos y responsabilidades

- `service/MatriculaServicio.java` (ya existe, se amplía): agregar los métodos `listarAdministrativas`,
  `detalleAdministrativo` y `cancelar`, más el helper privado `nombreUsuario(Long)`.
- `controller/MatriculaControlador.java` (ya existe, se amplía): agregar los tres endpoints.

### Reglas en orden

1. `listarAdministrativas`: llama primero a `procesarVencimientos()` (idempotente — una matrícula ya
   vencida no se vuelve a tocar), exige rol ADMINISTRADOR, normaliza texto/estado y delega en
   `matriculas.buscarAdministrativas(...)`.
2. `detalleAdministrativo`: exige administrador, busca con `findWithDetalleById` (404 si no existe),
   arma por separado la lista de pagos (`PagoMatriculaDetalleRespuesta`, con el nombre de quien registró
   cada uno) y el historial de cambios de estado (`HistorialEstadoMatriculaRespuesta`) — **nunca se
   mezclan en una sola lista**, son conceptos distintos (dinero vs. acceso).
3. `cancelar`: exige administrador, solo permite cancelar una matrícula en estado `ACTIVA` (si no,
   `BusinessValidationException`), guarda motivo y quién canceló, y registra el cambio de estado en el
   historial vía `registrarCambio(...)` — nunca ejecuta una devolución de dinero ni edita el pago ya
   registrado.
4. `nombreUsuario(Long usuarioId)`: helper privado que resuelve el nombre completo de quien registró un
   pago o un cambio de estado; si el id es `null` devuelve `"Autoservicio / sistema"` (una matrícula
   gratuita o un vencimiento automático no tienen un administrador detrás).

## Trabajo del frontend

### Pantalla (`features/admin/matriculas/matriculas-listado`)

- Maquetar según `maquetacion-html/HU-020-PF-MATRICULAS-listado.html`.
- Filtros: texto libre + select de estado (Todos/Activa/Cancelada), con botones "Buscar" y "Limpiar".
- Tabla paginada: Alumno (+correo), Curso, Ingreso (badge), Estado (badge), Registrada, y columna de
  Acciones con "Ver detalle" y "Cancelar" (este último deshabilitado si la matrícula no está ACTIVA).
- Modal "Detalle de matrícula": encabezado con avatar + nombre + badge de estado, resumen en `<dl>`
  (curso, modalidad, forma de ingreso, responsable, fechas), sección "Historial económico" (pagos) y
  sección "Historial de acceso" (cambios de estado) **separadas**, más un botón "Reenviar confirmación"
  (reutiliza `MatriculaApiService.reenviarConfirmacion`, que ya existe).
- Modal "Cancelar matrícula": pide motivo obligatorio, botón de confirmación en rojo.
- El botón "+ Nueva matrícula" y su modal (HU-019) ya están en el mismo archivo — no los dupliques ni los
  muevas, solo agrega lo de esta historia alrededor.

### Servicio Angular

Ampliar `features/matriculas/matricula-api.service.ts` (ya existe) con:

```ts
listarAdministrativas(texto = '', estado = '', page = 0, size = 20): Observable<PageResponse<MatriculaAdministrativa>> {
  const params = new HttpParams().set('texto', texto).set('estado', estado).set('page', page).set('size', size);
  return this.http.get<PageResponse<MatriculaAdministrativa>>(`${API_URL}/admin/matriculas`, { params });
}

cancelar(id: number, motivo: string): Observable<Matricula> {
  return this.http.patch<Matricula>(`${API_URL}/admin/matriculas/${id}/cancelacion`, { motivo });
}

detalleAdministrativo(id: number): Observable<MatriculaDetalleAdministrativa> {
  return this.http.get<MatriculaDetalleAdministrativa>(`${API_URL}/admin/matriculas/${id}`);
}
```

Los modelos `MatriculaAdministrativa` y `MatriculaDetalleAdministrativa` ya existen en
`features/matriculas/matricula.model.ts`.

## Pruebas mínimas

- listar delega los filtros (texto normalizado, estado normalizado) al repositorio tal cual;
- cancelar una matrícula activa la deja en `CANCELADA` con motivo e historial;
- cancelar una matrícula ya cancelada lanza validación (solo se cancela desde `ACTIVA`);
- cancelar una matrícula inexistente lanza `ResourceNotFoundException`;
- el detalle separa correctamente pagos de historial de estados.

## Terminado cuando

Un administrador filtra matrículas por texto y estado, abre el detalle de una y ve su historial
económico y de acceso por separado, y puede cancelarla con motivo — una matrícula no `ACTIVA` no se
puede volver a cancelar. Las historias HU-017, HU-019, HU-021 y HU-041 siguen funcionando exactamente
igual que antes.

## Implementación guiada para copiar y adaptar

### 1. Backend — métodos a agregar en `MatriculaServicio` (ya existe la clase)

```java
@Transactional
public Page<MatriculaAdministrativaRespuesta> listarAdministrativas(String texto, String estado, Pageable pageable) {
    procesarVencimientos();
    exigirAdministrador();
    return matriculas.buscarAdministrativas(normalizar(texto), normalizarEstado(estado), pageable);
}

@Transactional
public MatriculaDetalleAdministrativaRespuesta detalleAdministrativo(Long matriculaId) {
    procesarVencimientos();
    exigirAdministrador();
    Matricula m = matriculas.findWithDetalleById(matriculaId)
            .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
    List<PagoMatriculaDetalleRespuesta> pagosRespuesta = pagos.findByMatricula_IdOrderByResultadoEnDesc(matriculaId)
            .stream().map(p -> new PagoMatriculaDetalleRespuesta(p.getId(), p.getOrigen(), p.getEstado(),
                    p.getImporte(), p.getMoneda(), p.getMedio(), p.getReferenciaExterna(), p.getMotivo(),
                    p.getRegistradoPorUsuarioId(), nombreUsuario(p.getRegistradoPorUsuarioId()), p.getResultadoEn())).toList();
    List<HistorialEstadoMatriculaRespuesta> estados = historial.findByMatricula_IdOrderByRealizadoEnDesc(matriculaId)
            .stream().map(h -> new HistorialEstadoMatriculaRespuesta(h.getEstadoAnterior(), h.getEstadoNuevo(),
                    h.getMotivo(), h.getRealizadoPorUsuarioId(), nombreUsuario(h.getRealizadoPorUsuarioId()), h.getRealizadoEn())).toList();
    return new MatriculaDetalleAdministrativaRespuesta(m.getId(), m.getUsuario().getId(),
            m.getUsuario().getPersona().nombreCompleto(), m.getUsuario().getCorreo(), m.getCurso().getId(),
            m.getCurso().getTitulo(), m.getCurso().getModalidad(), m.getEstado(), m.getFormaIngreso(),
            m.getFechaMatricula(), m.getFechaActivacion(), m.getFechaVencimiento(), m.getFechaFinalizacion(),
            m.getMotivoCancelacion(), m.getCreadoPorUsuarioId(), nombreUsuario(m.getCreadoPorUsuarioId()), pagosRespuesta, estados);
}

@Transactional
public MatriculaRespuesta cancelar(Long matriculaId, CancelarMatriculaPeticion p) {
    procesarVencimientos();
    exigirAdministrador();
    Matricula m = matriculas.findWithDetalleById(matriculaId)
            .orElseThrow(() -> new ResourceNotFoundException("La matrícula no existe."));
    if (!"ACTIVA".equals(m.getEstado())) {
        throw new BusinessValidationException("Solo puedes cancelar una matrícula activa.");
    }
    String anterior = m.getEstado();
    m.setEstado("CANCELADA");
    m.setMotivoCancelacion(p.motivo().strip());
    m.setCanceladaEn(clock.instant());
    m.setCanceladaPorUsuarioId(actual.get().userId());
    registrarCambio(m, anterior, "CANCELADA", p.motivo().strip(), actual.get().userId());
    return respuesta(m, null);
}

private String nombreUsuario(Long usuarioId) {
    if (usuarioId == null) return "Autoservicio / sistema";
    return usuarios.findWithPersonaById(usuarioId).map(u -> u.getPersona().nombreCompleto()).orElse("Usuario " + usuarioId);
}
```

### 2. Backend — endpoints a agregar en `MatriculaControlador` (ya existe la clase)

```java
@GetMapping("/admin/matriculas/{matriculaId}")
public ResponseEntity<MatriculaDetalleAdministrativaRespuesta> detalle(@PathVariable Long matriculaId) {
    return ResponseEntity.ok(servicio.detalleAdministrativo(matriculaId));
}

@GetMapping("/admin/matriculas")
public ResponseEntity<PageResponse<MatriculaAdministrativaRespuesta>> listarAdministrativas(
        @RequestParam(defaultValue = "") String texto,
        @RequestParam(defaultValue = "") String estado,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    int tamano = Math.min(Math.max(size, 1), 50);
    Page<MatriculaAdministrativaRespuesta> pagina = servicio.listarAdministrativas(
            texto, estado, PageRequest.of(Math.max(page, 0), tamano));
    return ResponseEntity.ok(PageResponse.from(pagina));
}

@PatchMapping("/admin/matriculas/{matriculaId}/cancelacion")
public ResponseEntity<MatriculaRespuesta> cancelar(
        @PathVariable Long matriculaId, @Valid @RequestBody CancelarMatriculaPeticion peticion) {
    return ResponseEntity.ok(servicio.cancelar(matriculaId, peticion));
}
```

### 3. Frontend — estado y métodos a agregar en `matriculas-listado.ts` (ya existe la clase, ya tiene lo de HU-019)

```ts
protected readonly filas = signal<MatriculaAdministrativa[]>([]);
protected readonly cargando = signal(true);
protected readonly error = signal<string | null>(null);
protected texto = '';
protected estado = '';
protected readonly pagina = signal(0);
protected readonly totalPaginas = signal(0);
protected readonly filaCancelando = signal<MatriculaAdministrativa | null>(null);
protected readonly motivoCancelacion = signal('');
protected readonly cancelando = signal(false);
protected readonly detalle = signal<MatriculaDetalleAdministrativa | null>(null);
protected readonly cargandoDetalle = signal(false);
protected readonly reenviando = signal(false);

protected buscar(): void { this.pagina.set(0); this.cargar(); }
protected limpiarFiltros(): void { this.texto = ''; this.estado = ''; this.buscar(); }
protected irAPagina(pagina: number): void {
  if (pagina < 0 || pagina >= this.totalPaginas()) return;
  this.pagina.set(pagina);
  this.cargar();
}
protected cargar(): void {
  this.cargando.set(true);
  this.error.set(null);
  this.api.listarAdministrativas(this.texto, this.estado, this.pagina(), 20)
    .pipe(takeUntilDestroyed(this.destroyRef))
    .subscribe({
      next: (r) => { this.filas.set(r.items); this.totalPaginas.set(r.totalPages); this.cargando.set(false); },
      error: () => { this.error.set('No pudimos cargar las matrículas. Inténtalo nuevamente.'); this.cargando.set(false); },
    });
}
protected etiquetaIngreso(formaIngreso: string): string {
  switch (formaIngreso) {
    case 'GRATUITA': return 'Gratuita';
    case 'ADMINISTRADOR': return 'Asignación manual';
    case 'PAGO_EN_LINEA': return 'Pago en línea';
    case 'EXONERADA': return 'Exonerada';
    default: return formaIngreso;
  }
}
protected etiquetaEstado(estado: string): string {
  switch (estado) {
    case 'ACTIVA': return 'Activa';
    case 'CANCELADA': return 'Cancelada';
    case 'VENCIDA': return 'Vencida';
    case 'FINALIZADA': return 'Finalizada';
    case 'PENDIENTE_PAGO': return 'Pendiente de pago';
    default: return estado;
  }
}
protected readonly iniciales = obtenerIniciales;
protected claseIngreso(formaIngreso: string): string {
  switch (formaIngreso) {
    case 'GRATUITA': return 'badge--ingreso-gratuito';
    case 'ADMINISTRADOR': return 'badge--ingreso-manual';
    case 'PAGO_EN_LINEA': return 'badge--ingreso-pago';
    case 'EXONERADA': return 'badge--ingreso-manual';
    default: return 'badge--disp-cerrado';
  }
}
protected abrirCancelar(fila: MatriculaAdministrativa): void {
  this.motivoCancelacion.set('');
  this.filaCancelando.set(fila);
}
protected cerrarCancelar(): void { this.filaCancelando.set(null); }
protected confirmarCancelar(): void {
  const fila = this.filaCancelando();
  const motivo = this.motivoCancelacion().trim();
  if (!fila || !motivo) { this.alertas.mostrar('error', 'Indica el motivo de la cancelación.'); return; }
  this.cancelando.set(true);
  this.api.cancelar(fila.id, motivo).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
    next: () => { this.cancelando.set(false); this.filaCancelando.set(null); this.alertas.mostrar('exito', 'Matricula cancelada.'); this.cargar(); },
    error: (e) => { this.cancelando.set(false); this.alertas.mostrar('error', e.error?.message ?? 'No pudimos cancelar la matricula.'); },
  });
}
protected abrirDetalle(fila: MatriculaAdministrativa): void {
  this.detalle.set(null);
  this.cargandoDetalle.set(true);
  this.api.detalleAdministrativo(fila.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
    next: (detalle) => { this.detalle.set(detalle); this.cargandoDetalle.set(false); },
    error: () => { this.cargandoDetalle.set(false); this.alertas.mostrar('error', 'No pudimos cargar el detalle de la matrícula.'); },
  });
}
protected reenviarConfirmacion(): void {
  const id = this.detalle()?.id;
  if (!id) return;
  this.reenviando.set(true);
  this.api.reenviarConfirmacion(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
    next: (resultado) => { this.reenviando.set(false); this.alertas.mostrar(resultado.enviado ? 'exito' : 'error', resultado.mensaje); },
    error: () => { this.reenviando.set(false); this.alertas.mostrar('error', 'No pudimos reenviar la confirmación.'); },
  });
}
protected cerrarDetalle(): void { this.detalle.set(null); }
```

No olvides llamar `this.cargar()` en el constructor (donde hoy solo se inicializa lo de HU-019).

### 4. Comprobación incremental

1. `mvn test` — confirmar que los tests de HU-017/019/021 (que no cambiaron) siguen pasando, y agregar los
   propios de esta historia.
2. En Swagger: listar con filtros, ver detalle, cancelar una matrícula activa y confirmar que una
   segunda cancelación de la misma matrícula falla.
3. Conectar Angular: confirmar que "+ Nueva matrícula" (HU-019) sigue funcionando exactamente igual que
   antes de tocar esta pantalla.
