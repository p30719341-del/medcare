package com.medcare.repository;

import com.medcare.model.Cita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Long> {
    List<Cita> findByFechaAndEstadoNotOrderByHora(LocalDate fecha, String estado);
    List<Cita> findByPacienteIdOrderByFechaAscHoraAsc(Long pacienteId);
    List<Cita> findByPacienteIdAndEstadoOrderByFechaDescHoraDesc(Long pacienteId, String estado);
    List<Cita> findByDoctorAndFecha(String doctor, LocalDate fecha);
    List<Cita> findByFechaGreaterThanAndEstadoNotOrderByFechaAscHoraAsc(LocalDate fecha, String estado);
    List<Cita> findByFechaBetweenAndEstadoNotOrderByFechaAscHoraAsc(LocalDate desde, LocalDate hasta, String estado);
    List<Cita> findByDoctorAndFechaBetweenAndEstadoNotOrderByFechaAscHoraAsc(String doctor, LocalDate desde, LocalDate hasta, String estado);
    List<Cita> findByFechaBetweenAndEstadoIn(LocalDate desde, LocalDate hasta, List<String> estados);
    List<Cita> findByFechaAndHora(LocalDate fecha, java.time.LocalTime hora);
    List<Cita> findByCodigoReceta(String codigo);
    List<Cita> findAllByOrderByFechaAscHoraAsc();
    long countByEstado(String estado);
    void deleteByPacienteId(Long pacienteId);

    /** Filtros de la página "Citas médicas". Los parámetros nunca son null (ver AdminCitasController). */
    @Query("""
        select c from Cita c
        where c.fecha >= :desde and c.fecha <= :hasta
          and (:doctor = '' or c.doctor = :doctor)
          and (:estado = 'Todos' or c.estado = :estado)
          and (:q = '' or lower(c.paciente.name) like lower(concat('%', :q, '%')) or c.paciente.dni like concat('%', :q, '%'))
        order by c.fecha asc, c.hora asc
        """)
    List<Cita> filtrar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                       @Param("doctor") String doctor, @Param("estado") String estado, @Param("q") String q);
}
