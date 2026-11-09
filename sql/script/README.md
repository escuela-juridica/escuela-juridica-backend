# Base de datos definitiva de ESEJUR

Esta carpeta contiene la definición acumulativa de la base PostgreSQL para las seis épicas. Es la
fuente vigente para nuevas instalaciones y reemplaza los scripts incrementales anteriores.

## Archivo

`esejur-base-de-datos-completa.sql` contiene los tres bloques necesarios:

1. elimina las 47 tablas en orden inverso de dependencias;
2. crea tablas, columnas, llaves, restricciones y comentarios;
3. registra catálogos y datos demostrativos coherentes para probar los recorridos principales.

Los tres bloques se ejecutan dentro de una sola transacción.

## Orden de ejecución

Para instalar o reconstruir completamente una base de desarrollo se ejecuta únicamente:

```text
esejur-base-de-datos-completa.sql
```

El archivo siempre comienza con la limpieza total. No debe ejecutarse sobre una base cuyos datos se
necesiten conservar.

## Cobertura

- EP01: identidad, acceso, roles, verificación, recuperación, catálogo y ficha.
- EP02: datos maestros, cursos, contenido, sesiones, exámenes, reglas y publicación.
- EP03: matrícula gratuita/administrativa, pagos manuales o exonerados y accesos.
- EP04: aula, avance de videos/lecciones, sesiones, cambios y asistencia.
- EP05: intentos, respuestas, revisión, excepciones académicas y certificados.
- EP06: Culqi, idempotencia, atención de incidencias, reclamaciones y control final.

Los reportes y el dashboard consultan las tablas operativas; no necesitan tablas duplicadas de
resumen. La automatización se implementa en los servicios de la aplicación. Por decisión del
proyecto, estos scripts no crean triggers, funciones, procedimientos ni vistas.

## Datos demostrativos

- Ricardo Enrique Prada Guerra: administrador con acceso.
- Gabriel Mayanga, Joel Saldaña y Juan Morales: alumnos con acceso.
- Miguel Saldivar y Ariana Lazaro: docentes públicos sin cuenta.
- Contraseña de demostración para las cuentas: `Marco1415@`.
- Historial no vigente de verificación y recuperación, sin códigos ni tokens activos.
- Alumno de demostración del flujo completo: `alumno.demo@escuelajuridica.edu.pe` (contraseña `Marco1415@`), sin matrícula, y el curso 
  «Procedimiento Administrativo Práctico» (gratuito, virtual) con 4 módulos, 8 lecciones y un examen final automático de 4 preguntas, para mostrar 
  la matrícula gratuita, estudiar y generar el certificado al instante (0 días de espera). La respuesta correcta de cada pregunta es siempre la primera opción, y el examen dura 30 minutos y se rinde en modo una por una (sin volver atrás y sin dejar preguntas en blanco). Cada lección trae video, PDF descargables y lecturas: los PDF están en `uploads/cursos/31/` del backend y esa carpeta debe copiarse junto con el script.
- Treinta cursos de distintas modalidades, tipos y categorias, con descripcion, imagen de portada, beneficios, docentes, dos o tres modulos con sus lecciones y un examen por curso. Las fechas de inicio, cierre de matricula y sesiones en vivo se calculan a partir del dia de ejecucion del script.
- Matrículas gratuita, manual, exonerada y pagada con Culqi.
- Las portadas de esos cursos son archivos de la carpeta `uploads/portadas/` del backend (rutas `/uploads/portadas/<id del curso>-<marca de tiempo>.jpg`, el mismo formato que usa el panel al subir una portada); en un servidor nuevo hay que copiar esa carpeta junto con el script.
- Progreso, asistencia, intento automático/abierto, certificado y reclamación de ejemplo.
- Excepción académica, corrección de certificado y pago aprobado tras una cancelación para probar
  los recorridos excepcionales.

Los correos, teléfonos, operaciones de pago, enlaces y contenidos son ficticios.
