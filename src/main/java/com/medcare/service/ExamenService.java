package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.model.Examen;
import com.medcare.repository.ExamenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Exámenes adjuntos a una cita: solo PDF, PNG o JPG de hasta 3 MB; el tipo se detecta por el contenido, no por el nombre. */
@Service
public class ExamenService {

    public static final int MAX_BYTES = 3_000_000;
    public static final int MAX_POR_CITA = 10;

    private final ExamenRepository repo;
    private final ActividadService actividad;

    public ExamenService(ExamenRepository repo, ActividadService actividad) { this.repo = repo; this.actividad = actividad; }

    @Transactional
    public Examen subir(Cita cita, MultipartFile archivo, String descripcion, String subidoPor) {
        if (cita.isCancelada()) throw new ReglaNegocioException("No se pueden adjuntar archivos a una cita cancelada.");
        if (archivo == null || archivo.isEmpty()) throw new ReglaNegocioException("Elige un archivo para subir.");
        if (archivo.getSize() > MAX_BYTES) throw new ReglaNegocioException("El archivo pesa demasiado. El máximo es 3 MB.");
        if (repo.countByCitaId(cita.getId()) >= MAX_POR_CITA) throw new ReglaNegocioException("Una cita admite hasta " + MAX_POR_CITA + " archivos.");
        byte[] datos;
        try { datos = archivo.getBytes(); } catch (IOException e) { throw new ReglaNegocioException("No se pudo leer el archivo."); }
        String tipo = detectarTipo(datos);
        if (tipo == null) throw new ReglaNegocioException("Solo se aceptan archivos PDF, PNG o JPG.");
        String desc = descripcion == null || descripcion.isBlank() ? null : recortar(descripcion.trim().replaceAll("[\\r\\n]+", " "), 100);
        Examen e = repo.save(new Examen(cita, limpiarNombre(archivo.getOriginalFilename(), tipo), desc, tipo, datos, subidoPor, false));
        actividad.registrar("Examen adjuntado", e.getNombre() + " en la cita " + cita.getId());
        return e;
    }

    public List<Examen> deCita(Long citaId) { return repo.findByCitaIdOrderByFechaAsc(citaId); }

    public Examen obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReglaNegocioException("No encontramos ese archivo."));
    }

    @Transactional
    public void borrar(Examen e) {
        repo.delete(e);
        actividad.registrar("Examen eliminado", e.getNombre() + " de la cita " + e.getCita().getId());
    }

    /** Devuelve application/pdf, image/png o image/jpeg según los primeros bytes; null si es otra cosa. */
    public static String detectarTipo(byte[] b) {
        if (b == null || b.length < 8) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    /** Quita rutas y caracteres raros del nombre y le pone la extensión que corresponde al tipo real. */
    public static String limpiarNombre(String original, String tipo) {
        String n = original == null ? "" : original;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        int punto = n.lastIndexOf('.');
        if (punto > 0) n = n.substring(0, punto);
        n = n.replaceAll("[^\\p{L}\\p{N} ._-]", "").trim();
        if (n.isEmpty()) n = "examen";
        if (n.length() > 60) n = n.substring(0, 60);
        String ext = "application/pdf".equals(tipo) ? ".pdf" : "image/png".equals(tipo) ? ".png" : ".jpg";
        return n + ext;
    }

    private static String recortar(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
}
