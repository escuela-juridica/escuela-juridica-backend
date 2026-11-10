# Archivos de demostración (semillas)

Reflejan la carpeta `uploads/` del backend y contienen todos los archivos que referencian los scripts
SQL de `sql/script/`. No forman parte del JAR ni de la imagen: se suben a mano.

| Carpeta | Contenido | Lo usa |
|---|---|---|
| `portadas/` | portadas de los cursos | ambos scripts |
| `firmas/` | firmas de los firmantes de los certificados | ambos scripts |
| `cursos/1`, `2`, `3` | PDF de los tres cursos de demostración | `esejur-base-de-datos-demo.sql` |
| `cursos/31` | PDF del curso de demostración | `esejur-base-de-datos-completa.sql` |

## Cómo usarlas

Copia el contenido de esta carpeta a la carpeta de archivos del backend, conservando las subcarpetas:

- Local: `uploads/`
- VPS: `/opt/esejurce/files/` (variable `FILES_DIR`), con dueño `deploy`.

Para no pisar archivos que ya se hayan subido desde el panel, en Linux copia sin sobrescribir:

```bash
cp -rn semillas/. /opt/esejurce/files/
```
