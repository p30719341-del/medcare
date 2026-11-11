package com.medcare.repository;

import com.medcare.model.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findTop12ByOrderByIdDesc();
}
