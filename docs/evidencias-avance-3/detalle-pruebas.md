# Detalle de pruebas por archivo — Avance 3

Cada línea es una prueba ejecutada; el estado proviene del reporte de la ejecución.

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/AdministrarSesionesServiceTests.java`

HU-027 · Reprogramar/cancelar sesiones y corregir asistencia — 11/11 aprobadas

- ✔ unaSesionYaRealizadaNoSePuedeModificar
- ✔ cancelarDosVecesLaMismaSesionSeRechaza
- ✔ laHoraDeFinDebeSerPosteriorAlInicio
- ✔ reprogramarGuardaElNuevoHorarioConHistorialYAvisaALosAlumnosActivos
- ✔ reprogramarSinMotivoSeRechazaYNoCambiaNada
- ✔ soloUnAdministradorPuedeReprogramar
- ✔ siLaNuevaFechaSuperaElFinDelCursoSePideAmpliarloSinGuardarNada
- ✔ corregirLaAsistenciaConservaElValorAnteriorYReevaluaLaCertificacion
- ✔ corregirAsistenciaExigeMotivoYUnEstadoValido
- ✔ cancelarMarcaLaSesionConsevaElHistorialYAvisa
- ✔ listarAsistenciasEntregaElNombreDelAlumnoYSuEstado

## EP04 · `src/test/java/pe/edu/utp/escuela/app/controller/AulaHttpTests.java`

HU-022 a HU-026 · Aula, progreso, materiales y calendario por HTTP con H2 — 11/11 aprobadas

- ✔ sinSesionElAulaRespondeNoAutenticado
- ✔ conMatriculaActivaLaLeccionSinMaterialesRespondeUnaListaVacia
- ✔ unAdministradorNoPuedeEntrarAlAulaDeUnAlumno
- ✔ unaMatriculaPendienteDePagoNoAbreElAula
- ✔ unAlumnoSinMatriculaEnElCursoRecibeNoEncontrado
- ✔ sinMatriculaNoSeListanLosMaterialesDeUnaLeccion
- ✔ elCalendarioDeSesionesDeUnAlumnoSinMatriculasActivasVieneVacio
- ✔ unAlumnoMatriculadoRecibeSuTemarioConLaSegundaLeccionBloqueada
- ✔ completarLasLeccionesEnOrdenAvanzaElProgresoYSeGuardaEnLaBase
- ✔ elCalendarioRechazaUnRangoDeFechasInvertido
- ✔ elAvanceDeVideoConDatosInvalidosSeRechazaAntesDeTocarLaBase

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/AulaServiceTests.java`

HU-022 · Autorización para acceder al aula, temario, bloqueos y continuar — 10/10 aprobadas

- ✔ cursoCanceladoConservaLaConsultaDeQuienYaTeniaAcceso
- ✔ matriculaPendienteDePagoNoConcedeAcceso
- ✔ conTodoCompletadoElAulaReabreLaUltimaLeccion
- ✔ matriculaCanceladaInformaElMotivo
- ✔ matriculaActivaRecibeTemarioConEstadosYPuntoDeContinuacion
- ✔ sesionEnVivoRealizadaConGrabacionActivaExponeLaGrabacion
- ✔ unAdministradorNoEntraAlAulaDeUnAlumno
- ✔ sinMatriculaEnElCursoNoHayAula
- ✔ cuentaSinCorreoVerificadoDebeHabilitarseAntesDeEntrar
- ✔ matriculaVencidaNoPermiteEntrar

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/MaterialAlumnoServiceTests.java`

HU-023 · Bloqueo de materiales sin matrícula y entrega protegida — 8/8 aprobadas

- ✔ matriculaInactivaBloqueaElMaterial
- ✔ unMaterialExternoNoTieneContenidoParaTransmitir
- ✔ soloElAlumnoConsultaMateriales
- ✔ descargarExigeQueElMaterialPermitaDescarga
- ✔ sinMatriculaNoSeListanLosMateriales
- ✔ sinMatriculaNoSeTransmiteElContenidoAunqueSeConozcaLaUrl
- ✔ conMatriculaActivaElContenidoSeTransmiteDesdeElAlmacenamientoPrivado
- ✔ conMatriculaActivaSoloSeListanMaterialesActivosSinExponerLaRutaFisica

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/ProgresoServiceTests.java`

HU-024 · Registro de progreso (video, manual, asistencia) — 14/14 aprobadas

- ✔ elPorcentajeDeVideoNuncaRetrocedeSiElAlumnoRebobina
- ✔ alcanzarElUmbralDeVideoCompletaLaLeccionAutomaticamente
- ✔ unaSesionEnVivoSinAsistenciaNiGrabacionNoSePuedeCompletarAMano
- ✔ unEnlaceExternoNoPermiteRegistrarProgresoDeVideo
- ✔ soloElAlumnoRegistraProgreso
- ✔ noSePuedeCompletarUnaLeccionBloqueadaLlamandoDirectoALaApi
- ✔ bajoElUmbralSoloSeActualizaLaVisitaYLaLeccionSigueIncompleta
- ✔ sinMatriculaNoSeRegistraProgreso
- ✔ enUnCursoCanceladoLaConsultaSigueAbiertaPeroElProgresoYaNoSeRegistra
- ✔ completarManualRegistraLaLeccionYPideReevaluarLaCertificacion
- ✔ unaLeccionConVideoSubidoSoloSeCompletaViendoElVideo
- ✔ laPrimeraDuracionReportadaPorElNavegadorSeAdoptaComoOficial
- ✔ laAsistenciaPresenteCompletaLaSesionPeroLaAusenciaNo
- ✔ unCheckYaCompletadoNoSeTocaNiSeVuelveAReevaluar

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/RutaAcademicaServiceTests.java`

HU-025 · Reglas de ruta académica (secuencia, exámenes bloqueantes) — 10/10 aprobadas

- ✔ alCompletarLaLeccionPreviaSeLiberaLaSiguiente
- ✔ unExamenDeModuloBloqueanteSinAprobarFrenaElSiguienteModulo
- ✔ sinSecuenciaObligatoriaNingunaLeccionQuedaBloqueada
- ✔ conSecuenciaObligatoriaCadaLeccionExigeLaAnteriorCompletada
- ✔ unaSesionCanceladaLiberaLaSecuenciaYNoCuentaComoPrevia
- ✔ elExamenFinalExigeTodosLosModulosCompletados
- ✔ aprobarElExamenBloqueanteLiberaElSiguienteModulo
- ✔ laPracticaSiempreEsIlimitadaYNoBloquea
- ✔ unExamenDeModuloSeHabilitaSoloAlCompletarLasLeccionesDeSuModulo
- ✔ agotarLosIntentosSinAprobarImpideReintentarYExigeUnaExcepcionDeAdministracion

## EP04 · `src/test/java/pe/edu/utp/escuela/app/service/SesionAlumnoServiceTests.java`

HU-026 · Sesiones en vivo: ingreso, calendario, asistencia — 14/14 aprobadas

- ✔ elRecordatorioDelDiaAnteriorSeEnviaUnaSolaVezPorSesionYAlumno
- ✔ unaSesionCanceladaNoAdmiteIngreso
- ✔ soloUnAlumnoIngresaALaSesion
- ✔ elPorcentajeDeAsistenciaCuentaPresentesYJustificadasSobreLasSesionesElegibles
- ✔ sinSesionesElegiblesLaAsistenciaNoAplica
- ✔ ingresarDentroDeLaVentanaRegistraAsistenciaPresenteYEntregaElEnlace
- ✔ sinMatriculaNoSePuedeIngresarALaSesion
- ✔ ingresarDosVecesNoDuplicaLaAsistencia
- ✔ elCalendarioMuestraElEnlaceSoloDuranteLaVentanaDeLaSesion
- ✔ antesDeLaHoraDeInicioNoSePuedeIngresar
- ✔ elCalendarioRechazaUnRangoInvertido
- ✔ unaSesionAnteriorALaMatriculaNoGeneraAusencias
- ✔ sinMatriculasActivasElCalendarioVieneVacio
- ✔ alTerminarLaSesionQuienNoIngresoQuedaAusenteYNoCompletaLaLeccion

## EP05 · `src/test/java/pe/edu/utp/escuela/app/service/AvanceAcademicoServiceTests.java`

HU-025 / HU-032 · Cálculo de condiciones de certificación — 10/10 aprobadas

- ✔ conProgresoBajoElMinimoLaFraseDiceCuantoFalta
- ✔ enUnCursoVirtualLaAsistenciaNuncaSeCalculaAunqueLaReglaLaPida
- ✔ enUnCursoEnVivoLaAsistenciaMinimaEsUnaCondicion
- ✔ laNotaFinalEsElPromedioDeLasMejoresNotasDeLosExamenesCalificados
- ✔ unExamenSinAprobarImpideCumplirYLaFraseLoIndica
- ✔ sinSesionesElegiblesLaAsistenciaNoAplicaPeroNuncaCuentaComoCumplida
- ✔ unaNotaPromedioPorDebajoDelMinimoNoCumpleAunqueApruebeElExamen
- ✔ lasSesionesCanceladasYLasLeccionesOpcionalesNoCuentanParaElPorcentaje
- ✔ unExamenConRespuestaAbiertaSinCalificarBloqueaLaCertificacion
- ✔ conProgresoExamenYNotaSuficientesElAlumnoCumpleTodo

## EP05 · `src/test/java/pe/edu/utp/escuela/app/controller/CertificacionHttpTests.java`

HU-032 a HU-034 · Certificación, emisión, mis certificados, verificación pública — 10/10 aprobadas

- ✔ elCertificadoEmitidoApareceEnMisCertificadosYSoloSuDuenioLoDescarga
- ✔ sinMatriculaNoHayCertificacion
- ✔ alCumplirLosRequisitosSolicitaConfirmarLosDatosAntesDeEmitir
- ✔ confirmadosLosDatosElAlumnoEmiteSuCertificadoYQuedaGuardado
- ✔ unCertificadoAnuladoSeVerificaComoNoValidoYYaNoSePuedeDescargar
- ✔ confirmarDatosIncompletosSeRechazaConErroresPorCampo
- ✔ conRequisitosPendientesLaCertificacionLoExplicaYNoSePuedeEmitir
- ✔ unCodigoInexistenteSoloRespondeCertificadoNoEncontrado
- ✔ sinSesionNoSeConsultaLaCertificacion
- ✔ cualquierPersonaVerificaUnCertificadoVigenteSinIniciarSesionYSoloVeDatosPublicos

## EP05 · `src/test/java/pe/edu/utp/escuela/app/controller/ExamenHttpTests.java`

HU-028 / HU-029 / HU-030 · Examen automático, respuesta abierta y revisión — 11/11 aprobadas

- ✔ siSeAgotaElTiempoElIntentoSeEnviaSoloConLoGuardadoYNoAdmiteCambios
- ✔ unaRespuestaIncorrectaBajaLaNotaPorDebajoDelMinimoYElAlumnoNoAprueba
- ✔ unAlternativaDeOtraPreguntaSeRechazaAlGuardarLaRespuesta
- ✔ unAlumnoSinMatriculaNoVeLaFichaDelExamen
- ✔ sinSesionNoSePuedeIniciarUnExamen
- ✔ unaRespuestaAbiertaDejaElIntentoEnRevisionYElAdministradorLoCalifica
- ✔ alAgotarLosIntentosSinAprobarNoSePermiteReintentar
- ✔ respuestasCorrectasDanNotaVeinteYApruebanElExamen
- ✔ laNotaSeCalculaSobreVeinteConDosDecimales
- ✔ unExamenSinPreguntasNoSePuedeIniciar
- ✔ otroAlumnoNoPuedeConsultarElIntentoAjeno

## EP06 · `src/test/java/pe/edu/utp/escuela/app/controller/CancelacionYExcepcionesPagoHttpTests.java`

HU-037 / HU-038 · Cancelación de curso y excepciones de pago — 11/11 aprobadas

- ✔ reenviarAvisosDeUnCursoNoCanceladoSeRechaza
- ✔ elImpactoCuentaLosAlumnosConAccesoLosPendientesDePagoYLasSesionesFuturas
- ✔ unCursoCerradoOYaCanceladoNoPuedeCancelarse
- ✔ soloUnAdministradorConsultaElImpactoDeCancelarUnCurso
- ✔ unPagoAprobadoDespuesDeCancelarElCursoSeRegistraSinAccesoYQuedaParaAtencion
- ✔ cancelarSinMotivoSuficienteOSinConfirmarSeRechaza
- ✔ siFallaElCorreoElAvisoQuedaConErrorYSePuedeReenviarSinDuplicarLosEnviados
- ✔ elFiltroDeExcepcionesRechazaUnTipoDesconocido
- ✔ atenderUnaExcepcionInexistenteRespondeNoEncontrado
- ✔ elAdministradorListaYAtiendeLaExcepcionUnaSolaVez
- ✔ cancelarUnCursoLoMarcaCanceladoAvisaAlAlumnoYCancelaSusSesionesFuturas

## EP06 · `src/test/java/pe/edu/utp/escuela/app/controller/DashboardYReportePagosHttpTests.java`

HU-042 / HU-046 · Dashboard y reporte de pagos — 10/10 aprobadas

- ✔ unRangoMayorAUnMesAgrupaLaSeriePorMes
- ✔ elDashboardRechazaUnRangoInvertidoYUnaModalidadInvalida
- ✔ soloAdministracionConsultaElReporteDePagos
- ✔ losMediosDePagoRegistradosAlimentanElFiltro
- ✔ elTotalSoloSumaLosPagosConfirmados
- ✔ elDashboardSoloEsParaAdministracion
- ✔ elReporteRechazaFiltrosInvalidos
- ✔ elReporteSeExportaAExcelConLosMismosFiltros
- ✔ elReporteListaLosPagosYFiltraPorEstado
- ✔ elDashboardContabilizaMatriculasPorCursoFormaDeIngresoYPagosConfirmados

## EP06 · `src/test/java/pe/edu/utp/escuela/app/controller/PagoHttpTests.java`

HU-047 · Pagos Culqi: aprobado, pendiente, rechazado, error, reintento, webhook — 19/19 aprobadas

- ✔ unCursoCanceladoYaNoAdmitePagos
- ✔ unPagoAprobadoActivaLaMatriculaEmiteConstanciaYEnviaElCorreo
- ✔ siElAlumnoConfirmaUnImporteDistintoDelVigenteElPagoNoSeInicia
- ✔ unEventoDelWebhookVerificadoContraCulqiAplicaElResultadoUnaSolaVez
- ✔ elPagoDeUnAlumnoNoEsVisibleParaOtro
- ✔ unCursoGratuitoNoRequierePago
- ✔ repetirElTokenDeUnPagoYaResueltoNoCobraDeNuevoNiDuplicaLaConstancia
- ✔ sinImporteConfirmadoLaPeticionSeRechazaPorValidacion
- ✔ elWebhookSinCredencialesOConClaveIncorrectaSeRechaza
- ✔ consultarUnPagoPendienteReflejaLaOrdenPagadaEnCulqi
- ✔ siElCursoSeCancelaAntesDeCobrarElTokenSeRechaza
- ✔ unPagoPendienteNoConcedeAccesoNiReservaCupo
- ✔ conUnIntentoPendienteIniciarDeNuevoLoReanudaSinCrearOtro
- ✔ unPagoConErrorTampocoActivaLaMatricula
- ✔ unEventoDeUnaOrdenQueNoEsNuestraSeIgnora
- ✔ unaMatriculaYaActivaNoPuedePagarOtraVez
- ✔ unPagoRechazadoNoActivaLaMatriculaYPermiteReintentarConUnIntentoNuevo
- ✔ iniciarPagoCreaUnIntentoPendienteYUnaMatriculaPendienteDePagoSinAcceso
- ✔ sinSesionNoSeIniciaUnPago

## EP06 · `src/test/java/pe/edu/utp/escuela/app/controller/ReclamacionHttpTests.java`

HU-039 / HU-040 · Libro de reclamaciones y atención administrativa — 9/9 aprobadas

- ✔ unTipoDeReclamoInvalidoSeRechaza
- ✔ cualquierPersonaPresentaUnReclamoSinSesionYRecibeSuConstancia
- ✔ sinSesionNoSePrecargaElFormulario
- ✔ sinLaDeclaracionJuradaNiElDetalleSuficienteElFormularioSeRechazaConErroresPorCampo
- ✔ soloUnAdministradorVeLaBandejaDeReclamos
- ✔ unDniQueNoTieneOchoDigitosSeRechazaYNoSeRegistraElCaso
- ✔ elAdministradorVeElReclamoPendienteYLoResponde
- ✔ siFallaElCorreoElReclamoIgualQuedaRegistradoYSeMarcaLaConstanciaConError
- ✔ conSesionElReclamoQuedaLigadoALaCuentaYSePrecargaSuInformacion
