package pe.edu.utp.escuela.app.integration;


/**
 * Contrato de verificación; actualmente lo implementa el desafío nativo académico.
 * El adaptador valida y consume la evidencia. No basta con comprobar que existe.
 */
public interface VerificadorAntiRobotIntegration {
    void verificar(String evidencia);
}
