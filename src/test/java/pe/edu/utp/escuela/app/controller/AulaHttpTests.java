package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.Modulo;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.repository.ProgresoLeccionRepository;

/** EP04 — Aula del alumno por HTTP: autenticación, rol, matrícula, temario con bloqueos y registro de progreso con
 * persistencia real en H2. */
class AulaHttpTests extends HttpIntegracionBase {

    @Autowired ProgresoLeccionRepository progreso;

    private Curso cursoConDosLecciones(Usuario alumno, String estadoMatricula, Leccion[] salida) {
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, estadoMatricula);
        Modulo modulo = modulo(curso, 1);
        salida[0] = leccion(modulo, 1, "LECTURA");
        salida[1] = leccion(modulo, 2, "LECTURA");
        return curso;
    }

    @Test
    void sinSesionElAulaRespondeNoAutenticado() throws Exception {
        mvc.perform(get("/api/aula/cursos/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unAdministradorNoPuedeEntrarAlAulaDeUnAlumno() throws Exception {
        mvc.perform(get("/api/aula/cursos/1").with(comoAdministrador(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unAlumnoSinMatriculaEnElCursoRecibeNoEncontrado() throws Exception {
        Usuario ajeno = alumno("Ajeno");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");

        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(ajeno.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No tienes una matrícula en este curso."));
    }

    @Test
    void unaMatriculaPendienteDePagoNoAbreElAula() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoConDosLecciones(alumno, "PENDIENTE_PAGO", new Leccion[2]);

        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(alumno.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El acceso no está disponible."));
    }

    @Test
    void unAlumnoMatriculadoRecibeSuTemarioConLaSegundaLeccionBloqueada() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] lecciones = new Leccion[2];
        Curso curso = cursoConDosLecciones(alumno, "ACTIVA", lecciones);

        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursoId").value(curso.getId()))
                .andExpect(jsonPath("$.porcentaje").value(0))
                .andExpect(jsonPath("$.modulos.length()").value(1))
                .andExpect(jsonPath("$.modulos[0].lecciones[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.modulos[0].lecciones[1].estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.modulos[0].lecciones[1].motivoBloqueo")
                        .value("Completa la lección anterior: Lección 1"))
                .andExpect(jsonPath("$.ultimaLeccionIdValida").value(lecciones[0].getId()));
    }

    @Test
    void completarLasLeccionesEnOrdenAvanzaElProgresoYSeGuardaEnLaBase() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] lecciones = new Leccion[2];
        Curso curso = cursoConDosLecciones(alumno, "ACTIVA", lecciones);

        // Saltarse la primera lección llamando directo a la API se rechaza con el motivo del bloqueo.
        mvc.perform(post("/api/app/lecciones/" + lecciones[1].getId() + "/completar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Completa la lección anterior: Lección 1"));
        assertEquals(0, progreso.count());

        mvc.perform(post("/api/app/lecciones/" + lecciones[0].getId() + "/completar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(1, progreso.count());
        assertTrue(progreso.findAll().getFirst().isCompletada());
        assertEquals("MANUAL", progreso.findAll().getFirst().getFormaCompletado());

        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentaje").value(50))
                .andExpect(jsonPath("$.modulos[0].lecciones[0].estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.modulos[0].lecciones[1].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.ultimaLeccionIdValida").value(lecciones[1].getId()));

        mvc.perform(post("/api/app/lecciones/" + lecciones[1].getId() + "/completar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/aula/cursos/" + curso.getId()).with(comoAlumno(alumno.getId())))
                .andExpect(jsonPath("$.porcentaje").value(100));
    }

    @Test
    void elAvanceDeVideoConDatosInvalidosSeRechazaAntesDeTocarLaBase() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(post("/api/app/materiales/1/progreso-video")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segundosReproducidos\":-5,\"ultimaPosicionSegundos\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    @Test
    void sinMatriculaNoSeListanLosMaterialesDeUnaLeccion() throws Exception {
        Usuario ajeno = alumno("Ajeno");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        Leccion leccion = leccion(modulo(curso, 1), 1, "LECTURA");

        mvc.perform(get("/api/app/lecciones/" + leccion.getId() + "/materiales").with(comoAlumno(ajeno.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No tienes una matrícula en este curso."));
    }

    @Test
    void conMatriculaActivaLaLeccionSinMaterialesRespondeUnaListaVacia() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] lecciones = new Leccion[2];
        cursoConDosLecciones(alumno, "ACTIVA", lecciones);

        mvc.perform(get("/api/app/lecciones/" + lecciones[0].getId() + "/materiales")
                        .with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elCalendarioDeSesionesDeUnAlumnoSinMatriculasActivasVieneVacio() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/app/sesiones/calendario").param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elCalendarioRechazaUnRangoDeFechasInvertido() throws Exception {
        Usuario alumno = alumno("Ana");

        mvc.perform(get("/api/app/sesiones/calendario").param("desde", "2026-09-30").param("hasta", "2026-09-01")
                        .with(comoAlumno(alumno.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha final no puede ser anterior a la fecha inicial."));
    }
}
