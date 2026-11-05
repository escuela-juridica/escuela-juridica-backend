package pe.edu.utp.escuela.app.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Interoperabilidad SMTP contra un servidor aislado: no envía mensajes a Internet. */
class SmtpSimuladoIntegrationTests {

    private GreenMail smtp;

    @BeforeEach
    void iniciarSmtpSimulado() {
        smtp = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        smtp.start();
    }

    @AfterEach
    void detenerSmtpSimulado() {
        smtp.stop();
    }

    @Test
    void entregaCorreoHtmlCompletoAUnServidorSmtpRealEnMemoria() throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(smtp.getSmtp().getPort());

        SmtpMailService servicio = new SmtpMailService(
                sender, new MailTemplateRenderer(), "notificaciones@esejur.pe");
        servicio.sendHtml(HtmlMailMessage.to(
                "alumno@example.com",
                "Matrícula confirmada",
                "mail/test-mail.html",
                Map.of(
                        "name", "Ana Pérez",
                        "message", "Tu matrícula fue registrada correctamente.",
                        "sentAt", "04/11/2026 11:20 PET",
                        "year", "2026")));

        assertTrue(smtp.waitForIncomingEmail(5_000, 1));
        MimeMessage recibido = smtp.getReceivedMessages()[0];
        assertEquals("Matrícula confirmada", recibido.getSubject());
        assertEquals("alumno@example.com", recibido.getAllRecipients()[0].toString());
        assertTrue(recibido.getContentType().toLowerCase().contains("text/html"));
        assertTrue(recibido.getContent().toString().contains("matrícula fue registrada"));
    }
}
