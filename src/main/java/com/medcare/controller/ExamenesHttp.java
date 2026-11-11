package com.medcare.controller;

import com.medcare.model.Examen;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta HTTP para ver un examen adjunto (el tipo viene del contenido, nunca del nombre). */
final class ExamenesHttp {
    private ExamenesHttp() {}

    static ResponseEntity<byte[]> respuesta(Examen e) {
        // "sandbox" bloquea el visor de PDF de Chrome, así que solo se aplica a las imágenes
        ResponseEntity.BodyBuilder r = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + e.getNombre().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store");
        if (e.isImagen()) r = r.header("Content-Security-Policy", "sandbox");
        return r.contentType(MediaType.parseMediaType(e.getTipo())).body(e.getContenido());
    }
}
