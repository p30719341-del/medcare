package com.medcare.controller;

import com.medcare.repository.ActividadRepository;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.repository.ExamenRepository;
import com.medcare.repository.PacienteRepository;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.NotificacionService;
import com.medcare.service.PagosInfo;
import com.medcare.service.RecordatorioService;
import com.medcare.service.RespaldoService;
import com.medcare.service.WhatsAppService;
import com.medcare.util.Cifrado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** "Estado del sistema": qué servicios están reales, cuáles siguen simulados y qué falta configurar. Nunca muestra secretos. */
@Controller
@RequestMapping("/admin/sistema")
public class AdminSistemaController {

    /** Una línea del panel. */
    public static class Item {
        private final String nombre, estado, detalle; private final boolean ok;
        Item(String nombre, boolean ok, String estado, String detalle) { this.nombre = nombre; this.ok = ok; this.estado = estado; this.detalle = detalle; }
        public String getNombre() { return nombre; }
        public boolean isOk() { return ok; }
        public String getEstado() { return estado; }
        public String getDetalle() { return detalle; }
    }

    private final NotificacionService correo;
    private final WhatsAppService whatsapp;
    private final RecordatorioService recordatorios;
    private final RespaldoService respaldos;
    private final PagosInfo pagos;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final PacienteRepository pacienteRepo;
    private final CitaRepository citaRepo;
    private final DoctorRepository doctorRepo;
    private final ExamenRepository examenRepo;
    private final ActividadRepository actividadRepo;
    private final String smtpHost, dbUrl, baseUrl, perfiles;

    public AdminSistemaController(NotificacionService correo, WhatsAppService whatsapp, RecordatorioService recordatorios, RespaldoService respaldos,
                                  PagosInfo pagos, UsuarioRepository usuarios, PasswordEncoder encoder, PacienteRepository pacienteRepo,
                                  CitaRepository citaRepo, DoctorRepository doctorRepo, ExamenRepository examenRepo, ActividadRepository actividadRepo,
                                  @Value("${spring.mail.host:}") String smtpHost,
                                  @Value("${spring.datasource.url:}") String dbUrl,
                                  @Value("${medcare.base-url:}") String baseUrl,
                                  @Value("${spring.profiles.active:}") String perfiles) {
        this.correo = correo; this.whatsapp = whatsapp; this.recordatorios = recordatorios; this.respaldos = respaldos; this.pagos = pagos;
        this.usuarios = usuarios; this.encoder = encoder; this.pacienteRepo = pacienteRepo; this.citaRepo = citaRepo; this.doctorRepo = doctorRepo;
        this.examenRepo = examenRepo; this.actividadRepo = actividadRepo;
        this.smtpHost = smtpHost; this.dbUrl = dbUrl; this.baseUrl = baseUrl; this.perfiles = perfiles;
    }

    @GetMapping
    public String panel(Model model) {
        List<Item> s = new ArrayList<>();
        boolean smtp = correo.isActivo() && correo.isServidorConfigurado();
        s.add(new Item("Correo", smtp, smtp ? "Real" : "Simulado",
                correo.isActivo() ? (correo.isServidorConfigurado() ? "Se envían correos reales por SMTP (" + (smtpHost.isBlank() ? "servidor configurado" : smtpHost) + ")."
                                                                     : "medcare.mail.enabled=true pero falta el servidor SMTP (spring.mail.host).")
                                  : "Los correos solo se escriben en la consola. Activa medcare.mail.enabled=true y el SMTP."));
        boolean wa = whatsapp.isActivo() && whatsapp.isConfigurado();
        s.add(new Item("WhatsApp automático", wa, wa ? "Real" : "Simulado",
                wa ? "Se envían recordatorios con la API de WhatsApp Business."
                   : "Solo se escribe en la consola. Requiere cuenta de WhatsApp Business, token y plantilla aprobada (README.txt)."));
        String ult = recordatorios.getUltimaEjecucion() == null ? "aún no se ha ejecutado desde que arrancó"
                : "última revisión " + recordatorios.getUltimaEjecucion().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " (" + recordatorios.getUltimoTotal() + " enviados)";
        s.add(new Item("Recordatorios 24 h", recordatorios.isAutomatico(), recordatorios.isAutomatico() ? "Activo" : "Apagado", "Cada hora; " + ult + "."));
        boolean yape = pagos.isYape();
        s.add(new Item("Pagos", yape, yape ? "Yape/Plin" : "Simulado",
                yape ? "El paciente ve el número de Yape/Plin; recepción confirma el pago."
                     : "Recepción o el admin marcan “cobrado”. Una pasarela con tarjeta necesita cuenta de comercio propia."));
        List<RespaldoService.Copia> copias = respaldos.isSoportado() ? respaldos.listar() : List.of();
        s.add(new Item("Copias de seguridad", respaldos.isSoportado() && !copias.isEmpty(), respaldos.isSoportado() ? (copias.isEmpty() ? "Sin copias" : "Activas") : "Externas",
                respaldos.isSoportado() ? (copias.isEmpty() ? "Aún no hay copias: crea una desde “Copias de seguridad”."
                        : "Última copia: " + copias.get(0).getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".")
                        : "Usas MySQL/PostgreSQL: usa mysqldump/pg_dump o las copias de tu proveedor."));
        boolean demo = Cifrado.usaClaveDemo();
        s.add(new Item("Cifrado de datos médicos", !demo, demo ? "Clave de demo" : "Clave propia",
                demo ? "Defines MEDCARE_CLAVE_CIFRADO para usar tu propia clave antes de guardar datos reales."
                     : "Diagnósticos, recetas, alergias y mensajes se guardan cifrados (AES-256-GCM)."));
        boolean h2 = dbUrl.startsWith("jdbc:h2");
        s.add(new Item("Base de datos", !h2, h2 ? "H2 local" : tipoDb(), h2 ? "Archivo local ./data. En contenedores sin volumen se pierde al recrearlos; usa MySQL/PostgreSQL al publicar."
                                                                   : "Base externa conectada."));
        boolean urlPublica = !baseUrl.isBlank() && !baseUrl.contains("localhost");
        s.add(new Item("Dirección pública (BASE_URL)", urlPublica, urlPublica ? "Configurada" : "Localhost",
                urlPublica ? "El QR de las recetas apunta a esa dirección." : "El QR de las recetas solo funcionará en este equipo. Define BASE_URL al publicar."));
        boolean adminDemo = usuarios.findByUsername("admin").map(u -> encoder.matches("admin123", u.getPassword())).orElse(false);
        s.add(new Item("Clave del administrador", !adminDemo, adminDemo ? "Clave de demo" : "Personalizada",
                adminDemo ? "Sigue siendo admin123. Cámbiala (menú “Cambiar contraseña”) o define ADMIN_PASSWORD." : "Ya no es la clave de demostración."));
        boolean dosPasos = usuarios.findByUsername("admin").map(u -> u.isTotpActivo()).orElse(false);
        s.add(new Item("Verificación en 2 pasos (admin)", dosPasos, dosPasos ? "Activa" : "Apagada", dosPasos ? "El administrador usa la app autenticadora." : "Actívala desde “Verificación en 2 pasos”."));
        model.addAttribute("servicios", s);

        List<Item> d = new ArrayList<>();
        d.add(new Item("Pacientes", true, "", String.valueOf(pacienteRepo.count())));
        d.add(new Item("Doctores", true, "", String.valueOf(doctorRepo.count())));
        d.add(new Item("Citas", true, "", String.valueOf(citaRepo.count())));
        d.add(new Item("Exámenes adjuntos", true, "", String.valueOf(examenRepo.count())));
        d.add(new Item("Movimientos de actividad", true, "", String.valueOf(actividadRepo.count())));
        model.addAttribute("datos", d);

        Runtime rt = Runtime.getRuntime();
        long usadoMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576, maxMb = rt.maxMemory() / 1_048_576;
        Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        List<Item> v = new ArrayList<>();
        v.add(new Item("Java", true, "", System.getProperty("java.version")));
        v.add(new Item("Memoria", true, "", usadoMb + " MB de " + maxMb + " MB"));
        v.add(new Item("Encendido desde hace", true, "", up.toDays() + " d " + up.toHoursPart() + " h " + up.toMinutesPart() + " min"));
        v.add(new Item("Zona horaria", true, "", java.time.ZoneId.systemDefault().getId()));
        v.add(new Item("Perfil", true, "", perfiles.isBlank() ? "por defecto" : perfiles));
        model.addAttribute("servidor", v);
        return "sistema";
    }

    private String tipoDb() {
        if (dbUrl.startsWith("jdbc:mysql")) return "MySQL";
        if (dbUrl.startsWith("jdbc:postgresql")) return "PostgreSQL";
        return "Externa";
    }

    @PostMapping("/prueba-correo")
    public String pruebaCorreo(RedirectAttributes ra) {
        ra.addFlashAttribute("msg", correo.correoDePrueba(correo.getAdminEmail()));
        return "redirect:/admin/sistema";
    }
}
