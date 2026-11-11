package com.medcare.service;

import com.medcare.model.PlantillaReceta;
import com.medcare.repository.PlantillaRecetaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Plantillas de receta de cada doctor. */
@Service
public class PlantillaService {

    public static final int MAX_POR_DOCTOR = 30;
    private final PlantillaRecetaRepository repo;

    public PlantillaService(PlantillaRecetaRepository repo) { this.repo = repo; }

    public List<PlantillaReceta> deDoctor(String doctor) { return repo.findByDoctorOrderByTituloAsc(doctor); }

    public PlantillaReceta crear(String doctor, String titulo, String cie10, String diagnostico, String receta) {
        String t = titulo == null ? "" : titulo.trim();
        if (t.isEmpty()) throw new ReglaNegocioException("Ponle un nombre a la plantilla.");
        if (t.length() > 80) t = t.substring(0, 80);
        String r = receta == null ? "" : receta.trim();
        if (r.isEmpty()) throw new ReglaNegocioException("Escribe el texto de la receta.");
        if (cie10 != null && !cie10.isBlank() && !Cie10.formatoValido(cie10)) throw new ReglaNegocioException("El código CIE-10 no es válido (ejemplo: J02.9).");
        if (repo.countByDoctor(doctor) >= MAX_POR_DOCTOR) throw new ReglaNegocioException("Máximo " + MAX_POR_DOCTOR + " plantillas.");
        String d = diagnostico == null ? "" : diagnostico.trim();
        return repo.save(new PlantillaReceta(doctor, t,
                cie10 == null || cie10.isBlank() ? null : cie10.trim().toUpperCase(),
                d.isEmpty() ? null : (d.length() > 1000 ? d.substring(0, 1000) : d),
                r.length() > 2000 ? r.substring(0, 2000) : r));
    }

    /** Borra la plantilla solo si es de ese doctor. */
    public void borrar(Long id, String doctor) {
        repo.findById(id).filter(p -> p.getDoctor().equals(doctor)).ifPresent(repo::delete);
    }
}
