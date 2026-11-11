package com.medcare.repository;

import com.medcare.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {
    boolean existsByCitaId(Long citaId);
    List<Encuesta> findByCitaPacienteId(Long pacienteId);
    void deleteByCitaPacienteId(Long pacienteId);

    /** Por doctor: [nombre, promedio atención, promedio puntualidad, promedio instalaciones, cantidad]. */
    @Query("select e.doctor, avg(e.atencion), avg(e.puntualidad), avg(e.instalaciones), count(e) from Encuesta e group by e.doctor")
    List<Object[]> resumenPorDoctor();
}
