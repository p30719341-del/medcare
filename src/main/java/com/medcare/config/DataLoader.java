package com.medcare.config;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.model.Paciente;
import com.medcare.model.Usuario;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import com.medcare.repository.PacienteRepository;
import com.medcare.repository.UsuarioRepository;
import com.medcare.model.Examen;
import com.medcare.model.Encuesta;
import com.medcare.model.Mensaje;
import com.medcare.model.PlantillaReceta;
import com.medcare.repository.ExamenRepository;
import com.medcare.service.Catalogo;
import com.medcare.service.ConsultorioService;
import com.medcare.service.DoctorAccesoService;
import com.medcare.service.ReglaNegocioException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Datos ficticios de demostración (solo si la base está vacía; con MySQL no se duplican al reiniciar). */
@Component
public class DataLoader implements CommandLineRunner {

    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final CitaRepository citas;
    private final DoctorRepository doctores;
    private final PasswordEncoder encoder;
    private final DoctorAccesoService accesos;
    private final ExamenRepository examenes;
    private final ConsultorioService consultorios;
    private final com.medcare.repository.MensajeRepository mensajes;
    private final com.medcare.repository.EncuestaRepository encuestas;
    private final com.medcare.repository.PlantillaRecetaRepository plantillas;
    private int contadorConsultorio = 0;

    public DataLoader(PacienteRepository pacientes, UsuarioRepository usuarios, CitaRepository citas,
                      DoctorRepository doctores, PasswordEncoder encoder, DoctorAccesoService accesos,
                      ExamenRepository examenes, ConsultorioService consultorios,
                      com.medcare.repository.MensajeRepository mensajes, com.medcare.repository.EncuestaRepository encuestas,
                      com.medcare.repository.PlantillaRecetaRepository plantillas) {
        this.pacientes = pacientes; this.usuarios = usuarios; this.citas = citas;
        this.doctores = doctores; this.encoder = encoder; this.accesos = accesos;
        this.examenes = examenes; this.consultorios = consultorios;
        this.mensajes = mensajes; this.encuestas = encuestas; this.plantillas = plantillas;
    }

    @Override
    public void run(String... args) {
        if (doctores.count() == 0) cargarDoctores();
        // Cada doctor necesita su usuario (también en bases creadas con versiones anteriores).
        for (Doctor d : doctores.findAllByOrderByNombre()) {
            if (!usuarios.existsByDoctorId(d.getId())) accesos.crear(d, false);
        }
        // Citas de versiones anteriores sin tarifa: se les pone la del doctor.
        for (Cita c : citas.findAll()) {
            if (c.getPrecio() == 0 && !c.isCancelada()) { doctores.findByNombre(c.getDoctor()).ifPresent(d -> { c.setPrecio(d.getPrecio()); citas.save(c); }); }
        }
        migrar();
        boolean demo = !"false".equalsIgnoreCase(System.getenv("DEMO_DATA"));
        if (demo) sembrarPersonalYPlantillas();
        if (usuarios.findByUsername("admin").isPresent()) return;
        // En internet define la variable de entorno ADMIN_PASSWORD para no dejar la clave de demostración.
        String claveAdmin = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
        usuarios.save(new Usuario("admin", encoder.encode(claveAdmin), "ADMIN", null));
        // En internet puedes poner DEMO_DATA=false para no crear pacientes ni citas de ejemplo (solo admin y doctores).
        if ("false".equalsIgnoreCase(System.getenv("DEMO_DATA"))) return;

        LocalDate hoy = LocalDate.now();
        Paciente maria  = crear("María Fernanda López", "74581236", "987654321", hoy, "Activo");
        Paciente carlos = crear("Carlos Alberto Ruiz", "70814529", "956210448", hoy.minusDays(1), "Pendiente");
        Paciente lucia  = crear("Lucía Torres Mendoza", "76103984", "945887102", hoy.minusDays(3), "Activo");
        Paciente jose   = crear("José Miguel Castro", "72156498", "966321785", hoy.minusDays(5), "Activo");
        Paciente ana    = crear("Ana Sofía Ramírez", "73492815", "999204516", hoy.minusDays(7), "Pendiente");

        g(new Cita(hoy, LocalTime.of(9, 0),   "Medicina general", "Dr. Ramírez", "Confirmada", maria));
        Cita virtual = new Cita(hoy, LocalTime.of(10, 30), "Cardiología",      "Dra. Salas",  "Pendiente",  carlos);
        virtual.setModalidad("Virtual");                                        // ejemplo de telemedicina
        g(virtual);
        g(new Cita(hoy, LocalTime.of(11, 15), "Medicina general", "Dr. Ramírez", "Confirmada", lucia));
        g(new Cita(hoy, LocalTime.of(12, 0),  "Odontología",      "Dr. Paredes", "Pendiente",  jose));
        g(new Cita(hoy.plusDays(1), LocalTime.of(9, 30), "Medicina general", "Dr. Ramírez", "Pendiente", ana));
        g(new Cita(hoy.plusDays(7), LocalTime.of(8, 30), "Cardiología", "Dra. Salas", "Pendiente", maria));

        // Una cita ya atendida, para probar el historial, la receta en PDF y las reseñas
        Cita atendida = new Cita(hoy.minusDays(14), LocalTime.of(9, 30), "Medicina general", "Dr. Ramírez", "Atendida", maria);
        atendida.setDiagnostico("Faringitis aguda leve.");
        atendida.setReceta("Paracetamol 500 mg: 1 tableta cada 8 horas por 3 días si hay fiebre o dolor.\n"
                + "Abundante líquido y reposo. Volver a control si los síntomas duran más de 5 días.");
        g(atendida);
        adjuntarDemo(atendida);

        sembrarHistorial(List.of(maria, carlos, lucia, jose, ana));
        sembrarExtras(maria, carlos, atendida, virtual);
    }

    /** Usuario de recepción y plantillas de receta de ejemplo (también para bases de versiones anteriores). */
    private void sembrarPersonalYPlantillas() {
        if (usuarios.findByUsername("recepcion").isEmpty()) {
            Usuario r = new Usuario("recepcion", encoder.encode("recepcion123"), "RECEPCION", null);
            usuarios.save(r);      // clave de demostración (cámbiala en internet; ver DESPLIEGUE.txt)
        }
        for (Doctor d : doctores.findAllByOrderByNombre()) {
            if (plantillas.countByDoctor(d.getNombre()) > 0) continue;
            plantillas.save(new PlantillaReceta(d.getNombre(), "Resfriado común", "J00", "Resfriado común.",
                    "Paracetamol 500 mg: 1 tableta cada 8 horas si hay fiebre o malestar.\nAbundante líquido, reposo y lavado de manos."));
            plantillas.save(new PlantillaReceta(d.getNombre(), "Faringitis aguda", "J02.9", "Faringitis aguda.",
                    "Ibuprofeno 400 mg: 1 tableta cada 8 horas por 3 días, después de las comidas.\nGárgaras con agua tibia y sal. Volver si hay fiebre más de 3 días."));
            plantillas.save(new PlantillaReceta(d.getNombre(), "Control de rutina", "Z00.0", "Control de rutina.",
                    "Sin medicación. Mantener alimentación balanceada y actividad física.\nControl en 6 meses."));
        }
    }

    /** Triaje, chat, seguimiento y encuestas de ejemplo para que las pantallas nuevas tengan contenido. */
    private void sembrarExtras(Paciente maria, Paciente carlos, Cita atendida, Cita virtual) {
        atendida.setCie10("J02.9");
        atendida.setControlDias(15);          // el doctor pidió un control: el portal lo muestra
        citas.save(atendida);
        mensajes.save(new Mensaje(atendida, "Paciente", "Doctor, ¿puedo tomar el paracetamol junto con el desayuno?"));
        mensajes.save(new Mensaje(atendida, "Doctor", "Sí, puede tomarlo con alimentos. Si la fiebre sigue pasados 3 días, avíseme."));
        virtual.setTriaje("Dolor en el pecho al subir escaleras desde hace 2 semanas. Sin mareos.");
        citas.save(virtual);
        citas.findByPacienteIdOrderByFechaAscHoraAsc(maria.getId()).stream()
                .filter(c -> c.isActiva() && c.getFecha().isAfter(LocalDate.now().plusDays(3))).findFirst().ifPresent(c -> {
                    c.setTriaje("Control anual. Tengo la presión algo alta por las mañanas."); citas.save(c);
                });
        int n = 0;
        for (Cita c : citas.findAll()) {
            if (!c.isAtendida() || c.getId().equals(atendida.getId()) || n >= 10) continue;
            if (encuestas.existsByCitaId(c.getId())) continue;
            encuestas.save(new Encuesta(c, 4 + (n % 2), 3 + (n % 3), 4 + ((n + 1) % 2), n % 4 == 0 ? "Muy buena atención, gracias." : null));
            n++;
        }
    }

    /** Citas pasadas (atendidas) y futuras de ejemplo, para que la lista y los reportes tengan datos desde el inicio. */
    private void sembrarHistorial(List<Paciente> ps) {
        LocalDate hoy = LocalDate.now();
        List<Doctor> docs = doctores.findAllByOrderByNombre();
        Set<String> usados = new HashSet<>();
        citas.findAll().forEach(c -> usados.add(c.getDoctor() + c.getFecha() + c.getHora()));
        String[][] dx = {
            {"Control de rutina.", "Z00.0", "Sin medicación. Control en 6 meses."},
            {"Hipertensión arterial.", "I10", "Control de la presión en casa. Dieta baja en sal."},
            {"Faringitis aguda.", "J02.9", "Ibuprofeno 400 mg cada 8 horas por 3 días."},
            {"Lumbalgia.", "M54.5", "Reposo relativo y calor local. Naproxeno 550 mg cada 12 horas por 5 días."},
            {"Resfriado común.", "J00", "Paracetamol 500 mg cada 8 horas si hay fiebre. Abundante líquido."},
            {"Gastritis.", "K29.7", "Omeprazol 20 mg en ayunas por 14 días. Evitar irritantes."},
        };
        for (int i = 0; i < 48; i++) {                       // ~6 meses hacia atrás: casi todas atendidas, algunas con inasistencia
            boolean noVino = i % 16 == 5;
            Cita c = sembrar(usados, docs, ps.get(i % ps.size()), hoy.minusDays(3L + i * 4L), i * 3 + i / 5, i * 7 + 2, noVino ? "No asistió" : "Atendida");
            if (c != null && !noVino) {
                String[] d = dx[i % dx.length];
                c.setDiagnostico(d[0]); c.setCie10(d[1]); c.setReceta(d[2]); c.setCodigoReceta(codigoDemo(c)); citas.save(c);
            }
        }
        for (int j = 0; j < 12; j++) {                       // próximas semanas
            Cita f = sembrar(usados, docs, ps.get((j + 2) % ps.size()), hoy.plusDays(2L + j * 2L), j * 5 + 1, j * 3 + 4, j % 3 == 0 ? "Confirmada" : "Pendiente");
            if (f != null && j % 4 == 1) { f.setModalidad("Virtual"); f.setConsultorio(null); f.setEnlaceVirtual("https://meet.jit.si/MedCare-DEMO" + j + "XK2P"); citas.save(f); }
        }
    }

    /** Crea una cita respetando los días y horarios del doctor y la restricción única; devuelve null si no cabe. */
    private Cita sembrar(Set<String> usados, List<Doctor> docs, Paciente p, LocalDate fecha, int idxDoctor, int idxHora, String estado) {
        Doctor d = docs.get(idxDoctor % docs.size());
        for (int k = 0; k < 7 && !d.atiende(fecha.getDayOfWeek()); k++) fecha = fecha.minusDays(1);   // al día de atención anterior
        List<LocalTime> horas = d.horarios();
        if (horas.isEmpty() || !d.atiende(fecha.getDayOfWeek())) return null;
        LocalTime hora = horas.get(idxHora % horas.size());
        if (!usados.add(d.getNombre() + fecha + hora)) return null;
        return g(new Cita(fecha, hora, d.getEspecialidad(), d.getNombre(), estado, p));
    }

    private void cargarDoctores() {
        doctores.save(doctor("Dr. Ramírez", "Medicina general", "Atención de adultos y control preventivo.", "ramirez.jpg", "1,2,3,4,5,6", 8, 18, 30));
        doctores.save(doctor("Dra. Huamán", "Medicina general", "Chequeos generales y seguimiento de enfermedades crónicas.", "huaman.jpg", "1,2,3,4,5,6", 8, 18, 30));
        doctores.save(doctor("Dra. Salas", "Cardiología", "Evaluación cardiovascular y control de la presión.", "salas.jpg", "1,2,3,4,5,6", 8, 18, 30));
        doctores.save(doctor("Dr. Chávez", "Cardiología", "Prevención y seguimiento de enfermedades del corazón.", "chavez.jpg", "1,2,3,4,5,6", 8, 18, 30));
        doctores.save(doctor("Dr. Paredes", "Odontología", "Odontología general y limpieza dental.", "paredes.jpg", "1,2,3,4,5,6", 8, 18, 30));
        doctores.save(doctor("Dra. Quispe", "Pediatría", "Controles de crecimiento y vacunación infantil.", "quispe.jpg", "1,2,3,4,5", 8, 16, 20));
        doctores.save(doctor("Dr. Vargas", "Dermatología", "Cuidado de la piel y tratamiento del acné.", "vargas.jpg", "1,3,5", 9, 18, 30));
        doctores.save(doctor("Dr. Montoya", "Ginecología", "Controles de rutina y salud de la mujer.", "montoya.jpg", "1,2,3,4,5,6", 8, 18, 30));
    }

    private Doctor doctor(String nombre, String esp, String desc, String foto, String dias, int ini, int fin, int dur) {
        Doctor d = new Doctor(nombre, esp, desc, foto);
        d.setDias(dias);
        d.setInicio(LocalTime.of(ini, 0));
        d.setFin(LocalTime.of(fin, 0));
        d.setDuracion(dur);
        d.setConsultorio(Catalogo.CONSULTORIOS.get(contadorConsultorio++ % Catalogo.CONSULTORIOS.size()));
        if (dur == 20) { d.setPausaInicio(LocalTime.of(12, 0)); d.setPausaFin(LocalTime.of(13, 0)); }   // Pediatría: almuerzo 12–13
        return d;
    }

    /** Guarda una cita de demostración con la tarifa del doctor; las atendidas quedan casi todas cobradas. */
    private Cita g(Cita c) {
        doctores.findByNombre(c.getDoctor()).ifPresent(d -> c.setPrecio(d.getPrecio()));
        c.setPagado(c.isAtendida() && c.getFecha().getDayOfMonth() % 4 != 0);
        if (c.isVirtual()) { if (c.getEnlaceVirtual() == null) c.setEnlaceVirtual("https://meet.jit.si/MedCare-DEMO" + c.getFecha().getDayOfYear() + "R7QM"); }
        else if (!c.isCancelada() && c.getConsultorio() == null) c.setConsultorio(salaLibre(c));
        Cita s = citas.save(c);
        if (s.isAtendida() && s.getReceta() != null && s.getCodigoReceta() == null) { s.setCodigoReceta(codigoDemo(s)); s = citas.save(s); }
        return s;
    }

    /** Consultorio libre para una cita de demostración (null si no hay; no debe impedir cargar los datos). */
    private String salaLibre(Cita c) {
        try { return consultorios.asignar(c.getDoctor(), c.getFecha(), c.getHora(), null); } catch (ReglaNegocioException e) { return null; }
    }

    /** Código estable y único (a partir del id) para las recetas de demostración; empieza con D. */
    private static String codigoDemo(Cita c) {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        long n = (c.getId() == null ? 0 : c.getId()) * 2654435761L + 12345678901L;
        StringBuilder sb = new StringBuilder("D");
        for (int i = 0; i < 9; i++) { sb.append(abc.charAt((int) (n % 32))); n /= 32; }
        return sb.toString();
    }

    /** Exámenes de demostración (imágenes sintéticas marcadas como DEMO) para la cita atendida de María. */
    private void adjuntarDemo(Cita c) {
        adjuntar(c, "demo/rx-torax.png", "rx-torax-demo.png", "Radiografía de tórax (DEMO)", "image/png");
        adjuntar(c, "demo/laboratorio.png", "laboratorio-demo.png", "Hemograma completo (DEMO)", "image/png");
        adjuntar(c, "demo/informe.pdf", "informe-demo.pdf", "Informe de laboratorio (DEMO)", "application/pdf");
    }

    private void adjuntar(Cita c, String recurso, String nombre, String descripcion, String tipo) {
        try (java.io.InputStream in = new org.springframework.core.io.ClassPathResource(recurso).getInputStream()) {
            examenes.save(new Examen(c, nombre, descripcion, tipo, in.readAllBytes(), "Demo", true));
        } catch (java.io.IOException | RuntimeException e) {
            // sin el archivo de ejemplo la demostración sigue funcionando
        }
    }

    /** Pone al día una base creada con una versión anterior: consultorios, códigos de receta y reinicio opcional de los 2 pasos. */
    private void migrar() {
        for (Doctor d : doctores.findAllByOrderByNombre()) {
            if (d.getConsultorio() == null) { d.setConsultorio(Catalogo.CONSULTORIOS.get(contadorConsultorio++ % Catalogo.CONSULTORIOS.size())); doctores.save(d); }
        }
        for (Cita c : citas.findAllByOrderByFechaAscHoraAsc()) {
            boolean cambio = false;
            if (c.isActiva() && !c.isVirtual() && c.getConsultorio() == null) { c.setConsultorio(salaLibre(c)); cambio = true; }
            if (c.isAtendida() && c.getReceta() != null && c.getCodigoReceta() == null) { c.setCodigoReceta(codigoDemo(c)); cambio = true; }
            if (cambio) citas.save(c);
        }
        // Si perdiste el celular de los 2 pasos: arranca una vez con la variable RESET_2FA=true.
        if ("true".equalsIgnoreCase(System.getenv("RESET_2FA"))) {
            for (Usuario u : usuarios.findAll()) {
                if (u.isTotpActivo() || u.getTotpSecreto() != null) { u.setTotpActivo(false); u.setTotpSecreto(null); usuarios.save(u); }
            }
        }
    }

    private Paciente crear(String nombre, String dni, String tel, LocalDate visita, String estado) {
        Paciente p = pacientes.save(new Paciente(nombre, dni, tel, visita, estado));
        usuarios.save(new Usuario(dni, encoder.encode("paciente123"), "PACIENTE", p));   // clave de demostración: no se obliga a cambiarla
        return p;
    }
}
