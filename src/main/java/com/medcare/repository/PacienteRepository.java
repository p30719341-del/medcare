package com.medcare.repository;

import com.medcare.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByDni(String dni);
    Optional<Paciente> findByDni(String dni);

    @Query("""
        select p from Paciente p
        where (:q = '' or lower(p.name) like lower(concat('%', :q, '%'))
               or p.dni like concat('%', :q, '%') or p.phone like concat('%', :q, '%'))
          and (:estado = 'Todos' or p.status = :estado)
        order by p.id desc
        """)
    List<Paciente> buscar(@Param("q") String q, @Param("estado") String estado);
}
