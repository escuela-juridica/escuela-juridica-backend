# Evidencias del Avance 3 — Backend (pruebas de las épicas 4, 5 y 6)

Este directorio documenta las pruebas automatizadas que se agregaron al backend para demostrar que los flujos principales
de las **épicas 4 (aula, progreso y sesiones)**, **5 (evaluación y certificación)** y **6 (pagos, reclamos y reportes)**
fueron verificados. No se modificó código de producción: todas las pruebas usan los contratos actuales de los servicios,
controladores y DTOs.

## Resultado de la ejecución

Comando: `./mvnw.cmd test` (JDK 21, perfil `test`, H2 en modo PostgreSQL; sin Culqi, SMTP ni PostgreSQL reales).

| | Antes del Avance 3 | Ahora |
|---|---|---|
| Clases de prueba | 23 | **37** (+14) |
| Pruebas | 212 | **370** (+158) |
| Aprobadas | 212 | **370** |
| Fallidas | 0 | **0** |
| Errores no controlados | 0 | **0** |
| Omitidas | 0 | **0** |
| Tiempo | — | 59 s en total (53 s sumando el tiempo de las pruebas) |

Las 212 pruebas anteriores siguen presentes y aprobadas; no se eliminó ni se debilitó ninguna.

## Contenido de esta carpeta

| Archivo | Para qué sirve |
|---|---|
| [`matriz-cobertura.md`](matriz-cobertura.md) / [`matriz-cobertura.csv`](matriz-cobertura.csv) | Épica, historia/flujo, archivo de prueba, tipo de prueba y resultado de cada archivo nuevo. |
| [`detalle-pruebas.md`](detalle-pruebas.md) | Cada prueba nueva por nombre, con su estado de la última ejecución. |
| [`consola/mvn-test.log`](consola/mvn-test.log) | Salida de consola de `mvn test` (última parte, con el resumen `Tests run: 370, Failures: 0, Errors: 0`). |
| [`resultados/resumen-surefire.txt`](resultados/resumen-surefire.txt) | Pruebas, fallas, errores y tiempo por clase. |
| [`resultados/surefire/`](resultados/surefire) | Reportes XML de Surefire (el formato que ya usa el proyecto), uno por clase. |

## Qué demuestra cada épica

**EP04 — Aula, progreso y sesiones (78 pruebas nuevas)**

- *Autorización para acceder al aula* (`AulaServiceTests`, `AulaHttpTests`): un administrador recibe 403, un alumno sin
  matrícula 404, una matrícula pendiente de pago, cancelada, vencida o con cuenta sin verificar es rechazada con su motivo,
  y un alumno con matrícula activa recibe su temario con lecciones `COMPLETADA`/`DISPONIBLE`/`BLOQUEADA` y el punto donde continuar.
- *Bloqueo de materiales sin matrícula* (`MaterialAlumnoServiceTests`): sin matrícula o con matrícula inactiva no se lista,
  se ve ni se descarga nada; el material subido nunca expone su ruta física.
- *Registro de progreso* (`ProgresoServiceTests`, `AulaHttpTests`): umbral de video, check manual, completado por asistencia,
  un check completado no se desmarca, el porcentaje de video no retrocede y los intentos de saltarse una lección bloqueada se rechazan.
  La prueba HTTP persiste el progreso en H2.
- *Reglas de ruta académica* (`RutaAcademicaServiceTests`): secuencia obligatoria, sesiones canceladas que liberan la ruta,
  exámenes bloqueantes, habilitación de exámenes de módulo y final, intentos agotados.
- *Sesiones y asistencia* (`SesionAlumnoServiceTests`, `AdministrarSesionesServiceTests`): ingreso solo dentro de la ventana,
  calendario, ausencias automáticas al terminar la sesión, porcentaje de asistencia, reprogramación y cancelación con historial,
  corrección de asistencia con valor anterior conservado.

**EP05 — Evaluación y certificación (31 pruebas nuevas)**

- *Evaluación automática* (`ExamenHttpTests`): nota sobre 20 con dos decimales, aprobado y no aprobado, intentos agotados,
  alternativas ajenas rechazadas, intento ajeno invisible, cierre por tiempo agotado.
- *Respuestas abiertas y revisión* (`ExamenHttpTests`): el intento queda `PENDIENTE_REVISION`, no admite otro intento,
  el administrador califica (puntaje máximo validado, una sola vez) y el alumno no accede a la bandeja.
- *Condiciones de certificación* (`AvanceAcademicoServiceTests`): progreso, exámenes, nota promedio y asistencia (solo en cursos
  no virtuales), exámenes en revisión que bloquean el certificado y la frase de lo que falta.
- *Emisión, consulta y verificación pública* (`CertificacionHttpTests`): estados `PENDIENTE_REQUISITOS` → `PENDIENTE_DATOS` →
  `LISTO_PARA_EMITIR` → `VIGENTE`, confirmación de datos validada, PDF generado, mis certificados, descarga solo del dueño,
  verificación pública sin datos sensibles, certificado anulado y código inexistente.

**EP06 — Pagos, reclamos y reportes (49 pruebas nuevas)**

- *Creación y actualización de pagos* (`PagoHttpTests`): inicio con importe confirmado, `PRICE_CHANGED`, curso gratuito o
  cancelado, reanudación del intento pendiente, token cobrado una sola vez.
- *Estados aprobado, pendiente, rechazado y error* (`PagoHttpTests`): solo el aprobado activa la matrícula y genera constancia y
  correo; rechazado y error permiten reintentar con un intento nuevo; consulta de órdenes diferidas y webhook con autenticación
  y sin duplicar efectos.
- *Excepciones de matrícula/pago y cancelación de curso* (`CancelacionYExcepcionesPagoHttpTests`): cancelación con avisos,
  sesiones futuras canceladas y lectura conservada; pago aprobado después de cancelar el curso queda para atención; la
  excepción se atiende una sola vez.
- *Reclamos* (`ReclamacionHttpTests`): presentación con y sin sesión, validaciones (DNI, declaración jurada), fallo de correo sin
  perder el caso, bandeja y respuesta del administrador.
- *Reportes y dashboard* (`DashboardYReportePagosHttpTests`): acceso solo administrador, el total suma solo pagos confirmados,
  filtros validados, exportación a Excel y series del dashboard.

## Cómo están hechas las pruebas

- **Unitarias (Mockito):** servicios con repositorios simulados, en el mismo estilo de `MatriculaServiceTests`. Datos comunes en
  `AcademiaFixtures`.
- **Integración HTTP:** `HttpIntegracionBase` levanta la aplicación (`@SpringBootTest`, perfil `test`, H2) y pasa por la cadena real de
  Spring Security y el `GlobalExceptionHandler`. La sesión se simula con el `jwt()` de `spring-security-test` (rol `ALUMNO` o
  `ADMINISTRADOR`). Cada prueba corre en una transacción que se revierte, así que no deja datos en la base compartida.
- **Lo externo se reemplaza:** `MailService` y `CulqiIntegration` son `@MockitoBean`; ninguna prueba abre red.

## Alcance y pendientes reales

- La cadena de seguridad se ejercita con una sesión ya autenticada (`jwt()`): estas clases comprueban 401 sin sesión, 403 por rol y
  las reglas de negocio, pero no la lectura de la cookie de sesión ni el token CSRF (con `jwt()` las peticiones de escritura
  pasan sin token CSRF). La relación entre el JWT y la cookie la cubre `SecurityComponentsTests` (existente).
- Culqi, SMTP y PostgreSQL reales no se usan: la integración real con Culqi (tarjeta, Yape, billeteras) solo se valida contra el
  doble. El flujo de billeteras y su QR no se prueba contra el sandbox.
- No hay pruebas nuevas para: seguimiento académico y otorgamiento de intentos adicionales (`SeguimientoAcademicoService`),
  emisión manual, anulación y corrección de certificados (`CertificadoAdminService`), reportes académico, de asistencia, de
  certificados y de matrículas (exportaciones PDF/CSV), ni el reenvío de avisos de calificación. Esas historias existen en el
  código pero quedan fuera de este avance.
- De los tipos de excepción de pago (`PAGO_TARDIO`, `CURSO_CANCELADO`, `MATRICULA_CANCELADA`, `SOBRECUPO`), las pruebas agregadas
  ejercitan el caso `CURSO_CANCELADO`; los otros tres no tienen una prueba propia en este avance.
