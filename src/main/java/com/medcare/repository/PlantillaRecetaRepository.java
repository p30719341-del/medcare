package com.medcare.repository;

import com.medcare.model.PlantillaReceta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantillaRecetaRepository extends JpaRepository<PlantillaReceta, Long> {
    List<PlantillaReceta> findByDoctorOrderByTituloAsc(String doctor);
    long countByDoctor(String doctor);
}
