package com.medcare.repository;

import com.medcare.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    boolean existsByDoctorId(Long doctorId);
    Optional<Usuario> findByTokenReset(String tokenReset);
    Optional<Usuario> findByPacienteEmailIgnoreCase(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    void deleteByPacienteId(Long pacienteId);
}
