package com.medcare.repository;

import com.medcare.model.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    List<Actividad> findTop200ByOrderByIdDesc();
    List<Actividad> findTop200ByAccionOrderByIdDesc(String accion);
}
