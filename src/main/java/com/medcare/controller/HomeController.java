package com.medcare.controller;

import com.medcare.model.ReservaForm;
import com.medcare.repository.DoctorRepository;
import com.medcare.service.Catalogo;
import com.medcare.service.EsperaService;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.NotificacionService;
import com.medcare.service.ReservaService;
import com.medcare.service.Valoraciones;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.List;

/** Portada pública y reserva de citas (no requiere iniciar sesión). */
@Controller
public class HomeController {

    private final ReservaService reservas;
    private final DoctorRepository doctores;
    private final Valoraciones valoraciones;
    private final NotificacionService notificaciones;
    private final EsperaService espera;

    public HomeController(ReservaService reservas, DoctorRepository doctores, Valoraciones valoraciones,
                          NotificacionService notificaciones, EsperaService espera) {
        this.reservas = reservas; this.doctores = doctores; this.valoraciones = valoraciones;
        this.notificaciones = notificaciones; this.espera = espera;
    }

    @GetMapping("/")
    public String home(Authentication auth, Model model, jakarta.servlet.http.HttpServletRequest request) {
        request.getSession();   // crea la sesión (token CSRF) antes de renderizar la página
        if (!model.containsAttribute("form")) model.addAttribute("form", new ReservaForm());
        cargar(model, auth);
        return "home";
    }

    @PostMapping("/reservar")
    public String reservar(@Valid @ModelAttribute("form") ReservaForm form, BindingResult br,
                           Authentication auth, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) reservas.validar(form, br);
        if (br.hasErrors()) {
            cargar(model, auth);
            return "home";
        }
        try {
            ReservaService.Resultado r = reservas.reservar(form);
            ra.addFlashAttribute("reserva", r);
            notificaciones.reservaRecibida(r.getCita());
            notificaciones.avisoAdmin(r.getCita());
        } catch (DataIntegrityViolationException e) {
            // Otra persona reservó ese horario justo antes
            br.rejectValue("hora", "hora.ocupada", "Ese horario acaba de ser reservado. Elige otro.");
            cargar(model, auth);
            return "home";
        } catch (ReglaNegocioException e) {
            br.rejectValue("hora", "hora.sala", e.getMessage());      // por ejemplo, sin consultorio libre
            cargar(model, auth);
            return "home";
        }
        return "redirect:/#reservar";
    }

    /** Lista de espera: la persona pide que le avisemos si se libera un horario de ese doctor en esa fecha. */
    @PostMapping("/espera")
    public String esperar(@RequestParam String doctor,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                          @RequestParam(defaultValue = "") String nombre, @RequestParam(defaultValue = "") String dni,
                          @RequestParam(defaultValue = "") String telefono, @RequestParam(defaultValue = "") String email,
                          RedirectAttributes ra) {
        LocalDate hoy = LocalDate.now();
        String error = null;
        if (doctores.findByNombre(doctor).filter(d -> d.isActivo()).isEmpty()) error = "Elige un doctor.";
        else if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(Catalogo.DIAS_MAX))) error = "Elige una fecha de los próximos " + Catalogo.DIAS_MAX + " días.";
        else if (nombre.isBlank() || nombre.length() > 80) error = "Completa tu nombre para anotarte.";
        else if (!dni.matches("\\d{8}")) error = "El DNI debe tener 8 dígitos.";
        else if (!telefono.matches("[0-9 +()-]{7,15}")) error = "Ingresa un teléfono válido.";
        else if (!email.isBlank() && (email.length() > 100 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) error = "Correo no válido.";
        if (error == null) {
            try {
                espera.anotar(doctor, fecha, nombre, dni, telefono, email);
                ra.addFlashAttribute("esperaMsg", "Listo, te anotamos en la lista de espera. " + (email.isBlank()
                        ? "Como no dejaste correo, la clínica te llamará si se libera un horario."
                        : "Te avisaremos por correo si se libera un horario."));
                return "redirect:/#reservar";
            } catch (ReglaNegocioException e) { error = e.getMessage(); }
        }
        ra.addFlashAttribute("esperaError", error);
        return "redirect:/#reservar";
    }

    /** Horarios libres de un doctor en una fecha, por ejemplo ["08:00","08:30"]. */
    @GetMapping("/reservar/horas")
    @ResponseBody
    public List<String> horas(@RequestParam String doctor,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reservas.horasLibres(doctor, fecha).stream().map(h -> h.toString()).toList();
    }

    private void cargar(Model model, Authentication auth) {
        LocalDate hoy = LocalDate.now();
        model.addAttribute("especialidades", Catalogo.ESPECIALIDADES);
        model.addAttribute("doctores", doctores.findByActivoTrueOrderByNombre());
        model.addAttribute("ratings", valoraciones.porDoctor());
        model.addAttribute("resenas", valoraciones.ultimasConComentario(3));
        model.addAttribute("minFecha", hoy.toString());
        model.addAttribute("maxFecha", hoy.plusDays(Catalogo.DIAS_MAX).toString());
        boolean logueado = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        String panel = null;
        if (logueado) {
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean doctor = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            panel = admin ? "/admin/index" : doctor ? "/doctor/agenda" : "/paciente/mi-cita";
        }
        model.addAttribute("panelUrl", panel);
    }
}
