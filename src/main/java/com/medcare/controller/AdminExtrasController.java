package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.repository.CitaRepository;
import com.medcare.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Exportar a Excel/PDF, pagos, lista de espera, registro de actividad y recordatorios. */
@Controller
@RequestMapping("/admin")
public class AdminExtrasController {

    private static final LocalDate MUY_ANTES = LocalDate.of(2000, 1, 1);
    private static final LocalDate MUY_DESPUES = LocalDate.of(2100, 1, 1);

    private final CitaRepository citaRepo;
    private final ReservaService reservas;
    private final EsperaService espera;
    private final ActividadService actividad;
    private final RecordatorioService recordatorios;

    public AdminExtrasController(CitaRepository citaRepo, ReservaService reservas, EsperaService espera,
                                 ActividadService actividad, RecordatorioService recordatorios) {
        this.citaRepo = citaRepo; this.reservas = reservas; this.espera = espera;
        this.actividad = actividad; this.recordatorios = recordatorios;
    }

    // ---------- Exportar citas (CSV: se abre directo en Excel) ----------

    @GetMapping("/citas/export.csv")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                                           @RequestParam(defaultValue = "") String doctor,
                                           @RequestParam(defaultValue = "Todos") String estado,
                                           @RequestParam(defaultValue = "") String q) {
        LocalDate a = desde == null ? LocalDate.now() : fecha(desde), b = fecha(hasta);
        if (a != null && b != null && a.isAfter(b)) { LocalDate t = a; a = b; b = t; }
        List<Cita> lista = citaRepo.filtrar(a == null ? MUY_ANTES : a, b == null ? MUY_DESPUES : b, doctor, estado, q.trim());
        StringBuilder sb = new StringBuilder("﻿");       // marca UTF-8 para que Excel muestre bien las tildes
        sb.append("Fecha;Hora;Paciente;DNI;Telefono;Atendido;Doctor;Especialidad;Estado;Precio (S/);Pagado\r\n");
        for (Cita c : lista) {
            sb.append(String.join(";",
                    celda(c.getFecha().toString()), celda(c.getHora().toString()), celda(c.getPaciente().getName()),
                    celda(c.getPaciente().getDni()), celda(c.getPaciente().getPhone()), celda(c.getFamiliar()),
                    celda(c.getDoctor()), celda(c.getEspecialidad()), celda(c.getEstado()),
                    String.valueOf(c.getPrecio()), c.isPagado() ? "Si" : "No")).append("\r\n");
        }
        actividad.registrar("Exportación", lista.size() + " citas a CSV");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"citas-medcare.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Entre comillas, y sin que Excel interprete el texto como fórmula (=, +, -, @). */
    static String celda(String v) {
        if (v == null) return "\"\"";
        String t = v.replace("\r", " ").replace("\n", " ");
        if (!t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0) t = "'" + t;
        return "\"" + t.replace("\"", "\"\"") + "\"";
    }

    private static LocalDate fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }

    // ---------- Resumen en PDF ----------

    @GetMapping("/reportes/resumen.pdf")
    public ResponseEntity<byte[]> resumenPdf(@RequestParam(defaultValue = "") String desde, @RequestParam(defaultValue = "") String hasta,
                                             @RequestParam(defaultValue = "") String doctor) {
        List<Cita> todas = AdminCitasController.filtrar(citaRepo.findAll(), AdminCitasController.fechaONull(desde), AdminCitasController.fechaONull(hasta), doctor);
        List<Cita> validas = todas.stream().filter(c -> !c.isCancelada()).toList();
        List<String> f = new ArrayList<>();
        f.add("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ". Datos de demostración.");
        if (!desde.isBlank() || !hasta.isBlank() || !doctor.isBlank()) {
            f.add("Filtros: " + (desde.isBlank() ? "desde el inicio" : "desde " + desde) + " · " + (hasta.isBlank() ? "hasta hoy" : "hasta " + hasta)
                    + " · " + (doctor.isBlank() ? "todos los doctores" : doctor));
        }
        f.add("# Resumen");
        f.add("Total de citas: " + todas.size());
        for (String e : List.of("Pendiente", "Confirmada", "Atendida", "Cancelada")) {
            f.add("  " + e + ": " + todas.stream().filter(c -> e.equals(c.getEstado())).count());
        }
        int cobrado = validas.stream().filter(Cita::isPagado).mapToInt(Cita::getPrecio).sum();
        int porCobrar = validas.stream().filter(c -> !c.isPagado() && c.isAtendida()).mapToInt(Cita::getPrecio).sum();
        f.add("Ingresos cobrados: S/ " + cobrado + "   ·   Atendidas por cobrar: S/ " + porCobrar);
        f.add("# Citas por especialidad (sin canceladas)");
        validas.stream().collect(Collectors.groupingBy(Cita::getEspecialidad, TreeMap::new, Collectors.counting()))
                .forEach((k, v) -> f.add("  " + k + ": " + v));
        f.add("# Citas por doctor (sin canceladas)");
        validas.stream().collect(Collectors.groupingBy(Cita::getDoctor, TreeMap::new, Collectors.counting()))
                .forEach((k, v) -> f.add("  " + k + ": " + v));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"reporte-medcare.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(RecetaPdf.generarTexto("MedCare - Reporte de citas", f));
    }

    // ---------- Pago (simulado) ----------

    @PostMapping("/citas/{id}/pago")
    public String pago(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            Cita c = reservas.alternarPago(id);
            ra.addFlashAttribute("msg", c.isPagado() ? "Pago registrado." : "Pago anulado.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino(volver);
    }

    // ---------- Lista de espera ----------

    @GetMapping("/espera")
    public String esperaLista(Model model) {
        model.addAttribute("espera", espera.todas());
        return "espera";
    }

    @PostMapping("/espera/{id}/borrar")
    public String esperaBorrar(@PathVariable Long id, RedirectAttributes ra) {
        espera.quitar(id);
        ra.addFlashAttribute("msg", "Quitado de la lista de espera.");
        return "redirect:/admin/espera";
    }

    // ---------- Actividad ----------

    @GetMapping("/actividad")
    public String actividadLista(@RequestParam(defaultValue = "") String tipo, Model model) {
        model.addAttribute("registros", actividad.ultimas(tipo));
        model.addAttribute("tipo", tipo);
        return "actividad";
    }

    // ---------- Recordatorios ----------

    @PostMapping("/recordatorios/enviar")
    public String recordar(RedirectAttributes ra) {
        int n = recordatorios.enviarProximos();
        ra.addFlashAttribute("msg", n == 0 ? "No hay citas pendientes de recordar en las próximas 24 horas."
                : "Recordatorio enviado a " + n + " cita(s) (a quienes dejaron correo).");
        return "redirect:/admin/citas";
    }

    private static String destino(String volver) {
        if (volver != null && volver.startsWith("/admin/") && !volver.contains("//") && !volver.contains("\\") && !volver.contains(":")) return volver;
        return "/admin/citas";
    }
}
