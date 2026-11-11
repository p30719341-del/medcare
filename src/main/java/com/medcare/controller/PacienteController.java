package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.model.Paciente;
import com.medcare.model.Resena;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.ResenaRepository;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.Catalogo;
import com.medcare.service.NotificacionService;
import com.medcare.service.RecetaPdf;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.ReservaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.medcare.model.Examen;
import com.medcare.service.ExamenService;
import com.medcare.service.Urls;
import com.medcare.service.MensajeService;
import com.medcare.service.EncuestaService;
import com.medcare.service.Ics;
import com.medcare.service.Cie10;
import com.medcare.repository.EncuestaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.model.Mensaje;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Portal del paciente. Los datos salen de la sesión, nunca de la URL; cada acción verifica que la cita sea suya. */
@Controller
@RequestMapping("/paciente")
public class PacienteController {

    private final UsuarioRepository usuarioRepo;
    private final CitaRepository citaRepo;
    private final ResenaRepository resenaRepo;
    private final ReservaService reservas;
    private final NotificacionService notificaciones;
    private final ExamenService examenes;
    private final Urls urls;
    private final MensajeService mensajes;
    private final EncuestaService encuestas;
    private final EncuestaRepository encuestaRepo;
    private final DoctorRepository doctorRepo;

    public PacienteController(UsuarioRepository usuarioRepo, CitaRepository citaRepo, ResenaRepository resenaRepo,
                              ReservaService reservas, NotificacionService notificaciones, ExamenService examenes, Urls urls,
                              MensajeService mensajes, EncuestaService encuestas, EncuestaRepository encuestaRepo, DoctorRepository doctorRepo) {
        this.mensajes = mensajes; this.encuestas = encuestas; this.encuestaRepo = encuestaRepo; this.doctorRepo = doctorRepo;
        this.usuarioRepo = usuarioRepo; this.citaRepo = citaRepo; this.resenaRepo = resenaRepo;
        this.reservas = reservas; this.notificaciones = notificaciones; this.examenes = examenes; this.urls = urls;
    }

    @GetMapping("/mi-cita")
    public String miCita(Authentication auth, Model model) {
        Paciente p = actual(auth);
        List<Cita> citas = citaRepo.findByPacienteIdOrderByFechaAscHoraAsc(p.getId());
        Cita proxima = citas.stream()
                .filter(c -> c.isActiva() && !c.getFecha().isBefore(LocalDate.now()))
                .findFirst().orElse(null);
        List<Cita> historial = citaRepo.findByPacienteIdAndEstadoOrderByFechaDescHoraDesc(p.getId(), "Atendida");
        Set<Long> resenadas = resenaRepo.findByCitaPacienteId(p.getId()).stream()
                .map(r -> r.getCita().getId()).collect(Collectors.toSet());
        model.addAttribute("paciente", p);
        model.addAttribute("citas", citas);
        model.addAttribute("proxima", proxima);
        model.addAttribute("historial", historial);
        model.addAttribute("resenadas", resenadas);
        model.addAttribute("horasCambio", Catalogo.HORAS_CAMBIO);
        java.util.Map<Long, List<Examen>> adjuntos = new java.util.HashMap<>();
        for (Cita c : citas) {
            if (!c.isCancelada()) adjuntos.put(c.getId(), examenes.deCita(c.getId()));
        }
        model.addAttribute("examenes", adjuntos);
        model.addAttribute("maxMb", ExamenService.MAX_BYTES / 1_000_000);
        java.util.Map<Long, List<Mensaje>> chats = new java.util.HashMap<>();
        for (Cita c : citas) if (!c.isCancelada()) chats.put(c.getId(), mensajes.deCita(c.getId()));
        model.addAttribute("chats", chats);
        model.addAttribute("encuestadas", encuestaRepo.findByCitaPacienteId(p.getId()).stream()
                .map(e -> e.getCita().getId()).collect(Collectors.toSet()));
        return "mi-cita";
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            notificaciones.citaCancelada(reservas.cancelar(id, actual(auth)));
            ra.addFlashAttribute("msg", "Cita cancelada.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/paciente/mi-cita";
    }

    @GetMapping("/citas/{id}/reprogramar")
    public String reprogramarForm(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(actual(auth).getId())).orElse(null);
        if (c == null || !c.isCambiable()) {
            ra.addFlashAttribute("error", "Esa cita no se puede reprogramar.");
            return "redirect:/paciente/mi-cita";
        }
        cargarFechas(model);
        model.addAttribute("cita", c);
        return "reprogramar";
    }

    @PostMapping("/citas/{id}/reprogramar")
    public String reprogramar(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime hora) {
        String error = null;
        if (fecha == null || hora == null) {
            error = "Elige una fecha y un horario.";
        } else {
            try {
                notificaciones.citaReprogramada(reservas.reprogramar(id, actual(auth), fecha, hora));
                ra.addFlashAttribute("msg", "Cita reprogramada. Queda pendiente de confirmación.");
                return "redirect:/paciente/mi-cita";
            } catch (ReglaNegocioException e) {
                error = e.getMessage();
            } catch (DataIntegrityViolationException e) {
                error = "Ese horario acaba de ser reservado. Elige otro.";
            }
        }
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(actual(auth).getId())).orElse(null);
        if (c == null) return "redirect:/paciente/mi-cita";
        cargarFechas(model);
        model.addAttribute("cita", c);
        model.addAttribute("error", error);
        return "reprogramar";
    }

    @GetMapping("/citas/{id}/receta.pdf")
    public ResponseEntity<byte[]> receta(@PathVariable Long id, Authentication auth) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId()) && x.isAtendida()).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receta-medcare-" + c.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(RecetaPdf.generar(c, c.getCodigoReceta() == null ? null : urls.verificacion(c.getCodigoReceta())));
    }

    @GetMapping("/citas/{id}/boleta.pdf")
    public ResponseEntity<byte[]> boleta(@PathVariable Long id, Authentication auth) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId()) && x.isPagado()).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"boleta-" + c.getNumeroBoleta() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(RecetaPdf.generarBoleta(c));
    }

    // ---------- Exámenes adjuntos ----------

    @PostMapping("/citas/{id}/examenes")
    public String subirExamen(@PathVariable Long id, Authentication auth, RedirectAttributes ra,
                              @RequestParam("archivo") MultipartFile archivo, @RequestParam(defaultValue = "") String descripcion) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId())).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("error", "No encontramos esa cita.");
        } else {
            try {
                examenes.subir(c, archivo, descripcion, "Paciente");
                ra.addFlashAttribute("msg", "Archivo adjuntado a tu cita.");
            } catch (ReglaNegocioException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/paciente/mi-cita";
    }

    @GetMapping("/examenes/{id}")
    public ResponseEntity<byte[]> verExamen(@PathVariable Long id, Authentication auth) {
        Examen e = propio(id, auth);
        return e == null ? ResponseEntity.notFound().build() : ExamenesHttp.respuesta(e);
    }

    @PostMapping("/examenes/{id}/borrar")
    public String borrarExamen(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Examen e = propio(id, auth);
        if (e == null || !"Paciente".equals(e.getSubidoPor())) {
            ra.addFlashAttribute("error", "Solo puedes quitar los archivos que subiste tú.");
        } else {
            examenes.borrar(e);
            ra.addFlashAttribute("msg", "Archivo eliminado.");
        }
        return "redirect:/paciente/mi-cita";
    }

    /** El examen solo se entrega si pertenece a una cita de este paciente. */
    private Examen propio(Long id, Authentication auth) {
        try {
            Examen e = examenes.obtener(id);
            return e.getCita().getPaciente().getId().equals(actual(auth).getId()) ? e : null;
        } catch (ReglaNegocioException ex) {
            return null;
        }
    }


    // ---------- Calendario (.ics), síntomas previos (triaje), chat y encuesta ----------

    @GetMapping("/citas/{id}/calendario.ics")
    public ResponseEntity<byte[]> calendario(@PathVariable Long id, Authentication auth) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId()) && !x.isCancelada()).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        int min = doctorRepo.findByNombre(c.getDoctor()).map(d -> d.getDuracion()).orElse(30);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cita-medcare-" + c.getFecha() + ".ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(Ics.evento(c, min));
    }

    @PostMapping("/citas/{id}/triaje")
    public String triaje(@PathVariable Long id, Authentication auth, RedirectAttributes ra, @RequestParam(defaultValue = "") String sintomas) {
        try {
            reservas.guardarTriaje(id, actual(auth), sintomas);
            ra.addFlashAttribute("msg", "Listo: tu doctor verá tus síntomas antes de la cita.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/paciente/mi-cita";
    }

    @PostMapping("/citas/{id}/mensaje")
    public String mensaje(@PathVariable Long id, Authentication auth, RedirectAttributes ra, @RequestParam(defaultValue = "") String texto) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId())).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("error", "Esa cita no existe.");
        } else {
            try {
                mensajes.enviar(c, "Paciente", texto);
                ra.addFlashAttribute("msg", "Mensaje enviado a " + c.getDoctor() + ".");
            } catch (ReglaNegocioException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/paciente/mi-cita";
    }

    @PostMapping("/citas/{id}/encuesta")
    public String encuesta(@PathVariable Long id, Authentication auth, RedirectAttributes ra,
                           @RequestParam(defaultValue = "0") int atencion, @RequestParam(defaultValue = "0") int puntualidad,
                           @RequestParam(defaultValue = "0") int instalaciones, @RequestParam(defaultValue = "") String sugerencia) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId())).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("error", "Esa cita no existe.");
        } else {
            try {
                encuestas.guardar(c, atencion, puntualidad, instalaciones, sugerencia);
                ra.addFlashAttribute("msg", "¡Gracias por responder la encuesta!");
            } catch (ReglaNegocioException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/paciente/mi-cita";
    }

    @PostMapping("/citas/{id}/resena")
    public String resena(@PathVariable Long id, Authentication auth, RedirectAttributes ra,
                         @RequestParam int estrellas, @RequestParam(defaultValue = "") String comentario) {
        Paciente p = actual(auth);
        Cita c = citaRepo.findById(id).filter(x -> x.getPaciente().getId().equals(p.getId()) && x.isAtendida()).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("error", "Solo puedes opinar sobre citas que ya fueron atendidas.");
        } else if (estrellas < 1 || estrellas > 5) {
            ra.addFlashAttribute("error", "Elige de 1 a 5 estrellas.");
        } else if (resenaRepo.existsByCitaId(c.getId())) {
            ra.addFlashAttribute("error", "Ya opinaste sobre esta cita.");
        } else {
            String texto = comentario.trim();
            if (texto.length() > 300) texto = texto.substring(0, 300);
            try {
                resenaRepo.save(new Resena(c, estrellas, texto.isEmpty() ? null : texto, abreviar(p.getName())));
                ra.addFlashAttribute("msg", "¡Gracias por tu opinión!");
            } catch (DataIntegrityViolationException e) {
                ra.addFlashAttribute("error", "Ya opinaste sobre esta cita.");
            }
        }
        return "redirect:/paciente/mi-cita";
    }

    private Paciente actual(Authentication auth) {
        return usuarioRepo.findByUsername(auth.getName()).orElseThrow().getPaciente();
    }

    private void cargarFechas(Model model) {
        LocalDate hoy = LocalDate.now();
        model.addAttribute("horasCambio", Catalogo.HORAS_CAMBIO);
        model.addAttribute("minFecha", hoy.toString());
        model.addAttribute("maxFecha", hoy.plusDays(Catalogo.DIAS_MAX).toString());
    }

    /** "María Fernanda López" -> "María L." */
    static String abreviar(String nombre) {
        String[] t = nombre.trim().split("\\s+");
        if (t.length < 2) return t[0];
        return t[0] + " " + Character.toUpperCase(t[t.length - 1].charAt(0)) + ".";
    }
}
