-- ESEJUR - EP01 Datos iniciales y de prueba
-- Requiere 01-tablas-y-llaves-ep01.sql.
-- Los nombres corresponden al equipo de Desarrollo Web Integrado.
-- Los correos, telefonos, contenido academico y matriculas son simulados.

BEGIN;

-- Datos maestros del negocio.
INSERT INTO tipo_curso (codigo, nombre, orden) VALUES
('DIPLOMADO', 'Diplomado', 10),
('CURSO_ESPECIALIZADO', 'Especialización', 20),
('CURSO', 'Curso', 30),
('PROGRAMA_ACTUALIZACION', 'Actualización', 40),
('SEMINARIO', 'Seminario', 50),
('TALLER_CASOS_PRACTICOS', 'Taller de casos prácticos', 60),
('CONGRESO_CONFERENCIA', 'Congreso / Conferencia', 70),
('IN_HOUSE', 'In-house', 80)
ON CONFLICT (codigo) DO UPDATE SET
nombre = EXCLUDED.nombre, orden = EXCLUDED.orden, activo = true,
modificado_en = CURRENT_TIMESTAMP;

INSERT INTO categoria_tematica (codigo, nombre, orden) VALUES
('DERECHO_REGISTRAL', 'Derecho Registral', 10),
('CONTRATACIONES_ESTADO', 'Contrataciones del Estado', 20),
('DERECHO_PENAL', 'Derecho Penal', 30),
('DERECHO_LABORAL', 'Derecho Laboral', 40),
('DERECHO_TRIBUTARIO', 'Derecho Tributario', 50),
('DERECHO_ADMINISTRATIVO', 'Derecho Administrativo', 60),
('LITIGACION', 'Litigación', 70),
('ARBITRAJE', 'Arbitraje', 80),
('GESTION_PUBLICA', 'Gestión Pública', 90),
('DERECHO_NOTARIAL', 'Derecho Notarial', 100),
('DERECHO_INMOBILIARIO', 'Derecho Inmobiliario', 110),
('DERECHO_URBANISTICO', 'Derecho Urbanístico', 120),
('HABILIDADES_PROFESIONALES', 'Habilidades profesionales', 130)
ON CONFLICT (codigo) DO UPDATE SET
nombre = EXCLUDED.nombre, orden = EXCLUDED.orden, activo = true,
modificado_en = CURRENT_TIMESTAMP;

INSERT INTO entidad_certificadora (nombre, activo) VALUES
('Escuela Jurídica', true),
('Colegio de Notarios de Lima', true)
ON CONFLICT (nombre) DO UPDATE SET
activo = EXCLUDED.activo, modificado_en = CURRENT_TIMESTAMP;

INSERT INTO estado_curso (codigo, nombre, descripcion, orden) VALUES
('BORRADOR', 'Borrador', 'El curso se encuentra en preparación y todavía no es público.', 10),
('PUBLICADO', 'Publicado', 'El curso es visible y puede admitir nuevas matrículas.', 20),
('EN_CURSO', 'En curso', 'El curso ya inició y continúa según sus reglas de matrícula.', 30),
('CERRADO', 'Cerrado', 'El curso terminó normalmente o fue retirado de nuevas matrículas.', 40),
('CANCELADO', 'Cancelado', 'La Escuela decidió que el curso no continuará.', 50)
ON CONFLICT (codigo) DO UPDATE SET
nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion,
orden = EXCLUDED.orden, modificado_en = CURRENT_TIMESTAMP;

INSERT INTO rol (codigo, nombre, descripcion) VALUES
('ROLE_ALUMNO', 'Alumno', 'Puede matricularse y desarrollar cursos.'),
('ROLE_ADMINISTRADOR', 'Administrador', 'Gestiona la operación de la plataforma.')
ON CONFLICT (codigo) DO UPDATE SET
nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion, activo = true,
modificado_en = CURRENT_TIMESTAMP;

-- Personas. Milagros y Ariana son docentes sin cuenta de acceso.
INSERT INTO persona (
    nombres, apellido_paterno, apellido_materno, telefono,
    documento_identidad, foto_url, cargo_profesional, biografia_profesional
)
SELECT v.nombres, v.apellido_paterno, v.apellido_materno, v.telefono,
       NULL, v.foto_url, v.cargo_profesional, v.biografia_profesional
FROM (VALUES
    ('Administrador', 'General', NULL, '999 100 101', NULL, NULL, NULL),
    ('Gabriel Antonio', 'Mayanga', 'Cabrera', '999 100 102', NULL, NULL, NULL),
    ('Joel Anthony', 'Saldaña', 'Chávez', '999 100 103', NULL, NULL, NULL),
    ('Juan José', 'Morales', 'Velasquez', '999 100 104', NULL, NULL, NULL),
    ('Milagros Fernanda', 'Salvatierra', 'Guevara', NULL,
     'img/instructores/instructor-1.jpg', 'Docente especialista en Derecho Público',
     'Docente con experiencia en contratación pública, derecho administrativo y gestión estatal.'),
    ('Ariana Fiorella', 'Lazaro', 'Maza', NULL,
     'img/instructores/instructor-2.jpg', 'Docente especialista en Litigación',
     'Docente con experiencia en litigación oral, derecho penal y redacción jurídica.')
) AS v (nombres, apellido_paterno, apellido_materno, telefono,
        foto_url, cargo_profesional, biografia_profesional)
WHERE NOT EXISTS (
    SELECT 1 FROM persona p
    WHERE p.nombres = v.nombres
      AND p.apellido_paterno = v.apellido_paterno
      AND p.apellido_materno IS NOT DISTINCT FROM v.apellido_materno
);

-- Contraseña de prueba para las cuatro cuentas: Marco1415@
-- Se guardan hashes BCrypt independientes compatibles con Spring Security.
INSERT INTO usuario (
    persona_id, correo, origen_registro, activo, contrasena_hash,
    google_subject, requiere_cambio_contrasena, correo_verificado_en
)
SELECT p.persona_id, v.correo, v.origen_registro, true, v.hash,
       v.google_subject, false, CURRENT_TIMESTAMP
FROM (VALUES
    ('Administrador', 'General', NULL, 'admin@escuelajuridica.edu.pe',
     'ADMINISTRATIVO', '{bcrypt}$2a$10$3q2hYuey7bsCOddOq/6JXeyITlKtm51hBOPZ8XodNvK.AbLF7ntZC', NULL),
    ('Gabriel Antonio', 'Mayanga', 'Cabrera', 'gabriel.mayanga@demo.esejur.pe',
     'GOOGLE', '{bcrypt}$2a$10$41ZIgbSSlPVd3pBrI3BGuu9O6s0NXg.XnZZ2svvawPxfzrHU5Aj3G',
     'demo-google-gabriel-mayanga'),
    ('Joel Anthony', 'Saldaña', 'Chávez', 'joel.saldana@demo.esejur.pe',
     'FORMULARIO', '{bcrypt}$2a$10$RHTaYvzW9s7uFSCHQTkLFO0YUf.qVdldhm8UJAo1Zb9Ol6UjFxWnq', NULL),
    ('Juan José', 'Morales', 'Velasquez', 'juan.morales@demo.esejur.pe',
     'FORMULARIO', '{bcrypt}$2a$10$1AG2h0Y3AYpVmFFp/HPxl.EOOU0fI5pFi2OXoafZjRq3oDnCmUpmq', NULL)
) AS v (nombres, apellido_paterno, apellido_materno, correo,
        origen_registro, hash, google_subject)
JOIN persona p ON p.nombres = v.nombres
 AND p.apellido_paterno = v.apellido_paterno
 AND p.apellido_materno IS NOT DISTINCT FROM v.apellido_materno
ON CONFLICT (correo) DO UPDATE SET
persona_id = EXCLUDED.persona_id, origen_registro = EXCLUDED.origen_registro,
activo = true, contrasena_hash = EXCLUDED.contrasena_hash,
google_subject = EXCLUDED.google_subject,
requiere_cambio_contrasena = false,
correo_verificado_en = EXCLUDED.correo_verificado_en,
deshabilitado_en = NULL, modificado_en = CURRENT_TIMESTAMP;

-- Enrique es administrador; Gabriel, Joel y Juan son alumnos.
INSERT INTO usuario_rol (usuario_id, rol_id, es_principal)
SELECT u.usuario_id, r.rol_id, true
FROM usuario u
JOIN rol r ON r.codigo = CASE
    WHEN u.correo = 'admin@escuelajuridica.edu.pe'
    THEN 'ROLE_ADMINISTRADOR' ELSE 'ROLE_ALUMNO' END
WHERE u.correo IN (
    'admin@escuelajuridica.edu.pe', 'gabriel.mayanga@demo.esejur.pe',
    'joel.saldana@demo.esejur.pe', 'juan.morales@demo.esejur.pe'
)
ON CONFLICT (usuario_id, rol_id) DO UPDATE SET es_principal = true;

INSERT INTO codigo_verificacion_correo
    (usuario_id, codigo_hash, estado_envio, utilizado_en)
SELECT usuario_id,
       '0a14e0db42e1a9cc8680ab5395bcae92416186796fa4eb636c431d4d53d8f53d',
       'ACEPTADO', CURRENT_TIMESTAMP
FROM usuario WHERE correo = 'joel.saldana@demo.esejur.pe'
ON CONFLICT (codigo_hash) DO NOTHING;

INSERT INTO token_recuperacion_acceso
    (usuario_id, token_hash, estado_envio, expira_en, utilizado_en)
SELECT usuario_id,
       '9d504a09b3caa4d01105c85c91fdb518aac2e0bdf94cf930728d9f049cfb7f34',
       'ACEPTADO', CURRENT_TIMESTAMP + interval '60 minutes', CURRENT_TIMESTAMP
FROM usuario WHERE correo = 'gabriel.mayanga@demo.esejur.pe'
ON CONFLICT (token_hash) DO NOTHING;

-- Quince cursos: nueve alineados con el prototipo y seis adicionales de prueba.
-- Angular abre cada ficha en /cursos/:id y url_amigable contiene ese :id.
WITH datos (
    slug, titulo, descripcion, imagen, tipo, categoria, entidad,
    modalidad, venta, estado, destacado, precio, promocion,
    inicio, fin, cierre, cupo, horas, beneficios
) AS (VALUES
('registral', 'Diplomado en Derecho Registral y Notarial',
 'Domina los fundamentos registrales y notariales mediante contenidos aplicados y casos jurídicos.',
 'https://images.unsplash.com/photo-1589578527966-fdac0f44566c?q=80&w=1200&auto=format&fit=crop',
 'DIPLOMADO', 'DERECHO_REGISTRAL', 'Colegio de Notarios de Lima',
 'VIRTUAL', 'PAGADO', 'EN_CURSO', true, 650.00, 450.00,
 NULL::date, NULL::date, NULL::date, NULL::integer, 120.00,
 ARRAY['Acceso inmediato', '21 lecciones', 'Certificado digital']::text[]),
('contrataciones', 'Especialización en Contrataciones del Estado',
 'Actualiza tus conocimientos sobre los procedimientos y responsabilidades de la contratación pública.',
 'https://images.unsplash.com/photo-1593115057322-e94b77572f20?q=80&w=1200&auto=format&fit=crop',
 'CURSO_ESPECIALIZADO', 'CONTRATACIONES_ESTADO', 'Escuela Jurídica',
 'HIBRIDO', 'PAGADO', 'PUBLICADO', true, 380.00, NULL::numeric,
 DATE '2026-09-15', DATE '2026-12-12', DATE '2026-09-14', 40, 96.00,
 ARRAY['Sesiones en vivo', 'Contenido grabado', 'Certificado digital']::text[]),
('penal', 'Curso de Litigación Oral en el Proceso Penal',
 'Fortalece tus habilidades de argumentación, interrogatorio y actuación oral en el proceso penal.',
 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?q=80&w=1200&auto=format&fit=crop',
 'CURSO', 'DERECHO_PENAL', 'Escuela Jurídica',
 'EN_VIVO', 'PAGADO', 'PUBLICADO', false, 290.00, NULL::numeric,
 DATE '2026-09-02', DATE '2026-10-28', DATE '2026-09-01', 35, 48.00,
 ARRAY['12 sesiones en vivo', 'Casos prácticos', 'Certificado digital']::text[]),
('laboral', 'Diplomado en Derecho Laboral y Procesal Laboral',
 'Comprende la relación laboral y domina las principales actuaciones del proceso laboral.',
 'https://images.unsplash.com/photo-1450101499163-c8848c66ca85?q=80&w=1200&auto=format&fit=crop',
 'DIPLOMADO', 'DERECHO_LABORAL', 'Escuela Jurídica',
 'VIRTUAL', 'PAGADO', 'EN_CURSO', true, 700.00, 520.00,
 NULL::date, NULL::date, NULL::date, NULL::integer, 132.00,
 ARRAY['Acceso inmediato', '24 lecciones', 'Certificado digital']::text[]),
('tributaria', 'Actualización en Tributación Municipal',
 'Revisa los principales tributos municipales y su aplicación en casos frecuentes.',
 'https://images.unsplash.com/photo-1505664194779-8beaceb93744?q=80&w=1200&auto=format&fit=crop',
 'PROGRAMA_ACTUALIZACION', 'DERECHO_TRIBUTARIO', 'Escuela Jurídica',
 'VIRTUAL', 'GRATUITO', 'EN_CURSO', false, 0.00, NULL::numeric,
 NULL::date, NULL::date, NULL::date, NULL::integer, 24.00,
 ARRAY['Acceso gratuito', '9 lecciones', 'Material descargable']::text[]),
('sancionador', 'Especialización en Derecho Administrativo Sancionador',
 'Analiza la potestad sancionadora, sus principios y el desarrollo del procedimiento administrativo.',
 'https://images.unsplash.com/photo-1603791440384-56cd371ee9a7?q=80&w=1200&auto=format&fit=crop',
 'CURSO_ESPECIALIZADO', 'DERECHO_ADMINISTRATIVO', 'Escuela Jurídica',
 'HIBRIDO', 'PAGADO', 'EN_CURSO', false, 410.00, NULL::numeric,
 DATE '2026-08-11', DATE '2026-12-05', DATE '2026-09-10', 45, 88.00,
 ARRAY['Sesiones en vivo', 'Contenido grabado', '17 lecciones']::text[]),
('redaccion', 'Curso de Redacción de Escritos Judiciales',
 'Mejora la claridad, estructura y argumentación de tus principales escritos jurídicos.',
 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?q=80&w=1200&auto=format&fit=crop',
 'CURSO', 'LITIGACION', 'Escuela Jurídica',
 'VIRTUAL', 'PAGADO', 'EN_CURSO', false, 240.00, 180.00,
 NULL::date, NULL::date, NULL::date, NULL::integer, 20.00,
 ARRAY['Acceso inmediato', '8 lecciones', 'Modelos descargables']::text[]),
('arbitraje', 'Curso de Arbitraje y Medios Alternativos',
 'Conoce las etapas del arbitraje y los principales mecanismos alternativos de solución de conflictos.',
 'https://images.unsplash.com/photo-1606770347238-77fcfd29906c?q=80&w=1200&auto=format&fit=crop',
 'CURSO', 'ARBITRAJE', 'Escuela Jurídica',
 'EN_VIVO', 'PAGADO', 'EN_CURSO', false, 350.00, NULL::numeric,
 DATE '2026-08-29', DATE '2026-10-10', DATE '2026-09-05', 3, 40.00,
 ARRAY['11 sesiones en vivo', 'Casos arbitrales', 'Certificado digital']::text[]),
('gestion', 'Diplomado en Gestión Pública y Modernización del Estado',
 'Desarrolla una visión aplicada de la gestión pública y de la modernización estatal.',
 'https://images.unsplash.com/photo-1436450412740-6b988f486c6b?q=80&w=1200&auto=format&fit=crop',
 'DIPLOMADO', 'GESTION_PUBLICA', 'Escuela Jurídica',
 'HIBRIDO', 'PAGADO', 'CERRADO', false, 480.00, NULL::numeric,
 DATE '2026-10-06', DATE '2027-02-20', DATE '2026-10-05', 50, 120.00,
 ARRAY['Sesiones en vivo', 'Contenido grabado', '20 lecciones']::text[]),
('inmobiliario', 'Especialización en Saneamiento Físico Legal de Inmuebles',
 'Aprende a identificar y resolver problemas de saneamiento, titulación y regularización de inmuebles.',
 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?q=80&w=1200&auto=format&fit=crop',
 'CURSO_ESPECIALIZADO', 'DERECHO_INMOBILIARIO', 'Colegio de Notarios de Lima',
 'VIRTUAL', 'PAGADO', 'EN_CURSO', true, 360.00, 290.00,
 NULL::date, NULL::date, NULL::date, NULL::integer, 72.00,
 ARRAY['Acceso inmediato', '14 lecciones', 'Casos de saneamiento inmobiliario']::text[]),
('notarial', 'Curso de Derecho Notarial y Función Notarial',
 'Estudia los principales instrumentos notariales, procedimientos y responsabilidades de la función notarial.',
 'https://images.unsplash.com/photo-1455390582262-044cdead277a?q=80&w=1200&auto=format&fit=crop',
 'CURSO', 'DERECHO_NOTARIAL', 'Colegio de Notarios de Lima',
 'EN_VIVO', 'PAGADO', 'PUBLICADO', false, 260.00, NULL::numeric,
 DATE '2026-09-20', DATE '2026-11-15', DATE '2026-09-19', 30, 32.00,
 ARRAY['10 sesiones en vivo', 'Material notarial', 'Certificado digital']::text[]),
('urbanistico', 'Actualización en Derecho Urbanístico y Licencias',
 'Revisa el marco jurídico aplicable a habilitaciones urbanas, edificaciones y licencias municipales.',
 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=1200&auto=format&fit=crop',
 'PROGRAMA_ACTUALIZACION', 'DERECHO_URBANISTICO', 'Escuela Jurídica',
 'VIRTUAL', 'GRATUITO', 'EN_CURSO', false, 0.00, NULL::numeric,
 NULL::date, NULL::date, NULL::date, NULL::integer, 18.00,
 ARRAY['Acceso gratuito', '7 lecciones', 'Material descargable']::text[]),
('argumentacion', 'Taller de Argumentación Jurídica y Oratoria',
 'Desarrolla argumentos claros y fortalece la exposición oral mediante ejercicios jurídicos prácticos.',
 'https://images.unsplash.com/photo-1556761175-b413da4baf72?q=80&w=1200&auto=format&fit=crop',
 'TALLER_CASOS_PRACTICOS', 'HABILIDADES_PROFESIONALES', 'Escuela Jurídica',
 'HIBRIDO', 'PAGADO', 'PUBLICADO', true, 220.00, NULL::numeric,
 DATE '2026-10-01', DATE '2026-11-20', DATE '2026-09-30', 25, 36.00,
 ARRAY['Prácticas guiadas', 'Contenido grabado', '12 lecciones']::text[]),
('procedimiento', 'Seminario de Procedimiento Administrativo General',
 'Analiza las etapas, garantías, plazos y recursos del procedimiento administrativo general.',
 'https://images.unsplash.com/photo-1521791136064-7986c2920216?q=80&w=1200&auto=format&fit=crop',
 'SEMINARIO', 'DERECHO_ADMINISTRATIVO', 'Escuela Jurídica',
 'EN_VIVO', 'PAGADO', 'PUBLICADO', false, 190.00, NULL::numeric,
 DATE '2026-09-10', DATE '2026-10-08', DATE '2026-09-09', 50, 16.00,
 ARRAY['6 sesiones en vivo', 'Análisis normativo', 'Certificado digital']::text[]),
('casos-registrales', 'Taller de Casos Prácticos Registrales',
 'Resuelve observaciones y casos frecuentes de calificación registral mediante ejercicios aplicados.',
 'https://images.unsplash.com/photo-1450101499163-c8848c66ca85?q=80&w=1200&auto=format&fit=crop',
 'TALLER_CASOS_PRACTICOS', 'DERECHO_REGISTRAL', 'Colegio de Notarios de Lima',
 'VIRTUAL', 'PAGADO', 'EN_CURSO', false, 150.00, 120.00,
 NULL::date, NULL::date, NULL::date, NULL::integer, 16.00,
 ARRAY['Acceso inmediato', '6 casos guiados', 'Material descargable']::text[])
)
INSERT INTO curso (
 url_amigable, titulo, descripcion, imagen_portada_url,
 tipo_curso_id, categoria_tematica_id, entidad_certificadora_id,
 modalidad, tipo_venta, estado_curso_id, destacado,
 precio_regular, precio_promocional, fecha_inicio, fecha_fin,
 fecha_cierre_matricula, cupo_maximo, horas_academicas, beneficios,
 creado_por_usuario_id, publicado_en, cerrado_en
)
SELECT d.slug, d.titulo, d.descripcion, d.imagen,
 tc.tipo_curso_id, ct.categoria_tematica_id, ec.entidad_certificadora_id,
 d.modalidad, d.venta, est.estado_curso_id, d.destacado,
 d.precio, d.promocion, d.inicio, d.fin, d.cierre, d.cupo, d.horas,
 d.beneficios, admin.usuario_id, TIMESTAMPTZ '2026-08-01 09:00:00-05',
 CASE WHEN d.estado = 'CERRADO' THEN CURRENT_TIMESTAMP END
FROM datos d
JOIN tipo_curso tc ON tc.codigo = d.tipo
JOIN categoria_tematica ct ON ct.codigo = d.categoria
JOIN entidad_certificadora ec ON ec.nombre = d.entidad
JOIN estado_curso est ON est.codigo = d.estado
CROSS JOIN usuario admin
WHERE admin.correo = 'admin@escuelajuridica.edu.pe'
ON CONFLICT (url_amigable) DO UPDATE SET
titulo = EXCLUDED.titulo, descripcion = EXCLUDED.descripcion,
imagen_portada_url = EXCLUDED.imagen_portada_url,
tipo_curso_id = EXCLUDED.tipo_curso_id,
categoria_tematica_id = EXCLUDED.categoria_tematica_id,
entidad_certificadora_id = EXCLUDED.entidad_certificadora_id,
modalidad = EXCLUDED.modalidad, tipo_venta = EXCLUDED.tipo_venta,
estado_curso_id = EXCLUDED.estado_curso_id, destacado = EXCLUDED.destacado,
precio_regular = EXCLUDED.precio_regular,
precio_promocional = EXCLUDED.precio_promocional,
promocion_inicio_en = NULL, promocion_fin_en = NULL,
fecha_inicio = EXCLUDED.fecha_inicio, fecha_fin = EXCLUDED.fecha_fin,
fecha_cierre_matricula = EXCLUDED.fecha_cierre_matricula,
cupo_maximo = EXCLUDED.cupo_maximo,
horas_academicas = EXCLUDED.horas_academicas,
beneficios = EXCLUDED.beneficios,
creado_por_usuario_id = EXCLUDED.creado_por_usuario_id,
publicado_en = EXCLUDED.publicado_en, cerrado_en = EXCLUDED.cerrado_en,
cancelado_en = NULL, modificado_en = CURRENT_TIMESTAMP;

-- Asignaciones docentes; algunos cursos muestran dos docentes.
WITH asignacion (slug, nombres, apellido, orden) AS (VALUES
('registral', 'Milagros Fernanda', 'Salvatierra', 1),
('registral', 'Ariana Fiorella', 'Lazaro', 2),
('contrataciones', 'Milagros Fernanda', 'Salvatierra', 1),
('penal', 'Ariana Fiorella', 'Lazaro', 1),
('laboral', 'Milagros Fernanda', 'Salvatierra', 1),
('tributaria', 'Ariana Fiorella', 'Lazaro', 1),
('sancionador', 'Milagros Fernanda', 'Salvatierra', 1),
('sancionador', 'Ariana Fiorella', 'Lazaro', 2),
('redaccion', 'Ariana Fiorella', 'Lazaro', 1),
('arbitraje', 'Milagros Fernanda', 'Salvatierra', 1),
('gestion', 'Milagros Fernanda', 'Salvatierra', 1),
('gestion', 'Ariana Fiorella', 'Lazaro', 2),
('inmobiliario', 'Milagros Fernanda', 'Salvatierra', 1),
('notarial', 'Ariana Fiorella', 'Lazaro', 1),
('urbanistico', 'Milagros Fernanda', 'Salvatierra', 1),
('argumentacion', 'Ariana Fiorella', 'Lazaro', 1),
('procedimiento', 'Milagros Fernanda', 'Salvatierra', 1),
('casos-registrales', 'Milagros Fernanda', 'Salvatierra', 1),
('casos-registrales', 'Ariana Fiorella', 'Lazaro', 2)
)
INSERT INTO curso_docente (curso_id, persona_id, orden)
SELECT c.curso_id, p.persona_id, a.orden
FROM asignacion a
JOIN curso c ON c.url_amigable = a.slug
JOIN persona p ON p.nombres = a.nombres AND p.apellido_paterno = a.apellido
ON CONFLICT (curso_id, persona_id) DO UPDATE SET orden = EXCLUDED.orden;

-- 62 modulos: 43 del prototipo y 19 de los seis cursos adicionales.
WITH cfg (slug, modulos, lecciones) AS (VALUES
('registral',6,21), ('contrataciones',5,18), ('penal',4,12),
('laboral',7,24), ('tributaria',3,9), ('sancionador',5,17),
('redaccion',3,8), ('arbitraje',4,11), ('gestion',6,20),
('inmobiliario',4,14), ('notarial',4,10), ('urbanistico',3,7),
('argumentacion',4,12), ('procedimiento',2,6), ('casos-registrales',2,6)
)
INSERT INTO modulo (curso_id, titulo, descripcion, orden)
SELECT c.curso_id, 'Módulo ' || s.n || ' · ' || c.titulo,
       'Unidad académica ' || s.n || ' del programa.', s.n
FROM cfg JOIN curso c ON c.url_amigable = cfg.slug
CROSS JOIN LATERAL generate_series(1, cfg.modulos) AS s(n)
ON CONFLICT (curso_id, orden) DO UPDATE SET
titulo = EXCLUDED.titulo, descripcion = EXCLUDED.descripcion,
activo = true, modificado_en = CURRENT_TIMESTAMP;

-- 195 lecciones. La primera de cada curso es una vista previa grabada.
WITH cfg (slug, modulos, lecciones) AS (VALUES
('registral',6,21), ('contrataciones',5,18), ('penal',4,12),
('laboral',7,24), ('tributaria',3,9), ('sancionador',5,17),
('redaccion',3,8), ('arbitraje',4,11), ('gestion',6,20),
('inmobiliario',4,14), ('notarial',4,10), ('urbanistico',3,7),
('argumentacion',4,12), ('procedimiento',2,6), ('casos-registrales',2,6)
), dist AS (
 SELECT c.curso_id, c.modalidad, c.fecha_inicio, c.fecha_fin,
        cfg.lecciones, s.n,
        1 + (((s.n - 1) * cfg.modulos) / cfg.lecciones) AS orden_modulo
 FROM cfg JOIN curso c ON c.url_amigable = cfg.slug
 CROSS JOIN LATERAL generate_series(1, cfg.lecciones) AS s(n)
), preparada AS (
 SELECT dist.*,
        row_number() OVER (PARTITION BY curso_id, orden_modulo ORDER BY n)::integer AS orden_leccion,
        CASE WHEN n = 1 THEN 'GRABADA'
             WHEN modalidad = 'EN_VIVO' THEN 'EN_VIVO'
             WHEN modalidad = 'HIBRIDO' AND n % 3 = 0 THEN 'EN_VIVO'
             ELSE 'GRABADA' END AS tipo_leccion
 FROM dist
)
INSERT INTO leccion (
 modulo_id, titulo, descripcion, orden, tipo, estado,
 es_obligatoria, es_vista_previa,
 fecha_hora_inicio, fecha_hora_fin, enlace_reunion
)
SELECT m.modulo_id,
 CASE WHEN p.n = 1 THEN 'Bienvenida y vista previa del curso'
      ELSE 'Lección ' || p.n || ' · Desarrollo aplicado' END,
 CASE WHEN p.n = 1 THEN 'Introducción pública al contenido, metodología y resultados del curso.'
      ELSE 'Contenido académico reservado para alumnos matriculados.' END,
 p.orden_leccion, p.tipo_leccion,
 CASE WHEN p.tipo_leccion = 'EN_VIVO' THEN 'PROGRAMADA' ELSE 'DISPONIBLE' END,
 true, p.n = 1,
 CASE WHEN p.tipo_leccion = 'EN_VIVO' THEN
   (p.fecha_inicio + round((p.n - 1)::numeric * (p.fecha_fin - p.fecha_inicio)
      / greatest(p.lecciones - 1, 1))::integer + TIME '19:00')
      AT TIME ZONE 'America/Lima' END,
 CASE WHEN p.tipo_leccion = 'EN_VIVO' THEN
   (p.fecha_inicio + round((p.n - 1)::numeric * (p.fecha_fin - p.fecha_inicio)
      / greatest(p.lecciones - 1, 1))::integer + TIME '21:00')
      AT TIME ZONE 'America/Lima' END,
 CASE WHEN p.tipo_leccion = 'EN_VIVO'
      THEN 'https://meet.google.com/ese-jur-demo' END
FROM preparada p
JOIN modulo m ON m.curso_id = p.curso_id AND m.orden = p.orden_modulo
ON CONFLICT (modulo_id, orden) DO UPDATE SET
titulo = EXCLUDED.titulo, descripcion = EXCLUDED.descripcion,
tipo = EXCLUDED.tipo, estado = EXCLUDED.estado,
es_obligatoria = EXCLUDED.es_obligatoria,
es_vista_previa = EXCLUDED.es_vista_previa,
fecha_hora_inicio = EXCLUDED.fecha_hora_inicio,
fecha_hora_fin = EXCLUDED.fecha_hora_fin,
enlace_reunion = EXCLUDED.enlace_reunion,
motivo_cancelacion = NULL, activo = true,
modificado_en = CURRENT_TIMESTAMP;

-- Un video publico y una guia reservada por cada curso.
WITH demo AS (
 SELECT c.url_amigable, u.usuario_id admin_id
 FROM curso c CROSS JOIN usuario u
 WHERE c.url_amigable IN ('registral','contrataciones','penal','laboral',
   'tributaria','sancionador','redaccion','arbitraje','gestion',
   'inmobiliario','notarial','urbanistico','argumentacion','procedimiento',
   'casos-registrales')
 AND u.correo = 'admin@escuelajuridica.edu.pe'
)
INSERT INTO recurso (
 tipo, origen, referencia, nombre_archivo, tipo_mime, tamano_bytes,
 duracion_segundos, duracion_detectada, creado_por_usuario_id
)
SELECT 'VIDEO', 'ARCHIVO_LOCAL', 'cursos/' || url_amigable || '/vista-previa.mp4',
       'vista-previa-' || url_amigable || '.mp4', 'video/mp4', 52428800,
       720, true, admin_id FROM demo
UNION ALL
SELECT 'PDF', 'ARCHIVO_LOCAL', 'cursos/' || url_amigable || '/guia-programa.pdf',
       'guia-' || url_amigable || '.pdf', 'application/pdf', 1048576,
       NULL, false, admin_id FROM demo
ON CONFLICT (referencia) DO UPDATE SET
nombre_archivo = EXCLUDED.nombre_archivo, tipo_mime = EXCLUDED.tipo_mime,
tamano_bytes = EXCLUDED.tamano_bytes,
duracion_segundos = EXCLUDED.duracion_segundos,
duracion_detectada = EXCLUDED.duracion_detectada,
creado_por_usuario_id = EXCLUDED.creado_por_usuario_id,
activo = true, modificado_en = CURRENT_TIMESTAMP;

WITH ordenada AS (
 SELECT c.url_amigable, l.leccion_id,
        row_number() OVER (PARTITION BY c.curso_id ORDER BY m.orden, l.orden) posicion
 FROM curso c JOIN modulo m ON m.curso_id = c.curso_id
 JOIN leccion l ON l.modulo_id = m.modulo_id
 WHERE c.url_amigable IN ('registral','contrataciones','penal','laboral',
   'tributaria','sancionador','redaccion','arbitraje','gestion',
   'inmobiliario','notarial','urbanistico','argumentacion','procedimiento',
   'casos-registrales')
), materiales AS (
 SELECT o.leccion_id, r.recurso_id, 'Video de vista previa'::varchar titulo,
        1 orden, false permite_descarga
 FROM ordenada o JOIN recurso r
 ON r.referencia = 'cursos/' || o.url_amigable || '/vista-previa.mp4'
 WHERE o.posicion = 1
 UNION ALL
 SELECT o.leccion_id, r.recurso_id, 'Guía del programa'::varchar,
        1, true
 FROM ordenada o JOIN recurso r
 ON r.referencia = 'cursos/' || o.url_amigable || '/guia-programa.pdf'
 WHERE o.posicion = 2
)
INSERT INTO material_leccion
 (leccion_id, recurso_id, titulo, orden, permite_descarga)
SELECT leccion_id, recurso_id, titulo, orden, permite_descarga FROM materiales
ON CONFLICT (leccion_id, orden) DO UPDATE SET
recurso_id = EXCLUDED.recurso_id, titulo = EXCLUDED.titulo,
permite_descarga = EXCLUDED.permite_descarga,
activo = true, modificado_en = CURRENT_TIMESTAMP;

-- Arbitraje queda sin cupos: 3 cupos y 3 matriculas activas.
WITH demo (correo, slug, forma) AS (VALUES
('gabriel.mayanga@demo.esejur.pe', 'arbitraje', 'PAGO'),
('joel.saldana@demo.esejur.pe', 'arbitraje', 'PAGO'),
('juan.morales@demo.esejur.pe', 'arbitraje', 'PAGO'),
('gabriel.mayanga@demo.esejur.pe', 'registral', 'PAGO'),
('joel.saldana@demo.esejur.pe', 'tributaria', 'GRATUITA'),
('juan.morales@demo.esejur.pe', 'sancionador', 'PAGO')
)
INSERT INTO matricula (
 usuario_id, curso_id, estado, forma_ingreso, fecha_matricula, fecha_activacion
)
SELECT u.usuario_id, c.curso_id, 'ACTIVA', d.forma,
       TIMESTAMPTZ '2026-08-25 10:00:00-05',
       TIMESTAMPTZ '2026-08-25 10:01:00-05'
FROM demo d JOIN usuario u ON u.correo = d.correo
JOIN curso c ON c.url_amigable = d.slug
ON CONFLICT (usuario_id, curso_id) DO UPDATE SET
estado = EXCLUDED.estado, forma_ingreso = EXCLUDED.forma_ingreso,
fecha_matricula = EXCLUDED.fecha_matricula,
fecha_activacion = EXCLUDED.fecha_activacion,
fecha_vencimiento = NULL, motivo_cancelacion = NULL,
modificado_en = CURRENT_TIMESTAMP;

COMMIT;
