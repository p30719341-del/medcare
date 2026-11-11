package com.medcare.service;

import com.medcare.model.Cita;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Correos de confirmación. Si medcare.mail.enabled=false (por defecto) el correo NO se envía:
 * se escribe en la consola para que puedas ver qué se enviaría. Para enviar de verdad, ver application.properties.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    private final ObjectProvider<JavaMailSender> mail;
    private final boolean activo;
    private final String remitente;
    private final String adminEmail;

    public NotificacionService(ObjectProvider<JavaMailSender> mail,
                               @Value("${medcare.mail.enabled:false}") boolean activo,
                               @Value("${medcare.mail.from:no-reply@medcare.local}") String remitente,
                               @Value("${medcare.admin.email:}") String adminEmail) {
        this.mail = mail; this.activo = activo; this.remitente = remitente; this.adminEmail = adminEmail;
    }

    public void reservaRecibida(Cita c) {
        enviar(c, "Recibimos tu solicitud de cita", "Recibimos tu solicitud. Queda pendiente hasta que la clínica la confirme; te avisaremos.");
    }

    public void citaConfirmada(Cita c) {
        enviar(c, "Tu cita fue confirmada", "Tu cita está confirmada. Te esperamos.");
    }

    public void citaCancelada(Cita c) {
        enviar(c, "Tu cita fue cancelada", "Tu cita fue cancelada. Puedes reservar otra cuando quieras en nuestra web.");
    }

    public void citaReprogramada(Cita c) {
        enviar(c, "Tu cita fue reprogramada", "Cambiaste tu cita. Queda pendiente de confirmación.");
    }

    /** Recordatorio 24 h antes (lo envía RecordatorioService). */
    public void recordatorio(Cita c) {
        enviar(c, "Recordatorio de tu cita", "Te recordamos tu cita de mañana. Si no puedes asistir, cancélala o reprográmala desde tu portal.");
    }

    /** Aviso al administrador: llegó una cita nueva. Va a medcare.admin.email si está configurado. */
    public void avisoAdmin(Cita c) {
        String destino = adminEmail;
        String cuerpo = "Nueva cita pendiente de confirmar:\n\n  Paciente: " + c.getPaciente().getName() + " (DNI " + c.getPaciente().getDni() + ")\n"
                + "  " + c.getEspecialidad() + " con " + c.getDoctor() + "\n  " + c.getFecha().format(FECHA) + ", " + c.getHora() + " h";
        if (destino == null || destino.isBlank()) { log.info("[AVISO ADMIN] {}", cuerpo.replace("\n", " | ")); return; }
        enviarCorreo(destino, "Nueva cita pendiente", cuerpo);
    }

    /** Se liberó un horario del doctor en una fecha donde la persona estaba en lista de espera. */
    public void esperaLiberada(String para, String nombre, String doctor, LocalDate fecha) {
        if (para == null || para.isBlank()) return;
        enviarCorreo(para, "Se liberó un horario",
                "Hola " + nombre + ",\n\nSe liberó un horario con " + doctor + " el " + fecha.format(FECHA)
                + ".\nEntra a nuestra web y resérvalo antes de que se ocupe.\n\nMedCare (maqueta académica, datos ficticios)");
    }

    public void claveRecuperacion(String para, String nombre, String enlace) {
        enviarCorreo(para, "Recupera tu contraseña",
                "Hola " + nombre + ",\n\nPara crear una nueva contraseña entra a este enlace (vence en 1 hora):\n" + enlace
                + "\n\nSi no lo pediste tú, ignora este mensaje.\n\nMedCare (maqueta académica, datos ficticios)");
    }

    private void enviar(Cita c, String asunto, String intro) {
        String para = c.getPaciente().getEmail();
        if (para == null || para.isBlank()) return;
        String cuerpo = "Hola " + c.getPaciente().getName() + ",\n\n" + intro + "\n\n"
                + (c.getFamiliar() == null ? "" : "  Paciente: " + c.getFamiliar() + "\n")
                + "  Especialidad: " + c.getEspecialidad() + "\n"
                + "  Doctor: " + c.getDoctor() + "\n"
                + "  Fecha: " + c.getFecha().format(FECHA) + "\n"
                + "  Hora: " + c.getHora() + " h\n"
                + (c.isVirtual() ? "  Modalidad: videollamada\n  Enlace: " + c.getEnlaceVirtual() + "\n"
                                  : (c.getConsultorio() == null ? "" : "  Consultorio: " + c.getConsultorio() + "\n"))
                + "  Estado: " + c.getEstado() + "\n\n"
                + "MedCare (maqueta académica, datos ficticios)";
        enviarCorreo(para, asunto, cuerpo);
    }

    public boolean isActivo() { return activo; }
    public boolean isServidorConfigurado() { return mail.getIfAvailable() != null; }
    public String getAdminEmail() { return adminEmail; }

    /** Correo de prueba desde el panel del sistema. Devuelve una frase para mostrar al admin. */
    public String correoDePrueba(String para) {
        if (para == null || para.isBlank()) return "Configura medcare.admin.email (o ADMIN_EMAIL) para recibir la prueba.";
        enviarCorreo(para, "Correo de prueba", "Si lees esto, el correo de MedCare funciona.\n\nMedCare (maqueta académica, datos ficticios)");
        if (!activo) return "Correo SIMULADO: revisa la consola del servidor. Para enviar de verdad activa medcare.mail.enabled=true y configura el SMTP.";
        if (mail.getIfAvailable() == null) return "Falta configurar el servidor SMTP (spring.mail.host).";
        return "Intentamos enviar la prueba a " + para + ". Si no llega en un minuto, revisa spam y los datos del SMTP en la consola.";
    }

    private void enviarCorreo(String para, String asunto, String cuerpo) {
        if (!activo) {
            log.info("[CORREO SIMULADO] Para: {} | Asunto: {}\n{}", para, asunto, cuerpo);
            return;
        }
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.warn("medcare.mail.enabled=true pero no hay servidor de correo configurado (spring.mail.host).");
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(remitente); m.setTo(para); m.setSubject("MedCare: " + asunto); m.setText(cuerpo);
            sender.send(m);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo a {}: {}", para, e.getMessage());   // lo principal ya quedó guardado
        }
    }
}
