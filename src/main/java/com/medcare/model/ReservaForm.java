package com.medcare.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/** Datos del formulario público de reserva de cita. */
public class ReservaForm {

    @NotBlank(message = "Ingresa tu nombre completo")
    private String nombre;

    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos")
    private String dni;

    @Pattern(regexp = "[0-9 +()-]{7,15}", message = "Ingresa un teléfono válido")
    private String telefono;

    @Email(message = "Correo no válido")
    @Size(max = 100, message = "Máximo 100 caracteres")
    private String email;     // opcional: si lo pones, te enviamos la confirmación

    @Size(max = 60, message = "Máximo 60 caracteres")
    private String familiar;  // opcional: si la cita es para un hijo, padre, etc.

    /** Presencial (por defecto) o Virtual. */
    private String modalidad = "Presencial";

    @NotBlank(message = "Elige una especialidad")
    private String especialidad;

    @NotBlank(message = "Elige un doctor")
    private String doctor;

    @NotNull(message = "Elige una fecha")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;

    @NotNull(message = "Elige un horario")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime hora;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = (email == null || email.isBlank()) ? null : email.trim(); }
    public String getFamiliar() { return familiar; }
    public void setFamiliar(String f) { this.familiar = (f == null || f.isBlank()) ? null : f.trim(); }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String m) { this.modalidad = "Virtual".equalsIgnoreCase(m) ? "Virtual" : "Presencial"; }
    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
    public String getDoctor() { return doctor; }
    public void setDoctor(String doctor) { this.doctor = doctor; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
}
