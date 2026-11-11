package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.model.Paciente;
import com.medcare.model.ReservaForm;
import com.medcare.repository.BloqueoDiaRepository;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Reglas de las citas: reservar, cancelar, reprogramar y registrar la atención. */
@Service
public class ReservaService {

    /** Resultado de una reserva: la cita creada y si se creó también la cuenta del paciente. */
    public static class Resultado {
        private final Cita cita;
        private final boolean cuentaNueva;
        public Resultado(Cita cita, boolean cuentaNueva) { this.cita = cita; this.cuentaNueva = cuentaNueva; }
        public Cita getCita() { return cita; }
        public boolean isCuentaNueva() { return cuentaNueva; }
    }

    private final CitaRepository citas;
    private final iPacienteService pacientes;
    private final DoctorRepository doctores;
    private final BloqueoDiaRepository bloqueos;
    private final EsperaService espera;
    private final ActividadService actividad;
    private final ConsultorioService consultorios;
    private static final java.security.SecureRandom AZAR = new java.security.SecureRandom();

    public ReservaService(CitaRepository citas, iPacienteService pacientes, DoctorRepository doctores,
                          BloqueoDiaRepository bloqueos, EsperaService espera, ActividadService actividad,
                          ConsultorioService consultorios) {
        this.citas = citas; this.pacientes = pacientes; this.doctores = doctores;
        this.bloqueos = bloqueos; this.espera = espera; this.actividad = actividad; this.consultorios = consultorios;
    }

    /** Texto al azar sin letras confusas (sin 0/O ni 1/I), para códigos y enlaces. */
    static String aleatorio(int largo) {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) sb.append(abc.charAt(AZAR.nextInt(abc.length())));
        return sb.toString();
    }

    /** Horarios libres de un doctor en una fecha (vacío si la fecha no es válida o el doctor no atiende). */
    public List<LocalTime> horasLibres(String doctorNombre, LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        Optional<Doctor> d = doctores.findByNombre(doctorNombre).filter(Doctor::isActivo);
        if (d.isEmpty() || fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(Catalogo.DIAS_MAX))
                || !d.get().atiende(fecha.getDayOfWeek()) || bloqueos.existsByDoctorAndFecha(doctorNombre, fecha)) {
            return List.of();
        }
        Set<LocalTime> ocupadas = citas.findByDoctorAndFecha(doctorNombre, fecha).stream()
                .filter(Cita::isActiva).map(Cita::getHora).collect(Collectors.toSet());
        LocalDateTime limite = LocalDateTime.now().plusMinutes(60);   // mínimo 1 hora de anticipación
        return d.get().horarios().stream()
                .filter(h -> !ocupadas.contains(h) && LocalDateTime.of(fecha, h).isAfter(limite))
                .toList();
    }

    /** Reglas que no se pueden expresar con anotaciones. Agrega los errores al BindingResult. */
    public void validar(ReservaForm f, BindingResult br) {
        Optional<Doctor> d = doctores.findByNombre(f.getDoctor()).filter(Doctor::isActivo);
        if (d.isEmpty() || !d.get().getEspecialidad().equals(f.getEspecialidad())) {
            br.rejectValue("doctor", "doctor.invalido", "Elige un doctor de la especialidad seleccionada.");
            return;
        }
        LocalDate hoy = LocalDate.now();
        if (f.getFecha().isBefore(hoy) || f.getFecha().isAfter(hoy.plusDays(Catalogo.DIAS_MAX))) {
            br.rejectValue("fecha", "fecha.rango", "Elige una fecha dentro de los próximos " + Catalogo.DIAS_MAX + " días.");
        } else if (bloqueos.existsByDoctorAndFecha(f.getDoctor(), f.getFecha())) {
            br.rejectValue("fecha", "fecha.bloqueada", "El doctor no atiende ese día. Elige otra fecha.");
        } else if (!d.get().atiende(f.getFecha().getDayOfWeek())) {
            br.rejectValue("fecha", "fecha.dia", "Ese día el doctor no atiende. Elige otra fecha.");
        } else if (!horasLibres(f.getDoctor(), f.getFecha()).contains(f.getHora())) {
            br.rejectValue("hora", "hora.ocupada", "Ese horario no está disponible. Elige otro.");
        }
        // Si el DNI ya existe, el teléfono debe coincidir (evita reservar a nombre de otra persona por error)
        pacientes.buscarPorDni(f.getDni()).ifPresent(p -> {
            if (!soloDigitos(p.getPhone()).equals(soloDigitos(f.getTelefono()))) {
                br.rejectValue("dni", "dni.registrado", "Ese DNI ya está registrado. Usa el teléfono con el que te registraste.");
            }
        });
    }

    @Transactional
    public Resultado reservar(ReservaForm f) {
        Optional<Paciente> existente = pacientes.buscarPorDni(f.getDni());
        boolean cuentaNueva = existente.isEmpty();
        Paciente p;
        if (cuentaNueva) {
            p = new Paciente(f.getNombre().trim(), f.getDni(), f.getTelefono().trim(), LocalDate.now(), "Pendiente");
            p.setEmail(f.getEmail());
            pacientes.save(p);   // también crea el acceso: usuario = DNI, clave inicial = DNI (debe cambiarla al ingresar)
        } else {
            p = existente.get();
            if (p.getEmail() == null && f.getEmail() != null && !f.getEmail().isBlank()) {
                p.setEmail(f.getEmail());
                pacientes.save(p);
            }
        }
        Cita nueva = new Cita(f.getFecha(), f.getHora(), f.getEspecialidad(), f.getDoctor(), "Pendiente", p);
        nueva.setFamiliar(f.getFamiliar());
        nueva.setModalidad(f.getModalidad());
        if (nueva.isVirtual()) nueva.setEnlaceVirtual("https://meet.jit.si/MedCare-" + aleatorio(14));   // sala única e impredecible
        else nueva.setConsultorio(consultorios.asignar(f.getDoctor(), f.getFecha(), f.getHora(), null));
        doctores.findByNombre(f.getDoctor()).ifPresent(d -> nueva.setPrecio(d.getPrecio()));
        Cita c = citas.save(nueva);
        actividad.registrar("Cita reservada", p.getName() + " con " + c.getDoctor() + " el " + c.getFecha() + " " + c.getHora());
        return new Resultado(c, cuentaNueva);
    }

    /** Cancela una cita. dueno = paciente que la pide (null si la cancela el administrador). */
    @Transactional
    public Cita cancelar(Long id, Paciente dueno) {
        Cita c = obtener(id, dueno);
        if (!c.isActiva()) throw new ReglaNegocioException("Esa cita ya no se puede cancelar.");
        if (dueno != null && !c.isCambiable()) {
            throw new ReglaNegocioException("Solo puedes cancelar con al menos " + Catalogo.HORAS_CAMBIO + " horas de anticipación. Llámanos para ayudarte.");
        }
        c.setEstado("Cancelada");
        c.setBloqueo(c.getId());     // libera el horario (ver restricción única en Cita)
        Cita g = citas.save(c);
        actividad.registrar("Cita cancelada", c.getPaciente().getName() + " con " + c.getDoctor() + " el " + c.getFecha() + " " + c.getHora());
        espera.avisar(c.getDoctor(), c.getFecha());
        return g;
    }

    /** Cambia fecha y hora de una cita; vuelve a quedar pendiente de confirmación. */
    @Transactional
    public Cita reprogramar(Long id, Paciente dueno, LocalDate fecha, LocalTime hora) {
        Cita c = obtener(id, dueno);
        if (!c.isActiva()) throw new ReglaNegocioException("Esa cita ya no se puede reprogramar.");
        if (dueno != null && !c.isCambiable()) {
            throw new ReglaNegocioException("Solo puedes reprogramar con al menos " + Catalogo.HORAS_CAMBIO + " horas de anticipación.");
        }
        if (!horasLibres(c.getDoctor(), fecha).contains(hora)) {
            throw new ReglaNegocioException("Ese horario no está disponible. Elige otro.");
        }
        LocalDate fechaAnterior = c.getFecha();
        if (!c.isVirtual()) c.setConsultorio(consultorios.asignar(c.getDoctor(), fecha, hora, c.getId()));
        c.setSobreturno(false);
        c.setBloqueo(0L);
        c.setFecha(fecha);
        c.setHora(hora);
        c.setEstado("Pendiente");
        c.setRecordada(false);
        Cita g = citas.saveAndFlush(c);   // saveAndFlush: si otra persona tomó el horario, falla aquí
        actividad.registrar("Cita reprogramada", c.getPaciente().getName() + " pasó al " + fecha + " " + hora);
        espera.avisar(c.getDoctor(), fechaAnterior);
        return g;
    }

    @Transactional
    public Cita confirmar(Long id) {
        Cita c = obtener(id, null);
        if (!c.isActiva()) throw new ReglaNegocioException("Solo se pueden confirmar citas pendientes.");
        c.setEstado("Confirmada");
        actividad.registrar("Cita confirmada", c.getPaciente().getName() + " con " + c.getDoctor() + " el " + c.getFecha() + " " + c.getHora());
        return citas.save(c);
    }

    @Transactional
    public Cita registrarAtencion(Long id, String diagnostico, String receta) { return registrarAtencion(id, diagnostico, receta, null); }

    /** Registra el diagnóstico y la receta de una cita ya realizada. soloDoctor: si lo atiende un doctor, solo puede con sus propias citas. */
    @Transactional
    public Cita registrarAtencion(Long id, String diagnostico, String receta, String soloDoctor) {
        return registrarAtencion(id, diagnostico, receta, soloDoctor, null, null, null, null, false);
    }

    /** Igual que el anterior; con conFicha=true también guarda la ficha clínica del paciente (alergias, antecedentes, peso, talla). */
    @Transactional
    public Cita registrarAtencion(Long id, String diagnostico, String receta, String soloDoctor,
                                  String alergias, String antecedentes, String peso, String talla, boolean conFicha) {
        return registrarAtencion(id, diagnostico, receta, soloDoctor, alergias, antecedentes, peso, talla, conFicha, null, null);
    }

    /** Igual que el anterior; además guarda el código CIE-10 del diagnóstico y, si el doctor pide un control, en cuántos días. */
    @Transactional
    public Cita registrarAtencion(Long id, String diagnostico, String receta, String soloDoctor,
                                  String alergias, String antecedentes, String peso, String talla, boolean conFicha,
                                  String cie10, String controlDias) {
        Cita c = obtener(id, null);
        if (cie10 != null && !cie10.isBlank() && !Cie10.formatoValido(cie10)) {
            throw new ReglaNegocioException("El código CIE-10 no es válido (ejemplo: J02.9).");
        }
        Integer control = entero(controlDias);
        if (control != null && (control < 1 || control > 365)) throw new ReglaNegocioException("El control debe ser entre 1 y 365 días.");
        c.setCie10(cie10);
        c.setControlDias(control);
        verificarDoctor(c, soloDoctor);
        if (c.isCancelada()) throw new ReglaNegocioException("No se puede registrar la atención de una cita cancelada.");
        if (c.getFecha().isAfter(LocalDate.now())) throw new ReglaNegocioException("Aún no es la fecha de esta cita.");
        c.setDiagnostico(recortar(diagnostico, 1000));
        c.setReceta(recortar(receta, 2000));
        c.setEstado("Atendida");
        if (c.getCodigoReceta() == null) c.setCodigoReceta(aleatorio(10));     // va en el QR de la receta
        actividad.registrar("Atención registrada", c.getPaciente().getName() + " con " + c.getDoctor() + " el " + c.getFecha());
        if (conFicha) {
            pacientes.actualizarFicha(c.getPaciente().getId(), recortar(alergias, 300), recortar(antecedentes, 500), numero(peso), entero(talla));
        }
        return citas.save(c);
    }

    /**
     * Sobreturno: agrega un paciente extra en un horario que ya está ocupado (lo hace el administrador).
     * Máximo Catalogo.SOBRETURNOS_MAX por doctor y día. Queda confirmado y usa el mismo consultorio.
     */
    @Transactional
    public Cita sobreturno(Long pacienteId, String doctorNombre, LocalDate fecha, LocalTime hora) {
        Doctor d = doctores.findByNombre(doctorNombre).filter(Doctor::isActivo)
                .orElseThrow(() -> new ReglaNegocioException("Elige un doctor."));
        Paciente p = pacientes.listarId(pacienteId).orElseThrow(() -> new ReglaNegocioException("Elige un paciente."));
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(Catalogo.DIAS_MAX))) throw new ReglaNegocioException("Elige una fecha dentro de los próximos " + Catalogo.DIAS_MAX + " días.");
        if (!d.atiende(fecha.getDayOfWeek()) || bloqueos.existsByDoctorAndFecha(doctorNombre, fecha)) throw new ReglaNegocioException("Ese día el doctor no atiende.");
        if (!d.horarios().contains(hora)) throw new ReglaNegocioException("Ese no es un horario del doctor.");
        List<Cita> delDia = citas.findByDoctorAndFecha(doctorNombre, fecha).stream().filter(Cita::isActiva).toList();
        if (delDia.stream().noneMatch(x -> x.getHora().equals(hora))) {
            throw new ReglaNegocioException("Ese horario está libre: reserva una cita normal en vez de un sobreturno.");
        }
        if (delDia.stream().filter(Cita::isSobreturno).count() >= Catalogo.SOBRETURNOS_MAX) {
            throw new ReglaNegocioException("El doctor ya tiene " + Catalogo.SOBRETURNOS_MAX + " sobreturnos ese día.");
        }
        if (delDia.stream().anyMatch(x -> x.getPaciente().getId().equals(p.getId()))) {
            throw new ReglaNegocioException("Ese paciente ya tiene una cita con el doctor ese día.");
        }
        Cita c = new Cita(fecha, hora, d.getEspecialidad(), d.getNombre(), "Confirmada", p);
        c.setSobreturno(true);
        c.setBloqueo(-Math.abs(System.nanoTime()));           // no choca con la restricción única del horario
        c.setPrecio(d.getPrecio());
        c.setConsultorio(consultorios.asignar(d.getNombre(), fecha, hora, null));
        Cita g = citas.save(c);
        actividad.registrar("Sobreturno", p.getName() + " con " + d.getNombre() + " el " + fecha + " " + hora);
        return g;
    }

    /** Marca que el paciente no llegó. Solo citas pendientes o confirmadas cuya hora ya pasó. soloDoctor: si lo marca un doctor, solo en sus citas. */
    @Transactional
    public Cita marcarNoAsistio(Long id, String soloDoctor) {
        Cita c = obtener(id, null);
        verificarDoctor(c, soloDoctor);
        if (!c.isActiva()) throw new ReglaNegocioException("Solo se puede marcar inasistencia en citas pendientes o confirmadas.");
        if (c.getFecha().atTime(c.getHora()).isAfter(LocalDateTime.now())) throw new ReglaNegocioException("La cita aún no ocurre.");
        c.setEstado("No asistió");
        actividad.registrar("No asistió", c.getPaciente().getName() + " con " + c.getDoctor() + " el " + c.getFecha() + " " + c.getHora());
        return citas.save(c);
    }

    /** El paciente cuenta sus síntomas antes de la cita (triaje). Solo citas activas que aún no pasaron. */
    @Transactional
    public Cita guardarTriaje(Long id, Paciente dueno, String texto) {
        Cita c = obtener(id, dueno);
        if (!c.isActiva() || c.getFecha().isBefore(LocalDate.now())) throw new ReglaNegocioException("Ya no puedes agregar síntomas a esa cita.");
        String limpio = recortar(texto, 800);
        if (limpio == null) throw new ReglaNegocioException("Escribe tus síntomas.");
        c.setTriaje(limpio);
        actividad.registrar(dueno.getName(), "Triaje enviado", "cita del " + c.getFecha() + " con " + c.getDoctor());
        return citas.save(c);
    }

    /** Cancelación hecha por el doctor (solo sus citas). */
    @Transactional
    public Cita cancelarComoDoctor(Long id, String doctor) {
        verificarDoctor(obtener(id, null), doctor);
        return cancelar(id, null);
    }

    /** Marca la cita como cobrada o por cobrar (pago simulado). */
    @Transactional
    public Cita alternarPago(Long id) {
        Cita c = obtener(id, null);
        if (c.isCancelada()) throw new ReglaNegocioException("Una cita cancelada no se cobra.");
        c.setPagado(!c.isPagado());
        actividad.registrar(c.isPagado() ? "Pago registrado" : "Pago anulado", c.getPaciente().getName() + " · S/ " + c.getPrecio());
        return citas.save(c);
    }

    public Cita obtenerCita(Long id) { return obtener(id, null); }

    /** Lanza ReglaNegocioException si la cita no es de ese doctor. */
    public void verificarPropiedad(Long id, String doctor) { verificarDoctor(obtener(id, null), doctor); }

    private static void verificarDoctor(Cita c, String doctor) {
        if (doctor != null && !doctor.equals(c.getDoctor())) throw new ReglaNegocioException("No encontramos esa cita.");
    }

    private Cita obtener(Long id, Paciente dueno) {
        Cita c = citas.findById(id).orElseThrow(() -> new ReglaNegocioException("No encontramos esa cita."));
        if (dueno != null && !c.getPaciente().getId().equals(dueno.getId())) {
            throw new ReglaNegocioException("No encontramos esa cita.");    // misma respuesta para no revelar citas ajenas
        }
        return c;
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t);
    }

    private static Double numero(String s) {
        try { return s == null || s.isBlank() ? null : Double.valueOf(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    private static Integer entero(String s) {
        try { return s == null || s.isBlank() ? null : Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String soloDigitos(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }
}
