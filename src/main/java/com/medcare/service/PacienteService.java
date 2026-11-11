package com.medcare.service;

import com.medcare.model.Paciente;
import com.medcare.model.Usuario;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.PacienteRepository;
import com.medcare.repository.ResenaRepository;
import com.medcare.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class PacienteService implements iPacienteService {

    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final CitaRepository citas;
    private final ResenaRepository resenas;
    private final com.medcare.repository.ExamenRepository examenes;
    private final PasswordEncoder encoder;
    private final com.medcare.repository.MensajeRepository mensajes;
    private final com.medcare.repository.EncuestaRepository encuestas;

    public PacienteService(PacienteRepository pacientes, UsuarioRepository usuarios,
                           CitaRepository citas, ResenaRepository resenas, com.medcare.repository.ExamenRepository examenes, PasswordEncoder encoder,
                           com.medcare.repository.MensajeRepository mensajes, com.medcare.repository.EncuestaRepository encuestas) {
        this.examenes = examenes; this.mensajes = mensajes; this.encuestas = encuestas;
        this.pacientes = pacientes; this.usuarios = usuarios; this.citas = citas; this.resenas = resenas; this.encoder = encoder;
    }

    @Override public List<Paciente> listar(String q, String estado) { return pacientes.buscar(q, estado); }
    @Override public Optional<Paciente> listarId(Long id) { return pacientes.findById(id); }
    @Override public boolean existeDni(String dni) { return pacientes.existsByDni(dni); }
    @Override public Optional<Paciente> buscarPorDni(String dni) { return pacientes.findByDni(dni); }
    @Override public long total() { return pacientes.count(); }

    @Override
    @Transactional
    public void save(Paciente p) {
        if (p.getId() == null) {
            // Paciente nuevo: se crea también su acceso (usuario = DNI, clave inicial = DNI)
            Paciente nuevo = pacientes.save(p);
            Usuario acceso = new Usuario(nuevo.getDni(), encoder.encode(nuevo.getDni()), "PACIENTE", nuevo);
            acceso.setDebeCambiarClave(true);     // la clave inicial es el DNI: se pide cambiarla al ingresar
            usuarios.save(acceso);
        } else {
            // Edición: el DNI no se modifica porque es el nombre de usuario
            Paciente actual = pacientes.findById(p.getId()).orElseThrow();
            actual.setName(p.getName());
            actual.setPhone(p.getPhone());
            actual.setEmail(p.getEmail());
            actual.setVisit(p.getVisit());
            actual.setStatus(p.getStatus());
            pacientes.save(actual);
        }
    }

    /** Registro hecho por el propio paciente: elige su clave y no tiene que cambiarla. */
    @Transactional
    public void registrarPropio(Paciente p, String clave) {
        Paciente nuevo = pacientes.save(p);
        usuarios.save(new Usuario(nuevo.getDni(), encoder.encode(clave), "PACIENTE", nuevo));
    }

    @Override
    @Transactional
    public void actualizarFicha(Long id, String alergias, String antecedentes, Double peso, Integer talla) {
        Paciente p = pacientes.findById(id).orElseThrow();
        p.setAlergias(alergias);
        p.setAntecedentes(antecedentes);
        p.setPeso(peso != null && peso > 0 && peso < 500 ? peso : null);
        p.setTalla(talla != null && talla > 20 && talla < 260 ? talla : null);
        pacientes.save(p);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        resenas.deleteByCitaPacienteId(id);
        examenes.deleteByCitaPacienteId(id);
        mensajes.deleteByCitaPacienteId(id);
        encuestas.deleteByCitaPacienteId(id);
        citas.deleteByPacienteId(id);
        usuarios.deleteByPacienteId(id);
        pacientes.deleteById(id);
    }
}
