package com.medcare.service;

import com.medcare.model.Cita;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Genera un evento de calendario (.ics) para una cita, compatible con Google Calendar, Outlook y el calendario del celular. */
public final class Ics {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private Ics() {}

    public static byte[] evento(Cita c, int duracionMinutos) {
        LocalDateTime ini = c.getFecha().atTime(c.getHora());
        LocalDateTime fin = ini.plusMinutes(duracionMinutos);
        String lugar = c.isVirtual() ? "Videollamada" : "MedCare" + (c.getConsultorio() == null ? "" : " - Consultorio " + c.getConsultorio());
        String descripcion = c.getEspecialidad() + " con " + c.getDoctor()
                + (c.isVirtual() && c.getEnlaceVirtual() != null ? "\nEnlace: " + c.getEnlaceVirtual() : "")
                + "\nEstado: " + c.getEstado();
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//MedCare//Citas//ES\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:cita-").append(c.getId()).append("@medcare.local\r\n");
        sb.append("DTSTAMP:").append(LocalDateTime.now(ZoneOffset.UTC).format(UTC)).append("\r\n");
        sb.append("DTSTART;TZID=America/Lima:").append(ini.format(LOCAL)).append("\r\n");
        sb.append("DTEND;TZID=America/Lima:").append(fin.format(LOCAL)).append("\r\n");
        sb.append(linea("SUMMARY:", "Cita médica - " + c.getEspecialidad()));
        sb.append(linea("LOCATION:", lugar));
        sb.append(linea("DESCRIPTION:", descripcion));
        sb.append("BEGIN:VALARM\r\nTRIGGER:-PT2H\r\nACTION:DISPLAY\r\nDESCRIPTION:Cita médica en 2 horas\r\nEND:VALARM\r\n");
        sb.append("END:VEVENT\r\nEND:VCALENDAR\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Escapa los caracteres especiales de iCalendar (coma, punto y coma, barra) y parte las líneas largas (75 bytes). */
    static String linea(String nombre, String valor) {
        String v = valor.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
        String l = nombre + v;
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < l.length(); ) {
            int cp = l.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int b = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + b > 74) { out.append("\r\n "); bytes = 1; }
            out.append(ch); bytes += b; i += Character.charCount(cp);
        }
        return out.append("\r\n").toString();
    }
}
