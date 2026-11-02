package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Examen;
import pe.edu.utp.escuela.app.entity.IntentoExamen;
import pe.edu.utp.escuela.app.entity.OpcionPregunta;
import pe.edu.utp.escuela.app.entity.Pregunta;
import pe.edu.utp.escuela.app.entity.RespuestaIntento;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.repository.ExamenRepository;
import pe.edu.utp.escuela.app.repository.IntentoExamenRepository;
import pe.edu.utp.escuela.app.repository.OpcionPreguntaRepository;
import pe.edu.utp.escuela.app.repository.PreguntaRepository;
import pe.edu.utp.escuela.app.repository.RespuestaIntentoRepository;

/** EP05 · HU-028 / HU-029 / HU-030 — Rendir un examen de calificación automática, con respuesta abierta y su revisión,
 * por HTTP y con persistencia real en H2. */
class ExamenHttpTests extends HttpIntegracionBase {

    @Autowired ExamenRepository examenes;
    @Autowired PreguntaRepository preguntas;
    @Autowired OpcionPreguntaRepository opciones;
    @Autowired IntentoExamenRepository intentos;
    @Autowired RespuestaIntentoRepository respuestas;

    private Examen examenFinal(Curso curso, Integer maximoIntentos) {
        Examen examen = new Examen();
        examen.setCurso(curso);
        examen.setTitulo("Examen final");
        examen.setTipo("CALIFICADO");
        examen.setFinalidad("FINAL");
        examen.setOrden(1);
        examen.setMaximoIntentos(maximoIntentos);
        return examenes.saveAndFlush(examen);
    }

    private Pregunta preguntaUnica(Examen examen, int orden, String puntaje) {
        Pregunta pregunta = new Pregunta();
        pregunta.setExamen(examen);
        pregunta.setTipo("SELECCION_UNICA");
        pregunta.setEnunciado("Pregunta " + orden);
        pregunta.setPuntaje(new BigDecimal(puntaje));
        pregunta.setOrden(orden);
        return preguntas.saveAndFlush(pregunta);
    }

    private OpcionPregunta opcion(Pregunta pregunta, int orden, boolean correcta) {
        OpcionPregunta opcion = new OpcionPregunta();
        opcion.setPregunta(pregunta);
        opcion.setTexto("Opción " + orden);
        opcion.setEsCorrecta(correcta);
        opcion.setOrden(orden);
        return opciones.saveAndFlush(opcion);
    }

    private long iniciar(Usuario alumno, Examen examen) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/app/examenes/" + examen.getId() + "/intentos")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_PROGRESO"))
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    private void responder(Usuario alumno, long intento, Pregunta pregunta, OpcionPregunta elegida) throws Exception {
        mvc.perform(put("/api/app/intentos/" + intento + "/respuestas/" + pregunta.getId())
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opcionesIds\":[" + elegida.getId() + "]}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void sinSesionNoSePuedeIniciarUnExamen() throws Exception {
        mvc.perform(post("/api/app/examenes/1/intentos").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unAlumnoSinMatriculaNoVeLaFichaDelExamen() throws Exception {
        Usuario ajeno = alumno("Ajeno");
        Examen examen = examenFinal(curso("EN_CURSO", "VIRTUAL", "320.00"), 2);

        mvc.perform(get("/api/app/examenes/" + examen.getId()).with(comoAlumno(ajeno.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No tienes una matrícula en este curso."));
    }

    @Test
    void unExamenSinPreguntasNoSePuedeIniciar() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);

        mvc.perform(post("/api/app/examenes/" + examen.getId() + "/intentos")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este examen aún no tiene preguntas."));
    }

    @Test
    void respuestasCorrectasDanNotaVeinteYApruebanElExamen() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        Pregunta p2 = preguntaUnica(examen, 2, "10");
        OpcionPregunta ok1 = opcion(p1, 1, true);
        opcion(p1, 2, false);
        OpcionPregunta ok2 = opcion(p2, 1, true);
        opcion(p2, 2, false);

        long intento = iniciar(alumno, examen);
        responder(alumno, intento, p1, ok1);
        responder(alumno, intento, p2, ok2);

        mvc.perform(post("/api/app/intentos/" + intento + "/enviar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CALIFICADO"))
                .andExpect(jsonPath("$.nota").value(20.0))
                .andExpect(jsonPath("$.aprobado").value(true));

        IntentoExamen guardado = intentos.findById(intento).orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(guardado.getNota()));
        assertEquals(true, guardado.getAprobado());
    }

    @Test
    void unaRespuestaIncorrectaBajaLaNotaPorDebajoDelMinimoYElAlumnoNoAprueba() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        Pregunta p2 = preguntaUnica(examen, 2, "10");
        OpcionPregunta ok1 = opcion(p1, 1, true);
        opcion(p1, 2, false);
        opcion(p2, 1, true);
        OpcionPregunta mala2 = opcion(p2, 2, false);

        long intento = iniciar(alumno, examen);
        responder(alumno, intento, p1, ok1);
        responder(alumno, intento, p2, mala2);

        mvc.perform(post("/api/app/intentos/" + intento + "/enviar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CALIFICADO"))
                .andExpect(jsonPath("$.nota").value(10.0))
                .andExpect(jsonPath("$.aprobado").value(false));
    }

    @Test
    void alAgotarLosIntentosSinAprobarNoSePermiteReintentar() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 1);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        opcion(p1, 1, true);
        OpcionPregunta mala = opcion(p1, 2, false);

        long intento = iniciar(alumno, examen);
        responder(alumno, intento, p1, mala);
        mvc.perform(post("/api/app/intentos/" + intento + "/enviar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobado").value(false));

        mvc.perform(post("/api/app/examenes/" + examen.getId() + "/intentos")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("Agotaste tus intentos sin aprobar")));
        assertEquals(1, intentos.count());
    }

    @Test
    void unAlternativaDeOtraPreguntaSeRechazaAlGuardarLaRespuesta() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        Pregunta p2 = preguntaUnica(examen, 2, "10");
        opcion(p1, 1, true);
        OpcionPregunta deLaOtra = opcion(p2, 1, true);

        long intento = iniciar(alumno, examen);

        mvc.perform(put("/api/app/intentos/" + intento + "/respuestas/" + p1.getId())
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opcionesIds\":[" + deLaOtra.getId() + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Una alternativa no pertenece a esta pregunta."));
        assertEquals(0, respuestas.count());
    }

    @Test
    void otroAlumnoNoPuedeConsultarElIntentoAjeno() throws Exception {
        Usuario alumno = alumno("Ana");
        Usuario intruso = alumno("Intruso");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        opcion(p1, 1, true);

        long intento = iniciar(alumno, examen);

        mvc.perform(get("/api/app/intentos/" + intento).with(comoAlumno(intruso.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El intento no existe."));
    }

    @Test
    void unaRespuestaAbiertaDejaElIntentoEnRevisionYElAdministradorLoCalifica() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 2);
        Pregunta abierta = new Pregunta();
        abierta.setExamen(examen);
        abierta.setTipo("RESPUESTA_ABIERTA");
        abierta.setEnunciado("Explique el principio de legalidad");
        abierta.setPuntaje(new BigDecimal("20"));
        abierta.setOrden(1);
        abierta = preguntas.saveAndFlush(abierta);

        long intento = iniciar(alumno, examen);
        mvc.perform(put("/api/app/intentos/" + intento + "/respuestas/" + abierta.getId())
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"No hay delito sin ley previa.\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/app/intentos/" + intento + "/enviar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"))
                .andExpect(jsonPath("$.nota").doesNotExist());

        // Mientras haya una revisión pendiente no se admite otro intento.
        mvc.perform(post("/api/app/examenes/" + examen.getId() + "/intentos")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest());

        // El alumno no puede ver la bandeja de revisión.
        mvc.perform(get("/api/admin/evaluaciones/pendientes").with(comoAlumno(alumno.getId())))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/evaluaciones/pendientes").with(comoAdministrador(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].intentoId").value(intento))
                .andExpect(jsonPath("$.items[0].respuestasPendientes").value(1));

        RespuestaIntento pendiente = respuestas.findByIntentoExamen_Id(intento).getFirst();

        // Un puntaje por encima del máximo de la pregunta se rechaza.
        mvc.perform(post("/api/admin/evaluaciones/intentos/" + intento + "/calificar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuestas\":[{\"respuestaId\":" + pendiente.getId() + ",\"puntaje\":25}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El puntaje debe estar entre 0 y 20."));

        mvc.perform(post("/api/admin/evaluaciones/intentos/" + intento + "/calificar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuestas\":[{\"respuestaId\":" + pendiente.getId()
                                + ",\"puntaje\":16,\"observacion\":\"Buena fundamentación\"}]}"))
                .andExpect(status().isOk());

        IntentoExamen calificado = intentos.findById(intento).orElseThrow();
        assertEquals(IntentoExamen.CALIFICADO, calificado.getEstado());
        assertEquals(0, new BigDecimal("16.00").compareTo(calificado.getNota()));
        assertEquals(true, calificado.getAprobado());

        // Publicar la calificación dos veces no está permitido.
        mvc.perform(post("/api/admin/evaluaciones/intentos/" + intento + "/calificar")
                        .with(comoAdministrador(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuestas\":[{\"respuestaId\":" + pendiente.getId() + ",\"puntaje\":10}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void siSeAgotaElTiempoElIntentoSeEnviaSoloConLoGuardadoYNoAdmiteCambios() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        Examen examen = examenFinal(curso, 3);
        examen.setTiempoLimiteMinutos(30);
        examenes.saveAndFlush(examen);
        Pregunta p1 = preguntaUnica(examen, 1, "10");
        Pregunta p2 = preguntaUnica(examen, 2, "10");
        OpcionPregunta ok1 = opcion(p1, 1, true);
        opcion(p1, 2, false);
        opcion(p2, 1, true);

        long intento = iniciar(alumno, examen);
        responder(alumno, intento, p1, ok1);
        IntentoExamen enCurso = intentos.findById(intento).orElseThrow();
        enCurso.setExpiraEn(java.time.Instant.now().minusSeconds(60));
        intentos.saveAndFlush(enCurso);

        mvc.perform(put("/api/app/intentos/" + intento + "/respuestas/" + p2.getId())
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"opcionesIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El tiempo del intento terminó: se envió automáticamente con lo que tenías respondido."));

        // Solo la pregunta respondida a tiempo cuenta: 10 de 20 puntos.
        mvc.perform(get("/api/app/intentos/" + intento).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CALIFICADO"))
                .andExpect(jsonPath("$.cerradoPorTiempo").value(true))
                .andExpect(jsonPath("$.nota").value(10.0))
                .andExpect(jsonPath("$.aprobado").value(false));
    }

    @Test
    void laNotaSeCalculaSobreVeinteConDosDecimales() {
        assertEquals(0, new BigDecimal("14.00").compareTo(
                pe.edu.utp.escuela.app.service.ExamenAlumnoService.calcularNota(new BigDecimal("7"), new BigDecimal("10"))));
        assertEquals(0, new BigDecimal("13.33").compareTo(
                pe.edu.utp.escuela.app.service.ExamenAlumnoService.calcularNota(new BigDecimal("2"), new BigDecimal("3"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                pe.edu.utp.escuela.app.service.ExamenAlumnoService.calcularNota(BigDecimal.ZERO, new BigDecimal("10"))));
        assertEquals(0, new BigDecimal("20.00").compareTo(
                pe.edu.utp.escuela.app.service.ExamenAlumnoService.calcularNota(new BigDecimal("5"), new BigDecimal("5"))));
    }
}
