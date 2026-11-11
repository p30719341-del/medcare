package com.medcare.controller;

import com.medcare.model.Doctor;
import com.medcare.repository.DoctorRepository;
import com.medcare.service.Catalogo;
import com.medcare.service.DoctorAccesoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/** Alta, edición y horarios de los doctores. */
@Controller
@RequestMapping("/admin/doctores")
public class AdminDoctoresController {

    private final DoctorRepository doctores;
    private final DoctorAccesoService accesos;

    public AdminDoctoresController(DoctorRepository doctores, DoctorAccesoService accesos) {
        this.doctores = doctores; this.accesos = accesos;
    }

    @GetMapping
    public String lista(Model model) {
        model.addAttribute("doctores", doctores.findAllByOrderByNombre());
        return "doctores";
    }

    @GetMapping("/new")
    public String nuevo(Model model) {
        model.addAttribute("doctor", new Doctor());
        formulario(model);
        return "doctor-form";
    }

    @GetMapping("/edit/{id}")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return doctores.findById(id).map(d -> {
            model.addAttribute("doctor", d);
            formulario(model);
            return "doctor-form";
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "No encontramos ese doctor.");
            return "redirect:/admin/doctores";
        });
    }

    @PostMapping("/save")
    public String guardar(@Valid @ModelAttribute("doctor") Doctor d, BindingResult br,
                          @RequestParam(name = "diasSel", required = false) List<Integer> diasSel,
                          Model model, RedirectAttributes ra) {
        List<Integer> dias = diasSel == null ? List.of() : diasSel.stream().filter(n -> n >= 1 && n <= 7).distinct().sorted().toList();
        d.setDias(dias.stream().map(String::valueOf).collect(Collectors.joining(",")));   // para que el formulario conserve lo marcado

        Doctor existente = d.getId() == null ? null : doctores.findById(d.getId()).orElse(null);
        if (d.getId() != null && existente == null) {
            ra.addFlashAttribute("error", "No encontramos ese doctor.");
            return "redirect:/admin/doctores";
        }
        if (existente == null && d.getNombre() != null && doctores.existsByNombre(d.getNombre().trim())) {
            br.rejectValue("nombre", "nombre.duplicado", "Ya existe un doctor con ese nombre.");
        }
        if (d.getEspecialidad() != null && !d.getEspecialidad().isBlank() && !Catalogo.nombresEspecialidades().contains(d.getEspecialidad())) {
            br.rejectValue("especialidad", "especialidad.invalida", "Elige una especialidad de la lista.");
        }
        if (d.getConsultorio() != null && !d.getConsultorio().isBlank() && !Catalogo.CONSULTORIOS.contains(d.getConsultorio())) {
            br.rejectValue("consultorio", "consultorio.invalido", "Elige un consultorio de la lista.");
        }
        if (dias.isEmpty()) br.rejectValue("dias", "dias.vacio", "Marca al menos un día de atención.");
        if (d.getFoto() != null && !d.getFoto().isBlank() && !d.getFoto().matches("[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp|svg)")) {
            br.rejectValue("foto", "foto.invalida", "Escribe solo el nombre del archivo, por ejemplo ramirez.jpg");
        }
        if (d.getInicio() != null && d.getFin() != null) {
            if (!d.getFin().isAfter(d.getInicio())) {
                br.rejectValue("fin", "fin.antes", "La hora de fin debe ser posterior al inicio.");
            } else if ((d.getPausaInicio() == null) != (d.getPausaFin() == null)) {
                br.rejectValue("pausaFin", "pausa.incompleta", "Completa el inicio y el fin de la pausa, o deja ambos vacíos.");
            } else if (d.getPausaInicio() != null && !d.getPausaFin().isAfter(d.getPausaInicio())) {
                br.rejectValue("pausaFin", "pausa.orden", "El fin de la pausa debe ser posterior a su inicio.");
            } else if (d.horarios().isEmpty()) {
                br.rejectValue("duracion", "horarios.vacios", "Con ese horario y duración no queda ningún turno.");
            }
        }
        if (br.hasErrors()) {
            formulario(model);
            return "doctor-form";
        }

        Doctor g = existente != null ? existente : new Doctor();
        if (existente == null) g.setNombre(d.getNombre().trim());      // el nombre no se cambia después: las citas lo usan
        g.setEspecialidad(d.getEspecialidad());
        g.setDescripcion(d.getDescripcion());
        g.setFoto(d.getFoto() == null || d.getFoto().isBlank() ? null : d.getFoto());
        g.setDias(d.getDias());
        g.setInicio(d.getInicio());
        g.setFin(d.getFin());
        g.setPausaInicio(d.getPausaInicio());
        g.setPausaFin(d.getPausaFin());
        g.setDuracion(d.getDuracion());
        g.setPrecio(d.getPrecio());
        g.setConsultorio(d.getConsultorio());
        g.setActivo(d.isActivo());
        doctores.save(g);
        if (existente == null) {
            String usuario = accesos.crear(g, true);
            ra.addFlashAttribute("msg", "Doctor creado. Su acceso: usuario " + usuario + " · clave inicial " + DoctorAccesoService.CLAVE_INICIAL + " (la cambia al ingresar).");
        } else {
            ra.addFlashAttribute("msg", "Doctor guardado. Los nuevos horarios ya se ven en la web.");
        }
        return "redirect:/admin/doctores";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, RedirectAttributes ra) {
        doctores.findById(id).ifPresent(d -> {
            d.setActivo(!d.isActivo());
            doctores.save(d);
            ra.addFlashAttribute("msg", d.getNombre() + (d.isActivo() ? " vuelve a aparecer en la web." : " ya no aparece en la web."));
        });
        return "redirect:/admin/doctores";
    }

    private void formulario(Model model) {
        model.addAttribute("especialidades", Catalogo.nombresEspecialidades());
        model.addAttribute("consultorios", Catalogo.CONSULTORIOS);
        model.addAttribute("nombresDias", List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"));
    }
}
