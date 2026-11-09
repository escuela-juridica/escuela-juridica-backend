package pe.edu.utp.escuela.app.integration;

public interface GoogleCredentialVerifierIntegration {
    ContextoGoogleRegistroIntegration.Identidad verificar(String credencial);
}
