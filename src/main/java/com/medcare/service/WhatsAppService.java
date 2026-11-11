package com.medcare.service;

import com.medcare.model.Cita;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Recordatorios por WhatsApp con la API oficial (WhatsApp Business Cloud API de Meta).
 * Por defecto está APAGADO (medcare.whatsapp.enabled=false): solo escribe "[WHATSAPP SIMULADO]" en la consola.
 * Para enviar de verdad necesitas una cuenta de WhatsApp Business en Meta, un número, un token y una PLANTILLA
 * de mensaje aprobada con 4 variables: {{1}} nombre, {{2}} doctor, {{3}} fecha, {{4}} hora. Ver README.txt.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"));

    private final boolean activo;
    private final String token, telefonoId, plantilla, idioma;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public WhatsAppService(@Value("${medcare.whatsapp.enabled:false}") boolean activo,
                           @Value("${medcare.whatsapp.token:}") String token,
                           @Value("${medcare.whatsapp.phone-id:}") String telefonoId,
                           @Value("${medcare.whatsapp.template:recordatorio_cita}") String plantilla,
                           @Value("${medcare.whatsapp.lang:es}") String idioma) {
        this.activo = activo; this.token = token; this.telefonoId = telefonoId; this.plantilla = plantilla; this.idioma = idioma;
    }

    public boolean isActivo() { return activo; }
    public boolean isConfigurado() { return !token.isBlank() && !telefonoId.isBlank(); }

    /** Envía (o simula) el recordatorio. Nunca lanza errores: lo principal ya quedó guardado. */
    public void recordatorio(Cita c) {
        String numero = numero(c.getPaciente().getPhone());
        if (numero == null) return;
        String fecha = c.getFecha().format(FECHA), hora = c.getHora().toString();
        if (!activo) {
            log.info("[WHATSAPP SIMULADO] a {}: {} tiene cita con {} el {} a las {} h", numero, c.getPaciente().getName(), c.getDoctor(), fecha, hora);
            return;
        }
        if (!isConfigurado()) { log.warn("medcare.whatsapp.enabled=true pero faltan medcare.whatsapp.token y medcare.whatsapp.phone-id."); return; }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/v20.0/" + telefonoId + "/messages"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo(numero, c.getPaciente().getName(), c.getDoctor(), fecha, hora)))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) log.warn("WhatsApp respondió {}: {}", r.statusCode(), r.body());
        } catch (Exception e) {
            log.warn("No se pudo enviar el WhatsApp a {}: {}", numero, e.getMessage());
        }
    }

    /** JSON de un mensaje de plantilla con 4 parámetros de texto. */
    String cuerpo(String numero, String nombre, String doctor, String fecha, String hora) {
        StringBuilder sb = new StringBuilder("{\"messaging_product\":\"whatsapp\",\"to\":\"").append(json(numero))
                .append("\",\"type\":\"template\",\"template\":{\"name\":\"").append(json(plantilla))
                .append("\",\"language\":{\"code\":\"").append(json(idioma)).append("\"},\"components\":[{\"type\":\"body\",\"parameters\":[");
        String[] v = {nombre, doctor, fecha, hora};
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"type\":\"text\",\"text\":\"").append(json(v[i])).append("\"}");
        }
        return sb.append("]}]}}").toString();
    }

    /** 987654321 -> 51987654321 (Perú). Devuelve null si no parece un celular. */
    static String numero(String telefono) {
        if (telefono == null) return null;
        String d = telefono.replaceAll("\\D", "");
        if (d.length() == 9 && d.startsWith("9")) return "51" + d;
        if (d.length() == 11 && d.startsWith("519")) return d;
        return null;
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : (s == null ? "" : s).toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append(' '); break;
                default: if (ch < 0x20) sb.append(' '); else sb.append(ch);
            }
        }
        return sb.toString();
    }
}
