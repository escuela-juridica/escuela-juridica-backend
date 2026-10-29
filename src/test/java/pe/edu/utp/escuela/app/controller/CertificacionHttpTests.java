package pe.edu.utp.escuela.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.utp.escuela.app.entity.Certificado;
import pe.edu.utp.escuela.app.entity.Curso;
import pe.edu.utp.escuela.app.entity.Leccion;
import pe.edu.utp.escuela.app.entity.ReglaCurso;
import pe.edu.utp.escuela.app.entity.Usuario;
import pe.edu.utp.escuela.app.repository.CertificadoRepository;
import pe.edu.utp.escuela.app.repository.ReglaCursoRepository;

/** EP05 · HU-032 a HU-034 — Estado de la certificación, confirmación de datos, emisión, mis certificados, descarga y
 * verificación pública, con persistencia real en H2 y el PDF generado en disco. */
class CertificacionHttpTests extends HttpIntegracionBase {

    private static final String DATOS =
            "{\"nombres\":\"Ana María\",\"apellidoPaterno\":\"Pérez\",\"apellidoMaterno\":\"Gómez\"}";

    @Autowired ReglaCursoRepository reglas;
    @Autowired CertificadoRepository certificados;

    /** Curso virtual con una sola lección obligatoria y como único requisito completar el 100 % del progreso. */
    private Curso cursoSoloProgreso(Usuario alumno, Leccion[] leccion) {
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");
        matricula(alumno, curso, "ACTIVA");
        leccion[0] = leccion(modulo(curso, 1), 1, "LECTURA");
        ReglaCurso regla = new ReglaCurso();
        regla.setCurso(curso);
        regla.setRequiereExamenes(false);
        regla.setRequiereProgreso(true);
        regla.setProgresoMinimo(new BigDecimal("100"));
        regla.setDiasEsperaCertificado(0);
        reglas.saveAndFlush(regla);
        return curso;
    }

    private void completar(Usuario alumno, Leccion leccion) throws Exception {
        mvc.perform(post("/api/app/lecciones/" + leccion.getId() + "/completar")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Lleva al alumno hasta tener el certificado emitido y devuelve su código. */
    private String emitirCertificado(Usuario alumno, Curso curso, Leccion leccion) throws Exception {
        completar(alumno, leccion);
        mvc.perform(put("/api/app/cursos/" + curso.getId() + "/certificacion/datos")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk());
        MvcResult emitido = mvc.perform(post("/api/app/cursos/" + curso.getId() + "/certificacion/emitir")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(emitido.getResponse().getContentAsString()).get("certificado").get("codigo").asText();
    }

    @Test
    void sinSesionNoSeConsultaLaCertificacion() throws Exception {
        mvc.perform(get("/api/app/cursos/1/certificacion")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinMatriculaNoHayCertificacion() throws Exception {
        Usuario ajeno = alumno("Ajeno");
        Curso curso = curso("EN_CURSO", "VIRTUAL", "320.00");

        mvc.perform(get("/api/app/cursos/" + curso.getId() + "/certificacion").with(comoAlumno(ajeno.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No tienes una matrícula en este curso."));
    }

    @Test
    void conRequisitosPendientesLaCertificacionLoExplicaYNoSePuedeEmitir() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoSoloProgreso(alumno, new Leccion[1]);

        mvc.perform(get("/api/app/cursos/" + curso.getId() + "/certificacion").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REQUISITOS"))
                .andExpect(jsonPath("$.condiciones[0].codigo").value("PROGRESO"))
                .andExpect(jsonPath("$.condiciones[0].cumplida").value(false))
                .andExpect(jsonPath("$.puedeGenerarAhora").value(false))
                .andExpect(jsonPath("$.certificado").doesNotExist());

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/certificacion/emitir")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest());
        assertEquals(0, certificados.count());
    }

    @Test
    void alCumplirLosRequisitosSolicitaConfirmarLosDatosAntesDeEmitir() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] leccion = new Leccion[1];
        Curso curso = cursoSoloProgreso(alumno, leccion);
        completar(alumno, leccion[0]);

        mvc.perform(get("/api/app/cursos/" + curso.getId() + "/certificacion").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_DATOS"))
                .andExpect(jsonPath("$.puedeConfirmarDatos").value(true))
                .andExpect(jsonPath("$.datos.confirmados").value(false));

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/certificacion/emitir")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Confirma tus nombres y apellidos tal como quieres que aparezcan en tu certificado."));
    }

    @Test
    void confirmarDatosIncompletosSeRechazaConErroresPorCampo() throws Exception {
        Usuario alumno = alumno("Ana");
        Curso curso = cursoSoloProgreso(alumno, new Leccion[1]);

        mvc.perform(put("/api/app/cursos/" + curso.getId() + "/certificacion/datos")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\" \",\"apellidoPaterno\":\"Pérez\",\"apellidoMaterno\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void confirmadosLosDatosElAlumnoEmiteSuCertificadoYQuedaGuardado() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] leccion = new Leccion[1];
        Curso curso = cursoSoloProgreso(alumno, leccion);
        completar(alumno, leccion[0]);

        mvc.perform(put("/api/app/cursos/" + curso.getId() + "/certificacion/datos")
                        .with(comoAlumno(alumno.getId())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO_PARA_EMITIR"))
                .andExpect(jsonPath("$.puedeGenerarAhora").value(true));

        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/certificacion/emitir")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.certificado.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.certificado.nivel").value("NORMAL"))
                .andExpect(jsonPath("$.certificado.nombresImpresos").value("Ana María Pérez Gómez"));

        Certificado guardado = certificados.findAll().getFirst();
        assertEquals("SOLICITADA", guardado.getTipoEmision());
        assertTrue(guardado.getCodigo().startsWith("ESEJUR-"));

        // Emitir de nuevo no genera un segundo certificado.
        mvc.perform(post("/api/app/cursos/" + curso.getId() + "/certificacion/emitir")
                        .with(comoAlumno(alumno.getId())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tu certificado ya fue emitido."));
        assertEquals(1, certificados.count());
    }

    @Test
    void elCertificadoEmitidoApareceEnMisCertificadosYSoloSuDuenioLoDescarga() throws Exception {
        Usuario alumno = alumno("Ana");
        Usuario otro = alumno("Otro");
        Leccion[] leccion = new Leccion[1];
        Curso curso = cursoSoloProgreso(alumno, leccion);
        String codigo = emitirCertificado(alumno, curso, leccion[0]);

        mvc.perform(get("/api/app/certificados").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value(codigo))
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$[0].descargaUrl").value("/api/app/certificados/" + codigo + "/descargar"));

        mvc.perform(get("/api/app/certificados").with(comoAlumno(otro.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/app/certificados/" + codigo + "/descargar").with(comoAlumno(alumno.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(get("/api/app/certificados/" + codigo + "/descargar").with(comoAlumno(otro.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void cualquierPersonaVerificaUnCertificadoVigenteSinIniciarSesionYSoloVeDatosPublicos() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] leccion = new Leccion[1];
        Curso curso = cursoSoloProgreso(alumno, leccion);
        String codigo = emitirCertificado(alumno, curso, leccion[0]);

        mvc.perform(get("/api/publico/certificados/" + codigo.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.titular").value("Ana María Pérez Gómez"))
                .andExpect(jsonPath("$.curso").value(curso.getTitulo()))
                .andExpect(jsonPath("$.nota").doesNotExist())
                .andExpect(jsonPath("$.correo").doesNotExist())
                .andExpect(jsonPath("$.archivoPdfUrl").doesNotExist());
    }

    @Test
    void unCertificadoAnuladoSeVerificaComoNoValidoYYaNoSePuedeDescargar() throws Exception {
        Usuario alumno = alumno("Ana");
        Leccion[] leccion = new Leccion[1];
        Curso curso = cursoSoloProgreso(alumno, leccion);
        String codigo = emitirCertificado(alumno, curso, leccion[0]);
        Certificado certificado = certificados.findByCodigo(codigo).orElseThrow();
        certificado.setEstado(Certificado.ANULADO);
        certificado.setAnuladoEn(Instant.parse("2026-09-20T00:00:00Z"));
        certificado.setMotivoAnulacion("Datos erróneos");
        certificados.saveAndFlush(certificado);

        mvc.perform(get("/api/publico/certificados/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.motivoAnulacion").doesNotExist());

        mvc.perform(get("/api/app/certificados").with(comoAlumno(alumno.getId())))
                .andExpect(jsonPath("$[0].estado").value("ANULADO"))
                .andExpect(jsonPath("$[0].descargaUrl").doesNotExist());

        mvc.perform(get("/api/app/certificados/" + codigo + "/descargar").with(comoAlumno(alumno.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este certificado fue anulado y ya no se puede descargar."));
    }

    @Test
    void unCodigoInexistenteSoloRespondeCertificadoNoEncontrado() throws Exception {
        mvc.perform(get("/api/publico/certificados/ESEJUR-2026-NOEXISTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Certificado no encontrado."));
    }
}
