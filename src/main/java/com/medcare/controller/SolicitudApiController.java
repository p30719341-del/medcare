package com.medcare.controller;

import com.medcare.model.Solicitud;
import com.medcare.repository.SolicitudRepository;
import com.medcare.service.Catalogo;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * API del Laboratorio 12 (solicitudes de atención).
 *  GET  /api/solicitudes              -> últimas solicitudes (sin correo, nombre abreviado)
 *  POST /api/solicitudes              -> registra una solicitud (multipart/form-data, imagen opcional "evidencia")
 *  GET  /api/solicitudes/{id}/evidencia -> imagen adjunta
 * Los datos SE GUARDAN en la base de datos (no es una simulación).
 */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudApiController {

    static final long MAX_IMAGEN = 2L * 1024 * 1024;       // 2 MB
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String EMAIL = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private final SolicitudRepository repo;

    public SolicitudApiController(SolicitudRepository repo) { this.repo = repo; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> listar() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Solicitud s : repo.findTop12ByOrderByIdDesc()) out.add(dto(s));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> crear(@RequestParam(defaultValue = "") String nombre,
                                                      @RequestParam(defaultValue = "") String correo,
                                                      @RequestParam(defaultValue = "") String servicio,
                                                      @RequestParam(defaultValue = "") String descripcion,
                                                      @RequestParam(value = "evidencia", required = false) MultipartFile evidencia) {
        nombre = nombre.trim(); correo = correo.trim(); servicio = servicio.trim(); descripcion = descripcion.trim();
        Map<String, String> err = new LinkedHashMap<>();
        if (nombre.length() < 3 || nombre.length() > 80) err.put("nombre", "Escribe tu nombre completo (3 a 80 caracteres).");
        if (!correo.matches(EMAIL) || correo.length() > 100) err.put("correo", "Escribe un correo válido, por ejemplo nombre@correo.com.");
        final String srv = servicio;
        if (Catalogo.ESPECIALIDADES.stream().noneMatch(e -> e.getNombre().equals(srv)) && !"Otro".equals(srv))
            err.put("servicio", "Elige un tipo de servicio de la lista.");
        if (descripcion.length() < 10 || descripcion.length() > 500) err.put("descripcion", "Describe tu solicitud (10 a 500 caracteres).");

        byte[] bytes = null; String tipo = null;
        if (evidencia != null && !evidencia.isEmpty()) {
            if (evidencia.getSize() > MAX_IMAGEN) err.put("evidencia", "La imagen supera los 2 MB.");
            else {
                try {
                    bytes = evidencia.getBytes();
                    tipo = detectarTipo(bytes);      // por el contenido, no por el nombre
                    if (tipo == null) err.put("evidencia", "Solo se aceptan imágenes JPG o PNG.");
                } catch (IOException e) { err.put("evidencia", "No se pudo leer la imagen."); }
            }
        }
        if (!err.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mensaje", "Revisa los campos marcados.");
            body.put("errores", err);
            return ResponseEntity.badRequest().body(body);
        }

        Solicitud s = new Solicitud();
        s.setNombre(nombre); s.setCorreo(correo); s.setServicio(servicio); s.setDescripcion(descripcion);
        if (bytes != null) s.setImagen(limpiarNombre(evidencia.getOriginalFilename(), tipo), tipo, bytes);
        s = repo.save(s);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(s));
    }

    @GetMapping("/{id}/evidencia")
    public ResponseEntity<byte[]> evidencia(@PathVariable Long id) {
        return repo.findById(id).filter(Solicitud::tieneImagen)
                .map(s -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(s.getImagenTipo()))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.noStore())
                        .body(s.getImagen()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> dto(Solicitud s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("solicitante", abreviar(s.getNombre()));
        m.put("servicio", s.getServicio());
        m.put("descripcion", s.getDescripcion());
        m.put("estado", s.getEstado());
        m.put("fecha", s.getFecha().format(FMT));
        m.put("imagenUrl", s.tieneImagen() ? "/api/solicitudes/" + s.getId() + "/evidencia" : null);
        return m;
    }

    /** En el listado público solo se muestra "Ana P." (nunca el correo ni el nombre completo). */
    static String abreviar(String nombre) {
        String[] p = nombre.trim().split("\\s+");
        if (p.length == 1) return p[0];
        return p[0] + " " + Character.toUpperCase(p[p.length - 1].charAt(0)) + ".";
    }

    static String detectarTipo(byte[] b) {
        if (b.length < 4) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    private static String limpiarNombre(String original, String tipo) {
        String base = original == null ? "evidencia" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 60) base = base.substring(base.length() - 60);
        return base.isBlank() ? "evidencia" + ("image/png".equals(tipo) ? ".png" : ".jpg") : base;
    }
}
