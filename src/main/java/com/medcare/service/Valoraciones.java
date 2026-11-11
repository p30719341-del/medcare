package com.medcare.service;

import com.medcare.model.Resena;
import com.medcare.repository.ResenaRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Promedios de estrellas por doctor y últimas reseñas con comentario. */
@Service
public class Valoraciones {

    public static class Rating {
        private final double promedio;
        private final long cantidad;
        public Rating(double promedio, long cantidad) { this.promedio = promedio; this.cantidad = cantidad; }
        public double getPromedio() { return promedio; }
        public long getCantidad() { return cantidad; }
        /** Promedio con un decimal y coma, por ejemplo "4,5". */
        public String getTexto() { return String.format(java.util.Locale.forLanguageTag("es-PE"), "%.1f", promedio); }
        /** Estrellas llenas redondeadas, para dibujar ★★★★☆. */
        /** Por ejemplo "★★★★☆". */
        public String getEstrellas() { return "★".repeat(getLlenas()) + "☆".repeat(5 - getLlenas()); }
        public int getLlenas() { return (int) Math.round(promedio); }
    }

    private final ResenaRepository resenas;

    public Valoraciones(ResenaRepository resenas) { this.resenas = resenas; }

    public Map<String, Rating> porDoctor() {
        Map<String, Rating> m = new HashMap<>();
        for (Object[] f : resenas.resumenPorDoctor()) {
            m.put((String) f[0], new Rating(((Number) f[1]).doubleValue(), ((Number) f[2]).longValue()));
        }
        return m;
    }

    /** Reseñas recientes que incluyen comentario. */
    public List<Resena> ultimasConComentario(int max) {
        return resenas.findTop12ByOrderByIdDesc().stream()
                .filter(r -> r.getComentario() != null && !r.getComentario().isBlank())
                .limit(max).toList();
    }
}
