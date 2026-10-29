# Épica 4 — Aula, progreso y sesiones

Guías técnicas (backend + frontend) de las 4 historias en construcción ahora mismo. Cada una tiene
versión `.md` y `.docx` con el mismo contenido:

| Historia | Equipo | Rama |
|---|---|---|
| `HU-008-MAPA-TECNICO-GESTIONAR-USUARIOS` — Gestionar usuarios administrativamente | Miguel | `feature/HU-008-usuarios` |
| `HU-020-MAPA-TECNICO-CONTROL-MATRICULAS` — Controlar matrículas y pagos | Joel y Juan | `feature/HU-020-matriculas` |
| `HU-027-MAPA-TECNICO-CAMBIOS-SESIONES` — Administrar cambios en sesiones en vivo | Enrique | — |
| `HU-041-MAPA-TECNICO-REPORTE-MATRICULAS` — Consultar el reporte de matrículas | Ariana y Gabriel | `feature/HU-041-reporte-matriculas` |

> HU-008, HU-020 y HU-041 ya estaban implementadas y funcionando — se retiraron deliberadamente
> (código y tests) para convertirlas en ejercicio de programación del equipo. Cada guía trae el código
> real que existía, para reconstruirlo igual o mejor. HU-022, HU-023 y HU-026 (aula del alumno) ya no
> forman parte de este lote.

## La maquetación visual (HTML) está en otro lado, a propósito

Los 4 diseños listos para copiar y pegar **no están en esta carpeta** — viven en el proyecto
frontend, junto con el CSS y las imágenes compartidas que necesitan para verse bien:

```
escuela-juridica-frontend/maquetacion-html/
├── 00-INDICE.html                        ← abrir primero, tiene la guía completa
├── HU-008-PF-USUARIOS-listado.html
├── HU-020-PF-MATRICULAS-listado.html
├── HU-027-PF-ADMIN-sesiones.html
├── HU-041-PF-REPORTE-matriculas.html
├── estilos.css                           ← compartido por toda la app, no solo EP04
└── img/
```

Se abren con doble clic en el navegador, sin necesidad de tener Angular corriendo.

## Orden sugerido para leer

1. `maquetacion-html/00-INDICE.html` — contexto visual rápido de las 4 pantallas.
2. El `.md` o `.docx` de tu historia, acá en esta carpeta — ahí está el "cómo programar" real
   (entidades, DTOs, servicios, controlador, servicio Angular), con código listo para copiar y
   adaptar.
