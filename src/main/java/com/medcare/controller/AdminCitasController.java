package com.medcare.controller;

import com.medcare.model.Cita;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.service.Catalogo;
import com.medcare.service.ExamenService;
import com.medcare.service.NotificacionService;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.ReservaService;
import com.medcare.service.Valoraciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/** Citas del administrador: lista con filtros, calendario semanal, atención (diagnóstico y receta) y reportes. */
@Controller
@RequestMapping("/admin")
public class AdminCitasController {

    private static final LocalDate MUY_ANTES = LocalDate.of(2000, 1, 1);
    private static final LocalDate MUY_DESPUES = LocalDate.of(2100, 1, 1);

    private final CitaRepository citaRepo;
    private final DoctorRepository doctorRepo;
    private final ReservaService reservas;
    private final NotificacionService notificaciones;
    private final Valoraciones valoraciones;
    private final ExamenService examenes;
    private final com.medcare.service.ActividadService actividad;
    private final com.medcare.service.MensajeService mensajes;
    private final com.medcare.repository.EncuestaRepository encuestaRepo;

    public AdminCitasController(CitaRepository citaRepo, DoctorRepository doctorRepo, ReservaService reservas,
                                NotificacionService notificaciones, Valoraciones valoraciones, ExamenService examenes,
                                com.medcare.service.ActividadService actividad, com.medcare.service.MensajeService mensajes,
                                com.medcare.repository.EncuestaRepository encuestaRepo) {
        this.encuestaRepo = encuestaRepo;
        this.citaRepo = citaRepo; this.doctorRepo = doctorRepo; this.reservas = reservas;
        this.notificaciones = notificaciones; this.valoraciones = valoraciones; this.examenes = examenes;
        this.actividad = actividad; this.mensajes = mensajes;
    }

    // ---------- Lista con filtros ----------

    /**
     * Lista con filtros. Sin parámetro "desde" se muestran las citas desde hoy;
     * con desde= vacío ("Todas") se muestra también el historial.
     */
    @GetMapping("/citas")
    public String citas(@RequestParam(required = false) String desde,
                        @RequestParam(required = false) String hasta,
                        @RequestParam(defaultValue = "") String doctor,
                        @RequestParam(defaultValue = "Todos") String estado,
                        @RequestParam(defaultValue = "") String q,
                        HttpServletRequest req, Model model) {
        LocalDate hoy = LocalDate.now();
        LocalDate a = desde == null ? hoy : fecha(desde);
        LocalDate b = fecha(hasta);
        if (a != null && b != null && a.isAfter(b)) { LocalDate t = a; a = b; b = t; }   // fechas al revés
        List<Cita> lista = citaRepo.filtrar(a == null ? MUY_ANTES : a, b == null ? MUY_DESPUES : b, doctor, estado, q.trim());
        String ini = a == null ? "" : a.toString(), fin = b == null ? "" : b.toString();
        String rapido = "";
        if (ini.equals(hoy.toString()) && fin.equals(hoy.toString())) rapido = "hoy";
        else if (ini.equals(hoy.toString()) && fin.equals(hoy.plusDays(6).toString())) rapido = "semana";
        else if (ini.equals(hoy.withDayOfMonth(1).toString()) && fin.equals(hoy.withDayOfMonth(hoy.lengthOfMonth()).toString())) rapido = "mes";
        else if (ini.isEmpty() && fin.isEmpty()) rapido = "todas";
        model.addAttribute("citas", lista);
        model.addAttribute("doctores", doctorRepo.findAllByOrderByNombre());
        model.addAttribute("desde", ini);
        model.addAttribute("hasta", fin);
        model.addAttribute("doctor", doctor);
        model.addAttribute("estado", estado);
        model.addAttribute("q", q);
        model.addAttribute("rapido", rapido);
        model.addAttribute("hoy", hoy.toString());
        model.addAttribute("finSemana", hoy.plusDays(6).toString());
        model.addAttribute("iniMes", hoy.withDayOfMonth(1).toString());
        model.addAttribute("finMes", hoy.withDayOfMonth(hoy.lengthOfMonth()).toString());
        model.addAttribute("volver", volver(req));
        return "citas";
    }

    private static LocalDate fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }

    @PostMapping("/citas/{id}/confirmar")
    public String confirmar(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            notificaciones.citaConfirmada(reservas.confirmar(id));
            ra.addFlashAttribute("msg", "Cita confirmada.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino(volver);
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            notificaciones.citaCancelada(reservas.cancelar(id, null));
            ra.addFlashAttribute("msg", "Cita cancelada.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino(volver);
    }

    // ---------- Atención: diagnóstico y receta ----------

    @GetMapping("/citas/{id}/atencion")
    public String atencionForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Cita c = citaRepo.findById(id).orElse(null);
        if (c == null || c.isCancelada()) {
            ra.addFlashAttribute("error", "Esa cita no existe o fue cancelada.");
            return "redirect:/admin/citas";
        }
        model.addAttribute("cita", c);
        model.addAttribute("base", "/admin");
        model.addAttribute("volverUrl", "/admin/citas");
        model.addAttribute("accionUrl", "/admin/citas/" + id + "/atencion");
        model.addAttribute("examenes", examenes.deCita(id));
        model.addAttribute("examenUrl", "/admin/examenes/");    // el administrador solo puede ver los archivos
        model.addAttribute("plantillas", java.util.List.of());
        model.addAttribute("cie10", com.medcare.service.Cie10.todos());
        model.addAttribute("mensajes", mensajes.deCita(id));      // el admin solo lee el chat
        model.addAttribute("noAsistioUrl", "/admin/citas/" + id + "/noasistio");
        actividad.registrar("Acceso a historia", "Admin abrió la ficha de " + c.getPaciente().getName() + " (cita " + c.getFecha() + ")");
        return "atencion";
    }

    @PostMapping("/citas/{id}/noasistio")
    public String noAsistio(@PathVariable Long id, @RequestParam(defaultValue = "") String volver, RedirectAttributes ra) {
        try {
            reservas.marcarNoAsistio(id, null);
            ra.addFlashAttribute("msg", "Marcada como inasistencia.");
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino(volver);
    }

    @PostMapping("/citas/{id}/atencion")
    public String atencion(@PathVariable Long id, @RequestParam(defaultValue = "") String diagnostico,
                           @RequestParam(defaultValue = "") String receta,
                           @RequestParam(defaultValue = "") String alergias, @RequestParam(defaultValue = "") String antecedentes,
                           @RequestParam(defaultValue = "") String peso, @RequestParam(defaultValue = "") String talla,
                           @RequestParam(defaultValue = "") String cie10, @RequestParam(defaultValue = "") String controlDias,
                           RedirectAttributes ra) {
        try {
            reservas.registrarAtencion(id, diagnostico, receta, null, alergias, antecedentes, peso, talla, true, cie10, controlDias);
            ra.addFlashAttribute("msg", "Atención registrada. El paciente ya puede ver su receta en el portal.");
            return "redirect:/admin/citas";
        } catch (ReglaNegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/citas/" + id + "/atencion";
        }
    }

    // ---------- Calendario semanal ----------

    public static class Fila {
        private final LocalTime hora;
        private final List<List<Cita>> celdas;
        public Fila(LocalTime hora, List<List<Cita>> celdas) { this.hora = hora; this.celdas = celdas; }
        public LocalTime getHora() { return hora; }
        public List<List<Cita>> getCeldas() { return celdas; }
    }

    @GetMapping("/calendario")
    public String calendario(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana,
                             @RequestParam(defaultValue = "") String doctor, HttpServletRequest req, Model model) {
        LocalDate lunes = (semana == null ? LocalDate.now() : semana).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> dias = new ArrayList<>();
        for (int i = 0; i < 6; i++) dias.add(lunes.plusDays(i));          // lunes a sábado
        List<Cita> citas = citaRepo.findByFechaBetweenAndEstadoNotOrderByFechaAscHoraAsc(lunes, lunes.plusDays(5), "Cancelada")
                .stream().filter(c -> doctor.isEmpty() || c.getDoctor().equals(doctor)).toList();
        // Las filas son todas las horas que tienen citas esa semana, más las horas habituales (08:00–17:30 cada 30 min)
        TreeSet<LocalTime> horas = new TreeSet<>();
        for (LocalTime t = LocalTime.of(8, 0); t.isBefore(LocalTime.of(18, 0)); t = t.plusMinutes(30)) horas.add(t);
        citas.forEach(c -> horas.add(c.getHora()));
        List<Fila> filas = new ArrayList<>();
        for (LocalTime h : horas) {
            List<List<Cita>> celdas = new ArrayList<>();
            for (LocalDate d : dias) {
                celdas.add(citas.stream().filter(c -> c.getFecha().equals(d) && c.getHora().equals(h)).toList());
            }
            filas.add(new Fila(h, celdas));
        }
        model.addAttribute("lunes", lunes);
        model.addAttribute("sabado", lunes.plusDays(5));
        model.addAttribute("dias", dias);
        model.addAttribute("filas", filas);
        model.addAttribute("hoy", LocalDate.now());
        model.addAttribute("anterior", lunes.minusWeeks(1).toString());
        model.addAttribute("siguiente", lunes.plusWeeks(1).toString());
        model.addAttribute("estaSemana", LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
        model.addAttribute("doctor", doctor);
        model.addAttribute("doctores", doctorRepo.findAllByOrderByNombre());
        model.addAttribute("total", citas.size());
        return "calendario";
    }

    // ---------- Reportes ----------

    public static class Barra {
        private final String etiqueta; private final long valor; private final int pct; private final String detalle;
        public Barra(String etiqueta, long valor, long max, String detalle) {
            this.etiqueta = etiqueta; this.valor = valor; this.detalle = detalle;
            this.pct = max == 0 ? 0 : (int) Math.round(valor * 100.0 / max);
        }
        public String getEtiqueta() { return etiqueta; }
        public long getValor() { return valor; }
        public int getPct() { return pct; }
        public String getDetalle() { return detalle; }
        /** Color (solo para el gráfico de anillo): se guarda en "detalle". */
        public String getColor() { return detalle; }
    }

    /** Aplica los filtros de los reportes. Fechas vacías o mal escritas = sin límite; doctor vacío = todos. */
    public static List<Cita> filtrar(List<Cita> citas, LocalDate desde, LocalDate hasta, String doctor) {
        return citas.stream()
                .filter(c -> desde == null || !c.getFecha().isBefore(desde))
                .filter(c -> hasta == null || !c.getFecha().isAfter(hasta))
                .filter(c -> doctor == null || doctor.isBlank() || doctor.equals(c.getDoctor()))
                .toList();
    }

    static LocalDate fechaONull(String s) {
        try { return s == null || s.isBlank() ? null : LocalDate.parse(s.trim()); } catch (java.time.format.DateTimeParseException e) { return null; }
    }

    @GetMapping("/reportes")
    public String reportes(Model model, @RequestParam(defaultValue = "") String desde, @RequestParam(defaultValue = "") String hasta,
                           @RequestParam(defaultValue = "") String doctor) {
        LocalDate d1 = fechaONull(desde), d2 = fechaONull(hasta);
        if (d1 != null && d2 != null && d1.isAfter(d2)) { LocalDate x = d1; d1 = d2; d2 = x; }
        List<Cita> deDoctor = filtrar(citaRepo.findAll(), null, null, doctor);       // para las tendencias mensuales
        List<Cita> todas = filtrar(deDoctor, d1, d2, null);
        List<Cita> validas = todas.stream().filter(c -> !c.isCancelada() && !c.isNoAsistio()).toList();
        model.addAttribute("fDesde", d1 == null ? "" : d1.toString());
        model.addAttribute("fHasta", d2 == null ? "" : d2.toString());
        model.addAttribute("fDoctor", doctor);
        model.addAttribute("filtrado", d1 != null || d2 != null || !doctor.isBlank());
        model.addAttribute("listaDoctores", doctorRepo.findAllByOrderByNombre());
        model.addAttribute("hoy", LocalDate.now().toString());
        model.addAttribute("iniMes", LocalDate.now().withDayOfMonth(1).toString());
        model.addAttribute("ini30", LocalDate.now().minusDays(30).toString());
        model.addAttribute("iniAnio", LocalDate.now().withDayOfYear(1).toString());

        model.addAttribute("totalCitas", todas.size());
        model.addAttribute("totalAtendidas", todas.stream().filter(Cita::isAtendida).count());
        model.addAttribute("totalPendientes", todas.stream().filter(c -> "Pendiente".equals(c.getEstado())).count());
        model.addAttribute("totalCanceladas", todas.stream().filter(Cita::isCancelada).count());
        model.addAttribute("cobrado", validas.stream().filter(Cita::isPagado).mapToInt(Cita::getPrecio).sum());
        model.addAttribute("porCobrar", validas.stream().filter(c -> !c.isPagado() && c.isAtendida()).mapToInt(Cita::getPrecio).sum());

        Map<String, Long> est = todas.stream().collect(Collectors.groupingBy(Cita::getEstado, Collectors.counting()));
        List<Barra> porEstado = new ArrayList<>();
        StringBuilder gradiente = new StringBuilder();
        double acumulado = 0;
        for (String e : List.of("Pendiente", "Confirmada", "Atendida", "Cancelada")) {
            long v = est.getOrDefault(e, 0L);
            if (v == 0) continue;
            double parte = v * 100.0 / todas.size();
            String color = colorEstado(e);
            if (gradiente.length() > 0) gradiente.append(", ");
            gradiente.append(String.format(Locale.ROOT, "%s %.2f%% %.2f%%", color, acumulado, acumulado + parte));
            acumulado += parte;
            porEstado.add(new Barra(e, v, v, color));
        }
        model.addAttribute("porEstado", porEstado);
        model.addAttribute("donut", gradiente.length() == 0 ? "background:#e6ebf3" : "background:conic-gradient(" + gradiente + ")");
        model.addAttribute("porEspecialidad", barras(validas.stream().collect(Collectors.groupingBy(Cita::getEspecialidad, Collectors.counting())),
                Catalogo.nombresEspecialidades()));

        // Últimos 6 meses (incluye el actual)
        YearMonth actual = YearMonth.now();
        Map<String, Long> meses = new LinkedHashMap<>();
        List<String> orden = new ArrayList<>();
        Locale es = Locale.forLanguageTag("es-PE");
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            String clave = ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, es) + " " + ym.getYear();
            orden.add(clave);
            meses.put(clave, citaRepo.findAll().stream().filter(c -> !c.isCancelada() && (doctor.isBlank() || doctor.equals(c.getDoctor())) && YearMonth.from(c.getFecha()).equals(ym)).count());
        }
        model.addAttribute("porMes", barras(meses, orden));

        Map<String, Valoraciones.Rating> ratings = valoraciones.porDoctor();
        Map<String, Long> porDoctor = validas.stream().collect(Collectors.groupingBy(Cita::getDoctor, Collectors.counting()));
        List<String> doctores = doctorRepo.findAllByOrderByNombre().stream().map(d -> d.getNombre()).toList();
        long max = porDoctor.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Barra> docBarras = new ArrayList<>();
        for (String d : doctores) {
            Valoraciones.Rating r = ratings.get(d);
            docBarras.add(new Barra(d, porDoctor.getOrDefault(d, 0L), max,
                    r == null ? "Sin reseñas" : "★ " + r.getTexto() + " (" + r.getCantidad() + ")"));
        }
        model.addAttribute("porDoctor", docBarras);
        List<Cita> validasDoctor = deDoctor.stream().filter(c -> !c.isCancelada() && !c.isNoAsistio()).toList();
        tendencias(model, deDoctor, validasDoctor, doctores);
        model.addAttribute("comparativo", comparativo(todas, doctores));
        return "reportes";
    }

    /** Fila de la tabla "Comparación entre doctores". */
    public static class Comparado {
        private final String doctor; private final long total, atendidas, canceladas, noAsistio; private final int cobrado;
        private final String resena, encuesta; private final long asistencia;
        Comparado(String doctor, long total, long atendidas, long canceladas, long noAsistio, int cobrado, String resena, String encuesta, long asistencia) {
            this.doctor = doctor; this.total = total; this.atendidas = atendidas; this.canceladas = canceladas; this.noAsistio = noAsistio;
            this.cobrado = cobrado; this.resena = resena; this.encuesta = encuesta; this.asistencia = asistencia;
        }
        public String getDoctor() { return doctor; } public long getTotal() { return total; } public long getAtendidas() { return atendidas; }
        public long getCanceladas() { return canceladas; } public long getNoAsistio() { return noAsistio; } public int getCobrado() { return cobrado; }
        public String getResena() { return resena; } public String getEncuesta() { return encuesta; }
        /** % de citas atendidas entre las que ya debieron ocurrir (atendidas + no asistió). */
        public long getAsistencia() { return asistencia; }
    }

    private List<Comparado> comparativo(List<Cita> citas, List<String> doctores) {
        Map<String, Valoraciones.Rating> ratings = valoraciones.porDoctor();
        Map<String, String> enc = new HashMap<>();
        for (Object[] f : encuestaRepo.resumenPorDoctor()) {
            double prom = (((Number) f[1]).doubleValue() + ((Number) f[2]).doubleValue() + ((Number) f[3]).doubleValue()) / 3.0;
            enc.put((String) f[0], String.format(Locale.forLanguageTag("es-PE"), "%.1f/5 (%d)", prom, ((Number) f[4]).longValue()));
        }
        List<Comparado> r = new ArrayList<>();
        for (String d : doctores) {
            List<Cita> m = citas.stream().filter(c -> d.equals(c.getDoctor())).toList();
            long at = m.stream().filter(Cita::isAtendida).count(), no = m.stream().filter(Cita::isNoAsistio).count();
            long can = m.stream().filter(Cita::isCancelada).count();
            int cob = m.stream().filter(c -> c.isPagado() && !c.isCancelada()).mapToInt(Cita::getPrecio).sum();
            Valoraciones.Rating rt = ratings.get(d);
            r.add(new Comparado(d, m.size(), at, can, no, cob, rt == null ? "—" : "★ " + rt.getTexto() + " (" + rt.getCantidad() + ")",
                    enc.getOrDefault(d, "—"), at + no == 0 ? -1 : Math.round(at * 100.0 / (at + no))));
        }
        r.sort((a, b) -> Long.compare(b.getTotal(), a.getTotal()));
        return r;
    }

    /** Tarjeta de tendencia: valor, comparación con el mes anterior y si mejoró (sube) o empeoró. */
    public static class Tendencia {
        private final String titulo, valor, detalle; private final String clase;
        public Tendencia(String titulo, String valor, String detalle, String clase) { this.titulo = titulo; this.valor = valor; this.detalle = detalle; this.clase = clase; }
        public String getTitulo() { return titulo; }
        public String getValor() { return valor; }
        public String getDetalle() { return detalle; }
        /** up (mejora), down (empeora) o flat (sin cambio / sin dato). */
        public String getClase() { return clase; }
    }

    /** Compara este mes con el anterior y calcula horas pico, días fuertes y cancelaciones por doctor. */
    private void tendencias(Model model, List<Cita> todas, List<Cita> validas, List<String> doctores) {
        YearMonth ahora = YearMonth.now(), previo = ahora.minusMonths(1);
        long citasAhora = validas.stream().filter(c -> YearMonth.from(c.getFecha()).equals(ahora)).count();
        long citasPrevio = validas.stream().filter(c -> YearMonth.from(c.getFecha()).equals(previo)).count();
        int cobAhora = validas.stream().filter(c -> c.isPagado() && YearMonth.from(c.getFecha()).equals(ahora)).mapToInt(Cita::getPrecio).sum();
        int cobPrevio = validas.stream().filter(c -> c.isPagado() && YearMonth.from(c.getFecha()).equals(previo)).mapToInt(Cita::getPrecio).sum();
        long canAhora = todas.stream().filter(c -> c.isCancelada() && YearMonth.from(c.getFecha()).equals(ahora)).count();
        long canPrevio = todas.stream().filter(c -> c.isCancelada() && YearMonth.from(c.getFecha()).equals(previo)).count();
        long virtuales = validas.stream().filter(Cita::isVirtual).count();

        List<Tendencia> t = new ArrayList<>();
        t.add(comparar("Citas este mes", citasAhora, citasPrevio, "", true));
        t.add(comparar("Ingresos cobrados este mes", cobAhora, cobPrevio, "S/ ", true));
        t.add(comparar("Cancelaciones este mes", canAhora, canPrevio, "", false));
        t.add(new Tendencia("Citas virtuales", validas.isEmpty() ? "0 %" : Math.round(virtuales * 100.0 / validas.size()) + " %",
                virtuales + " de " + validas.size() + " citas", "flat"));
        model.addAttribute("tendencias", t);

        Map<String, Long> horas = new LinkedHashMap<>();
        List<String> ordenHoras = new ArrayList<>();
        for (int h = 8; h < 18; h++) {
            String k = String.format("%02d:00", h);
            ordenHoras.add(k);
            final int hh = h;
            horas.put(k, validas.stream().filter(c -> c.getHora().getHour() == hh).count());
        }
        model.addAttribute("porHora", barras(horas, ordenHoras));

        String[] dias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        Map<String, Long> porDia = new LinkedHashMap<>();
        for (int i = 0; i < dias.length; i++) {
            final DayOfWeek dow = DayOfWeek.of(i + 1);
            porDia.put(dias[i], validas.stream().filter(c -> c.getFecha().getDayOfWeek() == dow).count());
        }
        model.addAttribute("porDia", barras(porDia, List.of(dias)));

        Map<String, Long> totalDoc = todas.stream().collect(Collectors.groupingBy(Cita::getDoctor, Collectors.counting()));
        Map<String, Long> canDoc = todas.stream().filter(Cita::isCancelada).collect(Collectors.groupingBy(Cita::getDoctor, Collectors.counting()));
        long maxCan = canDoc.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Barra> cancel = new ArrayList<>();
        for (String d : doctores) {
            long c = canDoc.getOrDefault(d, 0L), tot = totalDoc.getOrDefault(d, 0L);
            cancel.add(new Barra(d, c, maxCan, tot == 0 ? "Sin citas" : Math.round(c * 100.0 / tot) + " % de " + tot));
        }
        model.addAttribute("cancelPorDoctor", cancel);
    }

    /** masEsMejor: true si subir es bueno (citas, ingresos); false si subir es malo (cancelaciones). */
    private static Tendencia comparar(String titulo, long ahora, long previo, String prefijo, boolean masEsMejor) {
        String valor = prefijo + ahora;
        if (previo == 0) return new Tendencia(titulo, valor, ahora == 0 ? "Sin datos aún" : "Sin datos del mes anterior", "flat");
        long dif = Math.round((ahora - previo) * 100.0 / previo);
        if (dif == 0) return new Tendencia(titulo, valor, "Igual que el mes anterior (" + prefijo + previo + ")", "flat");
        boolean sube = dif > 0;
        String clase = sube == masEsMejor ? "up" : "down";
        return new Tendencia(titulo, valor, (sube ? "▲ " : "▼ ") + Math.abs(dif) + " % vs mes anterior (" + prefijo + previo + ")", clase);
    }

    private static String colorEstado(String estado) {
        switch (estado) {
            case "Confirmada": return "#157a55";
            case "Atendida":   return "#2864e8";
            case "Pendiente":  return "#e8a21a";
            default:           return "#b3321f";
        }
    }

    private static List<Barra> barras(Map<String, Long> datos, List<String> orden) {
        long max = datos.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Barra> r = new ArrayList<>();
        for (String k : orden) r.add(new Barra(k, datos.getOrDefault(k, 0L), max, ""));
        return r;
    }

    // ---------- Utilidades ----------

    private static String volver(HttpServletRequest req) {
        String qs = req.getQueryString();
        return req.getRequestURI() + (qs == null ? "" : "?" + qs);
    }

    /** Solo se permite volver a páginas del panel admin (evita redirecciones a otros sitios). */
    private static String destino(String volver) {
        if (volver != null && volver.startsWith("/admin/") && !volver.contains("//") && !volver.contains("\\") && !volver.contains(":")) {
            return volver;
        }
        return "/admin/index";
    }
}
