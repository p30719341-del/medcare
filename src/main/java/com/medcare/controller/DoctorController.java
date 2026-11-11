package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.BloqueoService;
import com.medcare.service.NotificacionService;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.ReservaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import com.medcare.model.Examen;
import com.medcare.service.ExamenService;
import com.medcare.service.ActividadService;
import com.medcare.service.Cie10;
import com.medcare.service.MensajeService;
import com.medcare.service.PlantillaService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Portal del doctor: su agenda, la atención de sus pacientes y los días en que no atiende. */
@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private final UsuarioRepository usuarios;
    private final CitaRepository citas;
    private final ReservaService reservas;
    private final BloqueoService bloqueos;
    private final NotificacionService notificaciones;
    private final ExamenService examenes;
    private final ActividadService actividad;
    private final MensajeService mensajes;
    private final PlantillaService plantillas;

    public DoctorController(UsuarioRepository usuarios, CitaRepository citas, ReservaService reservas,
                            BloqueoService bloqueos, NotificacionService notificaciones, ExamenService examenes,
                            ActividadService actividad, MensajeService mensajes, PlantillaService plantillas) {
        this.usuarios = usuarios; this.citas = citas; this.reservas = reservas;
        this.bloqueos = bloqueos; this.notificaciones = notificaciones; this.examenes = examenes;
        this.actividad = actividad; this.mensajes = mensajes; this.plantillas = plantillas;
    }

    @GetMapping("/agenda")
    public String agenda(Authentication auth, Model model,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Doctor d = actual(auth);
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        List<Cita> lista = citas.findByDoctorAndFecha(d.getNombre(), dia).stream()
                .filter(c -> !c.isCancelada()).sorted(Comparator.comparing(Cita::getHora)).toList();
        model.addAttribute("doctor", d);
        model.addAttribute("fecha", dia);
        model.addAttribute("anterior", dia.minusDays(1).toString());
        model.addAttribute("siguiente", dia.plusDays(1).toString());
        model.addAttribute("hoy", LocalDate.now().toString());
        model.addAttribute("citas", lista);
        model.addAttribute("atiende", d.atiende(dia.getDayOfWeek()));
        model.addAttribute("atendidas", lista.stream().filter(Cita::isAtendida).count());
        model.addAttribute("porAtender", lista.stream().filter(c -> !c.isAtendida()).count());
        model.addAttribute("proximas", citas.findByDoctorAndFechaBetweenAndEstadoNotOrderByFechaAscHoraAsc(
                d.getNombre(), LocalDate.now(), LocalDate.now().plusDays(7), "Cancelada").stream().filter(Cita::isActiva).count());
        return "doctor-agenda";
    }

    @PostMapping("/citas/{id}/confirmar")
    public String confirmar(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            reservas.verificarPropiedad(id, actual(auth).getNombre());
            notificaciones.citaConfirmada(reservas.confirmar(id));
            ra.addFlashAttribute("msg", "Cita confirmada.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return volverA(id);
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            notificaciones.citaCancelada(reservas.cancelarComoDoctor(id, actual(auth).getNombre()));
            ra.addFlashAttribute("msg", "Cita cancelada. Se avisó al paciente si dejó su correo.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return volverA(id);
    }

    @GetMapping("/citas/{id}/atencion")
    public String atencionForm(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        Cita c = citas.findById(id).filter(x -> x.getDoctor().equals(actual(auth).getNombre()) && !x.isCancelada()).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("error", "Esa cita no existe o no es tuya.");
            return "redirect:/doctor/agenda";
        }
        model.addAttribute("cita", c);
        model.addAttribute("base", "/doctor");
        model.addAttribute("volverUrl", "/doctor/agenda?fecha=" + c.getFecha());
        model.addAttribute("accionUrl", "/doctor/citas/" + id + "/atencion");
        model.addAttribute("examenes", examenes.deCita(id));
        model.addAttribute("examenUrl", "/doctor/examenes/");
        model.addAttribute("subirUrl", "/doctor/citas/" + id + "/examenes");
        model.addAttribute("borrarUrl", "/doctor/examenes/");
        model.addAttribute("plantillas", plantillas.deDoctor(c.getDoctor()));
        model.addAttribute("cie10", Cie10.todos());
        model.addAttribute("mensajes", mensajes.deCita(id));
        model.addAttribute("chatUrl", "/doctor/citas/" + id + "/mensaje");
        model.addAttribute("noAsistioUrl", "/doctor/citas/" + id + "/noasistio");
        actividad.registrar("Acceso a historia", "Dr. abrió la ficha de " + c.getPaciente().getName() + " (cita " + c.getFecha() + ")");
        return "atencion";
    }

    @PostMapping("/citas/{id}/noasistio")
    public String noAsistio(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            reservas.marcarNoAsistio(id, actual(auth).getNombre());
            ra.addFlashAttribute("msg", "Marcada como inasistencia.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return volverA(id);
    }

    @PostMapping("/citas/{id}/mensaje")
    public String mensaje(@PathVariable Long id, Authentication auth, RedirectAttributes ra, @RequestParam(defaultValue = "") String texto) {
        try {
            reservas.verificarPropiedad(id, actual(auth).getNombre());
            mensajes.enviar(reservas.obtenerCita(id), "Doctor", texto);
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/citas/" + id + "/atencion#chat";
    }

    // ---------- Plantillas de receta ----------

    @GetMapping("/plantillas")
    public String plantillasForm(Authentication auth, Model model) {
        Doctor d = actual(auth);
        model.addAttribute("doctor", d);
        model.addAttribute("plantillas", plantillas.deDoctor(d.getNombre()));
        model.addAttribute("cie10", Cie10.todos());
        return "doctor-plantillas";
    }

    @PostMapping("/plantillas")
    public String plantillaCrear(Authentication auth, RedirectAttributes ra, @RequestParam(defaultValue = "") String titulo,
                                 @RequestParam(defaultValue = "") String cie10, @RequestParam(defaultValue = "") String diagnostico,
                                 @RequestParam(defaultValue = "") String receta) {
        try {
            plantillas.crear(actual(auth).getNombre(), titulo, cie10, diagnostico, receta);
            ra.addFlashAttribute("msg", "Plantilla guardada.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/plantillas";
    }

    @PostMapping("/plantillas/{id}/borrar")
    public String plantillaBorrar(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        plantillas.borrar(id, actual(auth).getNombre());
        ra.addFlashAttribute("msg", "Plantilla eliminada.");
        return "redirect:/doctor/plantillas";
    }

    @PostMapping("/citas/{id}/atencion")
    public String atencion(@PathVariable Long id, Authentication auth, RedirectAttributes ra,
                           @RequestParam(defaultValue = "") String diagnostico, @RequestParam(defaultValue = "") String receta,
                           @RequestParam(defaultValue = "") String alergias, @RequestParam(defaultValue = "") String antecedentes,
                           @RequestParam(defaultValue = "") String peso, @RequestParam(defaultValue = "") String talla,
                           @RequestParam(defaultValue = "") String cie10, @RequestParam(defaultValue = "") String controlDias) {
        try {
            Cita c = reservas.registrarAtencion(id, diagnostico, receta, actual(auth).getNombre(), alergias, antecedentes, peso, talla, true, cie10, controlDias);
            ra.addFlashAttribute("msg", "Atención registrada. El paciente ya puede ver su receta en su portal.");
            return "redirect:/doctor/agenda?fecha=" + c.getFecha();
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/doctor/citas/" + id + "/atencion";
        }
    }

    // ---------- Exámenes adjuntos ----------

    @PostMapping("/citas/{id}/examenes")
    public String subirExamen(@PathVariable Long id, Authentication auth, RedirectAttributes ra,
                              @RequestParam("archivo") MultipartFile archivo, @RequestParam(defaultValue = "") String descripcion) {
        try {
            reservas.verificarPropiedad(id, actual(auth).getNombre());
            examenes.subir(reservas.obtenerCita(id), archivo, descripcion, "Doctor");
            ra.addFlashAttribute("msg", "Archivo adjuntado a la cita.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/citas/" + id + "/atencion";
    }

    @GetMapping("/examenes/{id}")
    public ResponseEntity<byte[]> verExamen(@PathVariable Long id, Authentication auth) {
        Examen e = propio(id, auth);
        if (e != null) actividad.registrar("Acceso a historia", "Dr. abrió un examen de " + e.getCita().getPaciente().getName());
        return e == null ? ResponseEntity.notFound().build() : ExamenesHttp.respuesta(e);
    }

    @PostMapping("/examenes/{id}/borrar")
    public String borrarExamen(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Examen e = propio(id, auth);
        if (e == null) {
            ra.addFlashAttribute("error", "No encontramos ese archivo.");
            return "redirect:/doctor/agenda";
        }
        Long citaId = e.getCita().getId();
        examenes.borrar(e);
        ra.addFlashAttribute("msg", "Archivo eliminado.");
        return "redirect:/doctor/citas/" + citaId + "/atencion";
    }

    private Examen propio(Long id, Authentication auth) {
        try {
            Examen e = examenes.obtener(id);
            return e.getCita().getDoctor().equals(actual(auth).getNombre()) ? e : null;
        } catch (ReglaNegocioException ex) {
            return null;
        }
    }

    // ---------- Días bloqueados ----------

    @GetMapping("/bloqueos")
    public String bloqueosForm(Authentication auth, Model model) {
        Doctor d = actual(auth);
        model.addAttribute("doctor", d);
        model.addAttribute("bloqueos", bloqueos.proximos(d.getNombre()));
        model.addAttribute("hoy", LocalDate.now().toString());
        return "doctor-bloqueos";
    }

    @PostMapping("/bloqueos")
    public String bloquear(Authentication auth, RedirectAttributes ra,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam(defaultValue = "") String motivo) {
        try {
            long conCitas = bloqueos.bloquear(actual(auth).getNombre(), fecha, motivo);
            ra.addFlashAttribute("msg", conCitas == 0 ? "Día bloqueado: nadie podrá reservar ese día."
                    : "Día bloqueado, pero ya hay " + conCitas + " cita(s) ese día. Cancélalas o pídele al paciente que reprograme.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/bloqueos";
    }

    @PostMapping("/bloqueos/{id}/borrar")
    public String desbloquear(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        bloqueos.desbloquear(id, actual(auth).getNombre());
        ra.addFlashAttribute("msg", "Día desbloqueado.");
        return "redirect:/doctor/bloqueos";
    }

    private Doctor actual(Authentication auth) {
        return usuarios.findByUsername(auth.getName()).orElseThrow().getDoctor();
    }

    private String volverA(Long id) {
        return citas.findById(id).map(c -> "redirect:/doctor/agenda?fecha=" + c.getFecha()).orElse("redirect:/doctor/agenda");
    }
}
