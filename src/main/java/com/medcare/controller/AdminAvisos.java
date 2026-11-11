package com.medcare.controller;

import com.medcare.repository.CitaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Número de citas pendientes de confirmar: aparece como globo en el menú del administrador. */
@ControllerAdvice
public class AdminAvisos {

    private final CitaRepository citas;

    public AdminAvisos(CitaRepository citas) { this.citas = citas; }

    @ModelAttribute("pendientesCount")
    public Long pendientes(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.startsWith("/admin") ? citas.countByEstado("Pendiente") : null;
    }
}
