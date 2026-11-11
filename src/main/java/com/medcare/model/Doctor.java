package com.medcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Doctor de la clínica con su horario de atención (editable desde el panel de administración). */
@Entity
public class Doctor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ingresa el nombre del doctor")
    @Size(max = 60, message = "Máximo 60 caracteres")
    @Column(unique = true, nullable = false)
    private String nombre;

    @NotBlank(message = "Elige la especialidad")
    private String especialidad;

    @Size(max = 200, message = "Máximo 200 caracteres")
    private String descripcion;

    /** Nombre del archivo dentro de static/img/doctores (por ejemplo ramirez.jpg). */
    private String foto;

    /** Días de atención como números ISO separados por coma: 1 = lunes ... 7 = domingo. */
    private String dias = "1,2,3,4,5,6";

    @NotNull(message = "Indica la hora de inicio")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime inicio = LocalTime.of(8, 0);

    @NotNull(message = "Indica la hora de fin")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime fin = LocalTime.of(18, 0);

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pausaInicio = LocalTime.of(13, 0);

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime pausaFin = LocalTime.of(14, 0);

    @Min(value = 10, message = "Mínimo 10 minutos")
    @Max(value = 120, message = "Máximo 120 minutos")
    private int duracion = 30;

    /** Consultorio preferido (ej. "C-101"); si está ocupado a esa hora la cita usa otro libre. */
    @Column(length = 20)
    private String consultorio;

    @Min(value = 0, message = "No puede ser negativo")
    @Max(value = 2000, message = "Máximo 2000")
    private Integer precio = 80;      // tarifa de la consulta en soles

    private boolean activo = true;

    public Doctor() {}

    public Doctor(String nombre, String especialidad, String descripcion, String foto) {
        this.nombre = nombre; this.especialidad = especialidad; this.descripcion = descripcion; this.foto = foto;
    }

    /** Horarios de inicio de cada cita, sin contar la pausa (por ejemplo 08:00, 08:30 ... 12:30, 14:00 ...). */
    public List<LocalTime> horarios() {
        List<LocalTime> r = new ArrayList<>();
        if (inicio == null || fin == null || duracion <= 0) return r;
        LocalTime t = inicio;
        while (true) {
            LocalTime t2 = t.plusMinutes(duracion);
            if (!t2.isAfter(t) || t2.isAfter(fin)) break;   // evita pasar de medianoche
            boolean enPausa = pausaInicio != null && pausaFin != null && t.isBefore(pausaFin) && t2.isAfter(pausaInicio);
            if (!enPausa) r.add(t);
            t = t2;
        }
        return r;
    }

    public boolean atiende(DayOfWeek dia) { return atiendeNumero(dia.getValue()); }

    public boolean atiendeNumero(int n) {
        if (dias == null) return false;
        for (String s : dias.split(",")) if (s.trim().equals(String.valueOf(n))) return true;
        return false;
    }

    /** Resumen legible de los días de atención, por ejemplo "Lun–Sáb". */
    public String resumenDias() {
        String[] n = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        List<String> r = new ArrayList<>();
        for (int i = 1; i <= 7; i++) if (atiendeNumero(i)) r.add(n[i - 1]);
        if (r.isEmpty()) return "Sin días";
        if (r.size() == 7) return "Todos los días";
        if (r.size() > 2 && isSeguido()) return r.get(0) + "–" + r.get(r.size() - 1);
        return String.join(", ", r);
    }

    private boolean isSeguido() {
        int primero = 0, cuenta = 0;
        for (int i = 1; i <= 7; i++) if (atiendeNumero(i)) { if (primero == 0) primero = i; cuenta++; }
        for (int i = primero; i < primero + cuenta; i++) if (!atiendeNumero(i)) return false;
        return true;
    }

    public String getFotoArchivo() { return foto == null || foto.isBlank() ? "default.svg" : foto; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }
    public String getDias() { return dias; }
    public void setDias(String dias) { this.dias = dias; }
    public LocalTime getInicio() { return inicio; }
    public void setInicio(LocalTime inicio) { this.inicio = inicio; }
    public LocalTime getFin() { return fin; }
    public void setFin(LocalTime fin) { this.fin = fin; }
    public LocalTime getPausaInicio() { return pausaInicio; }
    public void setPausaInicio(LocalTime pausaInicio) { this.pausaInicio = pausaInicio; }
    public LocalTime getPausaFin() { return pausaFin; }
    public void setPausaFin(LocalTime pausaFin) { this.pausaFin = pausaFin; }
    public int getDuracion() { return duracion; }
    public void setDuracion(int duracion) { this.duracion = duracion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getConsultorio() { return consultorio; }
    public void setConsultorio(String c) { this.consultorio = (c == null || c.isBlank()) ? null : c.trim(); }
    public int getPrecio() { return precio == null ? 80 : precio; }
    public void setPrecio(Integer precio) { this.precio = precio; }
}
