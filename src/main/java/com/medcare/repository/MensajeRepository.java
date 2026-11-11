package com.medcare.repository;

import com.medcare.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByCitaIdOrderByIdAsc(Long citaId);
    long countByCitaId(Long citaId);
    void deleteByCitaPacienteId(Long pacienteId);
}
