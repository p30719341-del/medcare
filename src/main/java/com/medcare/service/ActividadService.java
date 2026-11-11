package com.medcare.service;

import com.medcare.model.Actividad;
import com.medcare.repository.ActividadRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/** Registro de actividad: quién hizo qué y cuándo. Nunca debe impedir la acción principal. */
@Service
public class ActividadService {

    private final ActividadRepository repo;

    public ActividadService(ActividadRepository repo) { this.repo = repo; }

    public void registrar(String accion, String detalle) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        registrar(a == null || a.getName() == null ? "visitante" : a.getName(), accion, detalle);
    }

    public void registrar(String usuario, String accion, String detalle) {
        try {
            repo.save(new Actividad(usuario, accion, detalle));
        } catch (Exception e) {
            // la auditoría es secundaria: si falla, la acción principal continúa
        }
    }

    public List<Actividad> ultimas() { return repo.findTop200ByOrderByIdDesc(); }

    /** Últimos movimientos de un tipo ("Acceso a historia", "Login"...). tipo vacío = todos. */
    public List<Actividad> ultimas(String tipo) {
        if (tipo == null || tipo.isBlank()) return ultimas();
        return repo.findTop200ByAccionOrderByIdDesc(tipo);
    }
}
