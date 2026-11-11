package com.medcare.repository;

import com.medcare.model.Espera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EsperaRepository extends JpaRepository<Espera, Long> {
    List<Espera> findByDoctorAndFechaAndAvisadaFalse(String doctor, LocalDate fecha);
    boolean existsByDoctorAndFechaAndDniAndAvisadaFalse(String doctor, LocalDate fecha, String dni);
    List<Espera> findAllByOrderByIdDesc();
}
