package com.medcare.repository;

import com.medcare.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByActivoTrueOrderByNombre();
    List<Doctor> findAllByOrderByNombre();
    Optional<Doctor> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
}
