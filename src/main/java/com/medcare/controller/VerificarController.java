package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.repository.CitaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Página pública a la que lleva el QR de la receta: confirma que es auténtica sin mostrar datos sensibles. */
@Controller
public class VerificarController {

    private final CitaRepository citas;

    public VerificarController(CitaRepository citas) { this.citas = citas; }

    @GetMapping("/verificar/{codigo}")
    public String verificar(@PathVariable String codigo, Model model) {
        Cita c = codigo.matches("[A-Z0-9]{6,20}") ? citas.findByCodigoReceta(codigo).stream().findFirst().orElse(null) : null;
        model.addAttribute("cita", c);
        model.addAttribute("codigo", codigo.length() > 20 ? codigo.substring(0, 20) : codigo);
        if (c != null) {
            model.addAttribute("iniciales", iniciales(c.getFamiliar() != null ? c.getFamiliar() : c.getPaciente().getName()));
            String dni = c.getPaciente().getDni();
            model.addAttribute("dniParcial", dni == null || dni.length() < 8 ? "" : "*****" + dni.substring(5));
        }
        return "verificar";
    }

    /** "María Fernanda López" -> "M. F. L." (no se muestra el nombre completo a quien solo tiene el código). */
    static String iniciales(String nombre) {
        StringBuilder sb = new StringBuilder();
        for (String p : nombre.trim().split("[\\s,]+")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(". ");
        return sb.toString().trim();
    }
}
