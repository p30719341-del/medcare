package com.medcare.service;

import com.medcare.model.BloqueoDia;
import com.medcare.model.Cita;
import com.medcare.repository.BloqueoDiaRepository;
import com.medcare.repository.CitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Días en que un doctor no atiende (vacaciones, emergencias). */
@Service
public class BloqueoService {

    private final BloqueoDiaRepository repo;
    private final CitaRepository citas;
    private final ActividadService actividad;

    public BloqueoService(BloqueoDiaRepository repo, CitaRepository citas, ActividadService actividad) {
        this.repo = repo; this.citas = citas; this.actividad = actividad;
    }

    public List<BloqueoDia> proximos(String doctor) {
        return repo.findByDoctorAndFechaGreaterThanEqualOrderByFecha(doctor, LocalDate.now());
    }

    /** Bloquea el día y devuelve cuántas citas activas ya había ese día (siguen ahí: hay que cancelarlas o reprogramarlas). */
    @Transactional
    public long bloquear(String doctor, LocalDate fecha, String motivo) {
        if (fecha.isBefore(LocalDate.now())) throw new ReglaNegocioException("Elige una fecha de hoy en adelante.");
        if (repo.existsByDoctorAndFecha(doctor, fecha)) throw new ReglaNegocioException("Ese día ya está bloqueado.");
        String m = motivo == null ? "" : motivo.trim();
        repo.save(new BloqueoDia(doctor, fecha, m.length() > 120 ? m.substring(0, 120) : m));
        actividad.registrar("Día bloqueado", doctor + " · " + fecha + (m.isEmpty() ? "" : " · " + m));
        return citas.findByDoctorAndFecha(doctor, fecha).stream().filter(Cita::isActiva).count();
    }

    @Transactional
    public void desbloquear(Long id, String doctor) {
        repo.findById(id).filter(b -> b.getDoctor().equals(doctor)).ifPresent(b -> {
            repo.delete(b);
            actividad.registrar("Día desbloqueado", doctor + " · " + b.getFecha());
        });
    }
}
