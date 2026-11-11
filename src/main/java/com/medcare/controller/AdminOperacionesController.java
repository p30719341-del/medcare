package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.model.Examen;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.service.ExamenService;
import com.medcare.service.RecetaPdf;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.RespaldoService;
import com.medcare.service.ReservaService;
import com.medcare.service.iPacienteService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

/** Administrador: sobreturnos, copias de seguridad, boletas y archivos de exámenes. */
@Controller
@RequestMapping("/admin")
public class AdminOperacionesController {

    private final ReservaService reservas;
    private final iPacienteService pacientes;
    private final DoctorRepository doctores;
    private final CitaRepository citas;
    private final RespaldoService respaldos;
    private final ExamenService examenes;
    private final com.medcare.service.ActividadService actividad;

    public AdminOperacionesController(ReservaService reservas, iPacienteService pacientes, DoctorRepository doctores,
                                      CitaRepository citas, RespaldoService respaldos, ExamenService examenes,
                                      com.medcare.service.ActividadService actividad) {
        this.reservas = reservas; this.pacientes = pacientes; this.doctores = doctores;
        this.citas = citas; this.respaldos = respaldos; this.examenes = examenes; this.actividad = actividad;
    }

    // ---------- Sobreturnos ----------

    @GetMapping("/sobreturno")
    public String sobreturnoForm(Model model) {
        cargarSobreturno(model);
        return "sobreturno";
    }

    @PostMapping("/sobreturno")
    public String sobreturno(@RequestParam Long paciente, @RequestParam String doctor,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                             @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime hora,
                             Model model, RedirectAttributes ra) {
        try {
            Cita c = reservas.sobreturno(paciente, doctor, fecha, hora);
            ra.addFlashAttribute("msg", "Sobreturno agregado en " + c.getConsultorio() + ". La cita quedó confirmada.");
            return "redirect:/admin/citas?desde=" + fecha + "&hasta=" + fecha;
        } catch (ReglaNegocioException | DataIntegrityViolationException e) {
            cargarSobreturno(model);
            model.addAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "No se pudo guardar el sobreturno.");
            return "sobreturno";
        }
    }

    private void cargarSobreturno(Model model) {
        LocalDate hoy = LocalDate.now();
        model.addAttribute("pacientes", pacientes.listar("", "Todos"));
        model.addAttribute("doctores", doctores.findByActivoTrueOrderByNombre());
        model.addAttribute("minFecha", hoy.toString());
        model.addAttribute("maxFecha", hoy.plusDays(com.medcare.service.Catalogo.DIAS_MAX).toString());
        model.addAttribute("max", com.medcare.service.Catalogo.SOBRETURNOS_MAX);
    }

    // ---------- Copias de seguridad ----------

    @GetMapping("/respaldos")
    public String respaldosLista(Model model) {
        model.addAttribute("copias", respaldos.listar());
        model.addAttribute("soportado", respaldos.isSoportado());
        return "respaldos";
    }

    @PostMapping("/respaldos/crear")
    public String respaldoCrear(Authentication auth, RedirectAttributes ra) {
        try {
            ra.addFlashAttribute("msg", "Copia creada: " + respaldos.crear(auth.getName()).getNombre());
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "No se pudo crear la copia: " + e.getMessage());
        }
        return "redirect:/admin/respaldos";
    }

    @GetMapping("/respaldos/{nombre:.+}")
    public ResponseEntity<Resource> respaldoDescargar(@PathVariable String nombre) {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new FileSystemResource(respaldos.ruta(nombre)));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---------- Boleta de una cita pagada ----------

    @GetMapping("/citas/{id}/boleta.pdf")
    public ResponseEntity<byte[]> boleta(@PathVariable Long id) {
        Cita c = citas.findById(id).filter(Cita::isPagado).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"boleta-" + c.getNumeroBoleta() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(RecetaPdf.generarBoleta(c));
    }

    // ---------- Ver un examen adjunto ----------

    @GetMapping("/examenes/{id}")
    public ResponseEntity<byte[]> examen(@PathVariable Long id) {
        try {
            Examen e = examenes.obtener(id);
            actividad.registrar("Acceso a historia", "Admin abrió un examen de " + e.getCita().getPaciente().getName());
            return ExamenesHttp.respuesta(e);
        } catch (ReglaNegocioException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
