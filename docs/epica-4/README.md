# Épica 4 — Aula, progreso y sesiones

Guías técnicas (backend + frontend) de las 4 historias en construcción ahora mismo. Cada una tiene
versión `.md` y `.docx` con el mismo contenido:

- `HU-022-MAPA-TECNICO-INGRESAR-AULA` — Ingresar y continuar un curso
- `HU-023-MAPA-TECNICO-MATERIALES` — Consultar materiales protegidos
- `HU-026-MAPA-TECNICO-PARTICIPAR-EN-VIVO` — Participar en sesiones en vivo
- `HU-027-MAPA-TECNICO-CAMBIOS-SESIONES` — Administrar cambios en sesiones en vivo

## La maquetación visual (HTML) está en otro lado, a propósito

Los 4 diseños listos para copiar y pegar **no están en esta carpeta** — viven en el proyecto
frontend, junto con el CSS y las imágenes compartidas que necesitan para verse bien:

```
escuela-juridica-frontend/maquetacion-html/
├── 00-INDICE.html                        ← abrir primero, tiene la guía completa
├── HU-022-PF-AULA-ingresar.html
├── HU-023-PF-AULA-materiales.html
├── HU-026-PF-CALENDARIO-calendario.html
├── HU-027-PF-ADMIN-sesiones.html
├── estilos.css                           ← compartido por toda la app, no solo EP04
└── img/
```

Se abren con doble clic en el navegador, sin necesidad de tener Angular corriendo.

## Orden sugerido para leer

1. `maquetacion-html/00-INDICE.html` — contexto visual rápido de las 4 pantallas.
2. El `.md` o `.docx` de tu historia, acá en esta carpeta — ahí está el "cómo programar" real
   (entidades, DTOs, servicios, controlador, servicio Angular), con código listo para copiar y
   adaptar.
