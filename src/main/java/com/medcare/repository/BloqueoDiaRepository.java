package com.medcare.repository;

import com.medcare.model.BloqueoDia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BloqueoDiaRepository extends JpaRepository<BloqueoDia, Long> {
    boolean existsByDoctorAndFecha(String doctor, LocalDate fecha);
    List<BloqueoDia> findByDoctorAndFechaGreaterThanEqualOrderByFecha(String doctor, LocalDate desde);
}
