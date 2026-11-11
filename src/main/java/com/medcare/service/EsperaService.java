package com.medcare.service;

import com.medcare.model.Espera;
import com.medcare.repository.EsperaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Lista de espera: quien no encontró horario pide que le avisen si se libera uno. */
@Service
public class EsperaService {

    private final EsperaRepository repo;
    private final NotificacionService notificaciones;

    public EsperaService(EsperaRepository repo, NotificacionService notificaciones) {
        this.repo = repo; this.notificaciones = notificaciones;
    }

    @Transactional
    public void anotar(String doctor, LocalDate fecha, String nombre, String dni, String telefono, String email) {
        if (repo.existsByDoctorAndFechaAndDniAndAvisadaFalse(doctor, fecha, dni)) {
            throw new ReglaNegocioException("Ya estás en la lista de espera de ese doctor para esa fecha.");
        }
        repo.save(new Espera(doctor, fecha, nombre.trim(), dni, telefono.trim(), email));
    }

    /** Se llama cuando se cancela o se mueve una cita: avisa a quienes esperaban ese doctor y fecha. */
    @Transactional
    public int avisar(String doctor, LocalDate fecha) {
        List<Espera> pendientes = repo.findByDoctorAndFechaAndAvisadaFalse(doctor, fecha);
        for (Espera e : pendientes) {
            notificaciones.esperaLiberada(e.getEmail(), e.getNombre(), doctor, fecha);
            e.setAvisada(true);
            repo.save(e);
        }
        return pendientes.size();
    }

    public List<Espera> todas() { return repo.findAllByOrderByIdDesc(); }

    @Transactional
    public void quitar(Long id) { repo.deleteById(id); }
}
