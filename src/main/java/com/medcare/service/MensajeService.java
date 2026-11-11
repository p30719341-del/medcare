package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.model.Mensaje;
import com.medcare.repository.MensajeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Chat entre el paciente y su doctor, dentro de cada cita. */
@Service
public class MensajeService {

    public static final int MAX = 500;
    public static final int MAX_POR_CITA = 100;
    private final MensajeRepository repo;
    private final ActividadService actividad;

    public MensajeService(MensajeRepository repo, ActividadService actividad) { this.repo = repo; this.actividad = actividad; }

    public List<Mensaje> deCita(Long citaId) { return repo.findByCitaIdOrderByIdAsc(citaId); }

    /** autor: "Paciente" o "Doctor". La cita debe estar activa o atendida (no cancelada). */
    public Mensaje enviar(Cita cita, String autor, String texto) {
        if (cita.isCancelada()) throw new ReglaNegocioException("No se puede escribir en una cita cancelada.");
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) throw new ReglaNegocioException("Escribe un mensaje.");
        if (t.length() > MAX) t = t.substring(0, MAX);
        if (repo.countByCitaId(cita.getId()) >= MAX_POR_CITA) throw new ReglaNegocioException("Esta conversación llegó al límite de mensajes.");
        Mensaje m = repo.save(new Mensaje(cita, autor, t));
        actividad.registrar(autor.equals("Doctor") ? cita.getDoctor() : cita.getPaciente().getName(), "Mensaje de chat", "cita " + cita.getId());
        return m;
    }
}
