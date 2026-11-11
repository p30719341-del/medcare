package com.medcare.service;

import com.medcare.model.Cita;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Enlaces de WhatsApp con el mensaje ya escrito (no requiere ninguna cuenta ni API). Uso: ${@wa.recordatorio(c)} */
@Component("wa")
public class Wa {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"));
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Abre el chat del paciente con un recordatorio de su cita. */
    public String recordatorio(Cita c) {
        String msg = "Hola " + primerNombre(c.getPaciente().getName()) + ", te recordamos tu cita en MedCare: "
                + c.getEspecialidad() + " con " + c.getDoctor() + ", el " + c.getFecha().format(FECHA)
                + " a las " + c.getHora().format(HORA) + ". Si no puedes asistir, avísanos para reprogramarla.";
        return "https://wa.me/" + numero(c.getPaciente().getPhone()) + "?text=" + codificar(msg);
    }

    /** Permite al paciente enviarse (o compartir) los datos de su cita. */
    public String compartir(Cita c) {
        String msg = "Mi cita en MedCare: " + c.getEspecialidad() + " con " + c.getDoctor() + ", el "
                + c.getFecha().format(FECHA) + " a las " + c.getHora().format(HORA) + ".";
        return "https://wa.me/?text=" + codificar(msg);
    }

    /** Solo dígitos; si tiene 9 se asume Perú y se antepone 51. */
    static String numero(String telefono) {
        String d = telefono == null ? "" : telefono.replaceAll("\\D", "");
        return d.length() == 9 ? "51" + d : d;
    }

    private static String primerNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static String codificar(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
