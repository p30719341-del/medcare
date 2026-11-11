package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"doctor", "fecha", "hora", "bloqueo"}))
public class Cita {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate fecha;
    private LocalTime hora;
    private String especialidad;
    private String doctor;
    private String estado; // Pendiente | Confirmada | Atendida | Cancelada
    /** 0 mientras la cita está activa; al cancelarla toma el valor de su id para liberar el horario. */
    private Long bloqueo = 0L;
    /** Diagnóstico y receta se guardan cifrados (ver util.Cifrado). */
    @Convert(converter = com.medcare.config.CifradoConverter.class)
    @Column(length = 3000)
    private String diagnostico;
    @Convert(converter = com.medcare.config.CifradoConverter.class)
    @Column(length = 6000)
    private String receta;
    /** Código CIE-10 del diagnóstico principal (ej. J02.9). */
    @Column(length = 10)
    private String cie10;
    /** Síntomas que el paciente cuenta antes de la cita (triaje). Cifrado. */
    @Convert(converter = com.medcare.config.CifradoConverter.class)
    @Column(length = 3000)
    private String triaje;
    /** Días después de esta cita en que el doctor pide un control (null = sin control). */
    private Integer controlDias;
    /** Cuándo se creó la solicitud (para avisar al admin de citas nuevas). */
    private java.time.LocalDateTime creada = java.time.LocalDateTime.now();

    /** Si la cita es para un familiar del titular (ej. "Mateo, hijo"). */
    private String familiar;
    /** Tarifa en soles al reservar y si ya se cobró (pago simulado). */
    private Integer precio = 0;
    private Boolean pagado = false;
    /** Presencial o Virtual (videollamada). Para las virtuales se genera un enlace único de Jitsi Meet. */
    private String modalidad = "Presencial";
    private String enlaceVirtual;
    /** Código que va en el QR de la receta; permite verificarla en /verificar/{codigo}. */
    @Column(length = 40)
    private String codigoReceta;
    /** Consultorio asignado automáticamente (sin choques con otras citas a la misma hora). */
    private String consultorio;
    /** true si es un paciente extra en un horario que ya estaba ocupado. */
    private Boolean sobreturno = false;
    /** true cuando ya se envió el recordatorio automático. */
    private Boolean recordada = false;

    @ManyToOne(optional = false)
    private Paciente paciente;

    public Cita() {}
    public Cita(LocalDate fecha, LocalTime hora, String especialidad, String doctor, String estado, Paciente paciente) {
        this.fecha = fecha; this.hora = hora; this.especialidad = especialidad;
        this.doctor = doctor; this.estado = estado; this.paciente = paciente;
    }

    /** Pendiente o Confirmada: ocupa el horario. */
    public boolean isActiva() { return "Pendiente".equals(estado) || "Confirmada".equals(estado); }
    public boolean isAtendida() { return "Atendida".equals(estado); }
    public boolean isCancelada() { return "Cancelada".equals(estado); }
    public boolean isNoAsistio() { return "No asistió".equals(estado); }
    /** El paciente puede cancelar o reprogramar si falta al menos CAMBIO horas. */
    public boolean isCambiable() {
        return isActiva() && fecha != null && hora != null
                && fecha.atTime(hora).isAfter(java.time.LocalDateTime.now().plusHours(com.medcare.service.Catalogo.HORAS_CAMBIO));
    }

    /** Se puede registrar la atención: no está cancelada ni marcada como inasistencia y la fecha ya llegó. */
    public boolean isAtendible() { return !isCancelada() && !isNoAsistio() && fecha != null && !fecha.isAfter(LocalDate.now()); }
    /** Clase CSS de la etiqueta de estado: wait (pendiente), ok (confirmada o atendida), bad (cancelada). */
    public String getClaseEstado() { return "Pendiente".equals(estado) ? "wait" : ("Cancelada".equals(estado) || "No asistió".equals(estado)) ? "bad" : "ok"; }

    public String getCie10() { return cie10; }
    public void setCie10(String c) { this.cie10 = (c == null || c.isBlank()) ? null : c.trim().toUpperCase(); }
    public String getTriaje() { return triaje; }
    public void setTriaje(String t) { this.triaje = (t == null || t.isBlank()) ? null : t.trim(); }
    public Integer getControlDias() { return controlDias; }
    public void setControlDias(Integer d) { this.controlDias = (d == null || d <= 0) ? null : d; }
    /** Fecha sugerida para el control, o null. */
    public LocalDate getControlFecha() { return controlDias == null || fecha == null ? null : fecha.plusDays(controlDias); }
    public java.time.LocalDateTime getCreada() { return creada; }
    public void setCreada(java.time.LocalDateTime c) { this.creada = c; }

    public Long getId() { return id; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
    public String getDoctor() { return doctor; }
    public void setDoctor(String doctor) { this.doctor = doctor; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Long getBloqueo() { return bloqueo; }
    public void setBloqueo(Long bloqueo) { this.bloqueo = bloqueo; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getReceta() { return receta; }
    public void setReceta(String receta) { this.receta = receta; }
    public String getFamiliar() { return familiar; }
    public void setFamiliar(String familiar) { this.familiar = (familiar == null || familiar.isBlank()) ? null : familiar.trim(); }
    public int getPrecio() { return precio == null ? 0 : precio; }
    public void setPrecio(int precio) { this.precio = precio; }
    public boolean isPagado() { return Boolean.TRUE.equals(pagado); }
    public void setPagado(boolean pagado) { this.pagado = pagado; }
    public boolean isRecordada() { return Boolean.TRUE.equals(recordada); }
    public void setRecordada(boolean r) { this.recordada = r; }
    public String getModalidad() { return modalidad == null ? "Presencial" : modalidad; }
    public void setModalidad(String m) { this.modalidad = "Virtual".equalsIgnoreCase(m) ? "Virtual" : "Presencial"; }
    public boolean isVirtual() { return "Virtual".equals(modalidad); }
    public String getEnlaceVirtual() { return enlaceVirtual; }
    public void setEnlaceVirtual(String e) { this.enlaceVirtual = e; }
    public String getCodigoReceta() { return codigoReceta; }
    public void setCodigoReceta(String c) { this.codigoReceta = c; }
    public String getConsultorio() { return consultorio; }
    public void setConsultorio(String c) { this.consultorio = c; }
    public boolean isSobreturno() { return Boolean.TRUE.equals(sobreturno); }
    public void setSobreturno(boolean s) { this.sobreturno = s; }
    /** Número de boleta de demostración, por ejemplo B001-000042. */
    public String getNumeroBoleta() { return String.format("B001-%06d", id == null ? 0 : id); }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
}
