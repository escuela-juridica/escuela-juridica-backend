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
- Quince cursos de distintas modalidades y condiciones comerciales.
- Matrículas gratuita, manual, exonerada y pagada con Culqi.
- Progreso, asistencia, intento automático/abierto, certificado y reclamación de ejemplo.
- Excepción académica, corrección de certificado y pago aprobado tras una cancelación para probar
  los recorridos excepcionales.

Los correos, teléfonos, operaciones de pago, enlaces y contenidos son ficticios.
