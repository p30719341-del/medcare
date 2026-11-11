package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Recepción: agenda las citas del día, confirma, cobra y cancela. NO ve diagnósticos, recetas, exámenes ni historias clínicas
 * (las plantillas de esta pantalla nunca los muestran y no hay rutas para abrirlos).
 */
@Controller
@RequestMapping("/recepcion")
public class RecepcionController {

    private static final LocalDate MUY_ANTES = LocalDate.of(2000, 1, 1);
    private static final LocalDate MUY_DESPUES = LocalDate.of(2100, 1, 1);

    private final CitaRepository citaRepo;
    private final DoctorRepository doctorRepo;
    private final ReservaService reservas;
    private final NotificacionService notificaciones;

    public RecepcionController(CitaRepository citaRepo, DoctorRepository doctorRepo, ReservaService reservas, NotificacionService notificaciones) {
        this.citaRepo = citaRepo; this.doctorRepo = doctorRepo; this.reservas = reservas; this.notificaciones = notificaciones;
    }

    @GetMapping("/citas")
    public String citas(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                        @RequestParam(defaultValue = "") String doctor, @RequestParam(defaultValue = "Todos") String estado,
                        @RequestParam(defaultValue = "") String q, Model model) {
        LocalDate hoy = LocalDate.now();
        LocalDate d = desde == null ? hoy : fecha(desde);       // sin parámetro: desde hoy; vacío: todo
        LocalDate h = hasta == null ? hoy.plusDays(7) : fecha(hasta);
        if (desde == null && hasta == null) { d = hoy; h = hoy.plusDays(7); }
        if (d != null && h != null && d.isAfter(h)) { LocalDate t = d; d = h; h = t; }
        var lista = citaRepo.filtrar(d == null ? MUY_ANTES : d, h == null ? MUY_DESPUES : h, doctor, estado, q.trim());
        model.addAttribute("citas", lista);
        model.addAttribute("doctores", doctorRepo.findAllByOrderByNombre());
        model.addAttribute("desde", d == null ? "" : d.toString());
        model.addAttribute("hasta", h == null ? "" : h.toString());
        model.addAttribute("doctor", doctor);
        model.addAttribute("estado", estado);
        model.addAttribute("q", q);
        model.addAttribute("hoy", hoy.toString());
        model.addAttribute("finSemana", hoy.plusDays(7).toString());
        model.addAttribute("iniMes", hoy.with(TemporalAdjusters.firstDayOfMonth()).toString());
        model.addAttribute("finMes", hoy.with(TemporalAdjusters.lastDayOfMonth()).toString());
        model.addAttribute("rapido", "");
        model.addAttribute("volver", "/recepcion/citas");
        return "recepcion-citas";
    }

    @PostMapping("/citas/{id}/confirmar")
    public String confirmar(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            notificaciones.citaConfirmada(reservas.confirmar(id));
            ra.addFlashAttribute("msg", "Cita confirmada.");
        } catch (ReglaNegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:" + destino(volver);
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            notificaciones.citaCancelada(reservas.cancelar(id, null));
            ra.addFlashAttribute("msg", "Cita cancelada.");
        } catch (ReglaNegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:" + destino(volver);
    }

    @PostMapping("/citas/{id}/noasistio")
    public String noAsistio(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            reservas.marcarNoAsistio(id, null);
            ra.addFlashAttribute("msg", "Marcada como inasistencia.");
        } catch (ReglaNegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:" + destino(volver);
    }

    @PostMapping("/citas/{id}/pago")
    public String pago(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            Cita c = reservas.alternarPago(id);
            ra.addFlashAttribute("msg", c.isPagado() ? "Pago registrado." : "Pago anulado.");
        } catch (ReglaNegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:" + destino(volver);
    }

    @GetMapping("/citas/{id}/boleta.pdf")
    public ResponseEntity<byte[]> boleta(@PathVariable Long id) {
        Cita c = citaRepo.findById(id).filter(Cita::isPagado).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"boleta-" + c.getNumeroBoleta() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(RecetaPdf.generarBoleta(c));
    }

    private static LocalDate fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }

    private static String destino(String volver) {
        if (volver != null && volver.startsWith("/recepcion/") && !volver.contains("//") && !volver.contains("\\") && !volver.contains(":")) return volver;
        return "/recepcion/citas";
    }
}
