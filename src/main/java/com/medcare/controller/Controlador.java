package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.model.Paciente;
import com.medcare.repository.CitaRepository;
import com.medcare.service.iPacienteService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/** Panel principal y CRUD de pacientes: solo accesible para el rol ADMIN (ver SecurityConfig). */
@Controller
@RequestMapping("/admin")
public class Controlador {

    private final iPacienteService service;
    private final CitaRepository citaRepo;

    public Controlador(iPacienteService service, CitaRepository citaRepo) {
        this.service = service; this.citaRepo = citaRepo;
    }

    @GetMapping("/index")
    public String listar(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "Todos") String estado,
                         Model model) {
        List<Paciente> pacientes = service.listar(q.trim(), estado);
        List<Cita> citasHoy = citaRepo.findByFechaAndEstadoNotOrderByHora(LocalDate.now(), "Cancelada");
        long confirmadas = citasHoy.stream().filter(c -> "Confirmada".equals(c.getEstado()) || c.isAtendida()).count();
        model.addAttribute("pacientes", pacientes);
        model.addAttribute("totalPacientes", service.total());
        model.addAttribute("citasHoy", citasHoy);
        model.addAttribute("confirmadas", confirmadas);
        model.addAttribute("pendientes", citasHoy.size() - confirmadas);
        model.addAttribute("proximas", citaRepo.findByFechaGreaterThanAndEstadoNotOrderByFechaAscHoraAsc(LocalDate.now(), "Cancelada")
                .stream().limit(8).toList());
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);
        model.addAttribute("volver", "/admin/index");
        return "index";
    }

    @GetMapping("/new")
    public String agregar(Model model) {
        model.addAttribute("paciente", new Paciente());
        return "form";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        return service.listarId(id).map(p -> {
            model.addAttribute("paciente", p);
            return "form";
        }).orElse("redirect:/admin/index");
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("paciente") Paciente p, BindingResult br,
                       RedirectAttributes ra) {
        if (p.getId() == null && p.getDni() != null && service.existeDni(p.getDni())) {
            br.rejectValue("dni", "dni.duplicado", "Ya existe un paciente con ese DNI.");
        }
        if (br.hasErrors()) return "form";
        service.save(p);
        ra.addFlashAttribute("msg", "Paciente guardado correctamente.");
        return "redirect:/admin/index";
    }

    // POST (no GET) para que una simple URL no pueda borrar registros
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("msg", "Paciente eliminado.");
        return "redirect:/admin/index";
    }
}
