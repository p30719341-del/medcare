package com.medcare.controller;

import com.medcare.model.Paciente;
import com.medcare.model.RegistroForm;
import com.medcare.service.ActividadService;
import com.medcare.service.PacienteService;
import com.medcare.service.PoliticaClave;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** Registro del propio paciente: crea su ficha y su acceso (usuario = DNI, clave que él elige). */
@Controller
public class RegistroController {

    private final PacienteService pacientes;
    private final ActividadService actividad;

    public RegistroController(PacienteService pacientes, ActividadService actividad) {
        this.pacientes = pacientes; this.actividad = actividad;
    }

    @GetMapping("/registro")
    public String formulario(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistroForm());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("form") RegistroForm f, BindingResult br, Model model, RedirectAttributes ra) {
        if (!br.hasErrors() && pacientes.existeDni(f.getDni())) {
            br.rejectValue("dni", "dni.existe", "Ese DNI ya tiene cuenta. Inicia sesión o recupera tu contraseña.");
        }
        if (!br.hasErrors()) {
            var errores = PoliticaClave.validar(f.getClave(), null, f.getDni(), f.getConfirmar());
            if (!errores.isEmpty()) model.addAttribute("errores", errores);
        }
        if (br.hasErrors() || model.containsAttribute("errores")) return "registro";
        Paciente p = new Paciente(f.getNombre().trim(), f.getDni(), f.getTelefono().trim(), LocalDate.now(), "Activo");
        p.setEmail(f.getEmail());
        pacientes.registrarPropio(p, f.getClave());
        actividad.registrar(f.getDni(), "Paciente registrado", f.getNombre().trim());
        ra.addFlashAttribute("registrado", true);
        return "redirect:/login?registrado";
    }
}
