package com.medcare.service;

import com.medcare.model.Paciente;
import java.util.List;
import java.util.Optional;

public interface iPacienteService {
    List<Paciente> listar(String q, String estado);
    Optional<Paciente> listarId(Long id);
    void save(Paciente p);
    void delete(Long id);
    boolean existeDni(String dni);
    Optional<Paciente> buscarPorDni(String dni);
    long total();
    void actualizarFicha(Long id, String alergias, String antecedentes, Double peso, Integer talla);
}
