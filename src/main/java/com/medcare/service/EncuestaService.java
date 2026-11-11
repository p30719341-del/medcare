package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.model.Encuesta;
import com.medcare.repository.EncuestaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Encuesta de satisfacción posterior a cada cita atendida. */
@Service
public class EncuestaService {

    private final EncuestaRepository repo;
    private final ActividadService actividad;

    public EncuestaService(EncuestaRepository repo, ActividadService actividad) { this.repo = repo; this.actividad = actividad; }

    public Encuesta guardar(Cita cita, int atencion, int puntualidad, int instalaciones, String sugerencia) {
        if (!cita.isAtendida()) throw new ReglaNegocioException("Solo puedes opinar sobre citas que ya fueron atendidas.");
        if (!nota(atencion) || !nota(puntualidad) || !nota(instalaciones)) throw new ReglaNegocioException("Califica los tres puntos de 1 a 5.");
        if (repo.existsByCitaId(cita.getId())) throw new ReglaNegocioException("Ya respondiste la encuesta de esta cita.");
        String s = sugerencia == null ? "" : sugerencia.trim();
        if (s.length() > 300) s = s.substring(0, 300);
        try {
            Encuesta e = repo.save(new Encuesta(cita, atencion, puntualidad, instalaciones, s.isEmpty() ? null : s));
            actividad.registrar(cita.getPaciente().getName(), "Encuesta respondida", "cita " + cita.getId());
            return e;
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException("Ya respondiste la encuesta de esta cita.");
        }
    }

    private static boolean nota(int n) { return n >= 1 && n <= 5; }
}
