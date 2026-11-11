package com.medcare.repository;

import com.medcare.model.Resena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ResenaRepository extends JpaRepository<Resena, Long> {
    boolean existsByCitaId(Long citaId);
    List<Resena> findByCitaPacienteId(Long pacienteId);
    List<Resena> findTop12ByOrderByIdDesc();
    void deleteByCitaPacienteId(Long pacienteId);

    /** Por doctor: [nombre, promedio, cantidad]. */
    @Query("select r.doctor, avg(r.estrellas), count(r) from Resena r group by r.doctor")
    List<Object[]> resumenPorDoctor();
}
