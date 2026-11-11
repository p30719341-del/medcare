package com.medcare.repository;

import com.medcare.model.Examen;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamenRepository extends JpaRepository<Examen, Long> {
    List<Examen> findByCitaIdOrderByFechaAsc(Long citaId);
    long countByCitaId(Long citaId);
    void deleteByCitaPacienteId(Long pacienteId);
}
