# ESEJUR — Backend

API REST de "Escuela Jurídica" (ESEJUR), plataforma de cursos en línea con matrícula, contenido, exámenes y certificación. Proyecto académico del Curso Integrador II — Software (UTP).

## Stack técnico

| Componente | Versión / detalle |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Build | Maven (con wrapper `mvnw` / `mvnw.cmd`) |
| Persistencia | Spring Data JPA / Hibernate |
| Base de datos | PostgreSQL 15+ (H2 en memoria solo para tests) |
| Seguridad | Spring Security + OAuth2 Resource Server (JWT propio, HS256) |
| Validación | Jakarta Bean Validation |
| Correo | Spring Mail (SMTP) |
| Export | Apache POI (Excel), Apache PDFBox (PDF) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Security Test |

No se usa Flyway ni Liquibase: el esquema se gestiona con un script SQL manual, y no hay librería de JWT de terceros — se usa el soporte nativo de Spring Security OAuth2 Resource Server (`NimbusJwtEncoder`/`NimbusJwtDecoder`).

## Estructura de paquetes

```
src/main/java/pe/edu/utp/escuela/app/
├── controller/   16 controladores REST, uno por área funcional
├── service/      25 servicios con la lógica de negocio y las reglas de autorización
├── repository/   29 interfaces Spring Data JPA
├── entity/       35 entidades JPA (dominio completo)
├── dto/          ~115 records de petición/respuesta, separados de las entidades
├── config/       SecurityConfig, CorsConfig, WebConfig, PasswordConfig, ClockConfig
├── security/     JwtService, SessionCookieService, CurrentUserService, hash/token
├── export/       ReporteMatriculaExportador (CSV, Excel, PDF)
├── mail/         MailService, plantillas HTML
├── exception/    Jerarquía de BusinessException + GlobalExceptionHandler
└── util/         TextNormalizer y utilidades varias
```

## Modelo de dominio

**Identidad y acceso** — `Persona` (datos personales, compartida entre `Usuario` y `Firmante`), `Usuario` (cuenta de acceso), `Rol`/`UsuarioRol` (catálogo de roles con rol principal), `CodigoVerificacionCorreo`, `TokenRecuperacionAcceso`.

**Catálogos** — `TipoCurso`, `CategoriaTematica`, `EstadoCurso`, `TipoMaterial`, `EntidadCertificadora`, `Firmante`, `ConfiguracionInstitucional`.

**Cursos y contenido** — `Curso` (entidad central: precios, modalidad, fechas, cupo, estado), `CursoDocente`/`CursoFirmante` (asignaciones), `Modulo`, `Leccion` (video/documento/sesión en vivo), `Recurso` (archivo o enlace), `MaterialLeccion`, `ReglaCurso` (reglas académicas y de certificación), `ReglaArchivo`, `HistorialEstadoCurso`.

**Exámenes** — `Examen`, `Pregunta`, `OpcionPregunta`.

**Matrícula, pagos y progreso** — `Matricula`, `HistorialEstadoMatricula`, `Pago` (registro manual; integración con pasarela de pago planificada para una historia futura), `ProgresoLeccion`, `LogroCertificacion`, `Certificado`.

**Notificaciones** — `Notificacion` (registro de cada correo enviado y su resultado).

Casi todas extienden `RegistroAuditable` (`@MappedSuperclass`), que añade `creadoEn`/`modificadoEn` automáticos.

## Seguridad y autenticación

- JWT propio firmado con **HMAC-SHA256**, nunca expuesto en el cuerpo de la respuesta: viaja solo en una cookie **HttpOnly** (`ESEJUR_SESION`), con `secure`/`sameSite` configurables por entorno.
- Duración de sesión: 60 minutos (`security.jwt.expiration-minutes`), igual para el JWT y para el `maxAge` de la cookie.
- Un `BearerTokenResolver` personalizado extrae el token de la cookie en vez del header `Authorization`, e ignora rutas públicas (login, registro, verificación, recuperación, health, `/uploads/**`, swagger).
- Existe una **segunda clave JWT independiente** para las referencias de verificación de correo, deliberadamente distinta de la de sesión, para que un token de registro nunca pueda decodificarse como una cookie de sesión válida.
- **Spring Security solo distingue público vs. autenticado** a nivel de ruta (`permitAll()` / `anyRequest().authenticated()`). La autorización fina por rol (`ALUMNO` / `ADMINISTRADOR`) se implementa **dentro de cada servicio**, vía `CurrentUserService.get().hasRole("ADMINISTRADOR")`, lanzando `ForbiddenException` si no corresponde — no se usan matchers de Spring Security por rol.
- Login con correo: el mismo error se usa para correo inexistente o contraseña incorrecta, para no revelar si una cuenta existe. Verifica cuenta activa y correo verificado antes de emitir sesión.
- Login/registro con Google: no hay integración OAuth2 completa con un proveedor externo todavía; existe el flujo para completar el registro a partir de un "contexto Google" ya validado externamente (correo/`googleSubject` nunca se toman del cuerpo enviado por el navegador).
- Recuperación de contraseña: la solicitud siempre responde con un mensaje neutral exista o no la cuenta; los tokens se guardan hasheados, nunca en texto plano.

## Patrón de capas

Controller (fino, valida y delega) → Service (lógica de negocio, autorización, `@Transactional`) → Repository (Spring Data JPA). Las entidades JPA nunca se exponen directamente; los DTOs (`dto/`) son la única interfaz pública.

Manejo de errores centralizado en `GlobalExceptionHandler` (`@RestControllerAdvice`): cada subclase de `BusinessException` (`ForbiddenException`, `ResourceNotFoundException`, `InvalidCredentialsException`, `DuplicateResourceException`, etc.) expone su propio `HttpStatus` y código; errores de validación, de body ilegible y de integridad de datos se traducen a respuestas JSON consistentes (`ApiErrorResponse`), sin filtrar detalles internos al cliente.

## Funcionalidades principales

- **Autenticación y cuenta** — login, registro (con aceptación legal y desafío anti-robot propio), completar registro con Google, verificación de correo por código, recuperación de contraseña.
- **Perfil** — consultar/actualizar datos personales, cambiar contraseña con política configurable.
- **Catálogo público** — listar cursos publicados con filtros, ficha por URL amigable, vista previa de lección gratuita, sin necesidad de sesión.
- **Gestión de cursos** (admin) — alta/edición, docentes y firmantes, reglas académicas, validar y publicar, adelantar/retrasar inicio, cerrar, destacar, duplicar, eliminar.
- **Contenido del curso** (admin) — CRUD de módulos y lecciones con reordenación, sesiones en vivo, subida de materiales (archivo o enlace), activar/desactivar.
- **Exámenes y calificaciones** (admin) — CRUD de exámenes, preguntas y alternativas, reordenación.
- **Matrícula, pagos y reportes** — matrícula gratuita y administrativa, listado/detalle, advertencias académicas, cancelación, reportes exportables en CSV/Excel/PDF.
- **Administración de usuarios** (admin) — búsqueda, alta, edición, roles, activar/desactivar, resetear contraseña.
- **Información base** (admin) — catálogos maestros: tipos de curso, categorías, docentes, entidades certificadoras, firmantes, tipos de material, reglas de archivo, configuración institucional.

**Aula virtual / sesiones en vivo / materiales protegidos**: documentado como mapa técnico en `docs/epica-4/`, en construcción. Hoy `/uploads/**` es público como cualquier recurso estático — proteger el acceso real a materiales de cursos de pago es responsabilidad de esas historias, aún no implementadas.

## Base de datos

PostgreSQL 15+. `spring.jpa.hibernate.ddl-auto=validate` — Hibernate valida el esquema contra las entidades pero no lo crea ni migra; el esquema real vive en `sql/script/esejur-base-de-datos-completa.sql` (47 tablas), un script único y **destructivo** (elimina y recrea todo) que además carga datos demostrativos. No hay triggers, funciones ni vistas: toda la lógica vive en los servicios Java. Los tests usan H2 en memoria con `ddl-auto=create-drop`.

## Testing

25 archivos de test (JUnit 5 + Mockito), concentrados en `service/` (19 archivos, uno por cada servicio de negocio relevante), más `security/`, `export/`, `mail/` y `controller/`. Predominan los tests unitarios de servicio (repositorios mockeados, `Clock` fijo para fechas deterministas), con algo de integración ligera contra H2.

```
./mvnw test
```

## Configuración

Variables relevantes de `application.properties` y perfiles (`local`, `dev`, `prod`, `test`):

- `server.port=8080`
- `application.frontend-base-url` — usado para construir enlaces de correo
- `esejur.almacenamiento.directorio=uploads` — carpeta de materiales subidos
- `cors.allowed-origin` — origen permitido (frontend Angular)
- `security.jwt.expiration-minutes=60`, `security.cookie.name=ESEJUR_SESION`
- `spring.mail.*` — configuración SMTP

El perfil `prod` es el único libre de secretos en el repo: usa exclusivamente variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`). Los perfiles `local`/`dev` traen credenciales de desarrollo en texto plano para facilitar el trabajo en equipo — no deben usarse fuera de ese contexto.

## Cómo ejecutar el proyecto

**Requisitos**: JDK 21, PostgreSQL corriendo y accesible. No hace falta Maven instalado (se usa el wrapper incluido).

1. Crear la base de datos y ejecutar `sql/script/esejur-base-de-datos-completa.sql` contra ella (perfil `local` espera `escuela_juridica` en `localhost:5432`).
2. Ejecutar la API:
   ```bash
   ./mvnw spring-boot:run
   ```
3. La API queda en `http://localhost:8080`, con Swagger UI en `http://localhost:8080/swagger-ui.html`. Se espera el frontend corriendo en `http://localhost:4200` (origen CORS configurado).

Para otro perfil: `--spring.profiles.active=dev` (o `prod`, con las variables `DB_URL`/`DB_USER`/`DB_PASSWORD` correspondientes).
