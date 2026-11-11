package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Persona que quiere una cita con un doctor en una fecha llena y pidió que le avisemos si se libera un horario. */
@Entity
public class Espera {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String doctor;
    private LocalDate fecha;
    private String nombre;
    private String dni;
    private String telefono;
    private String email;
    private LocalDateTime creada = LocalDateTime.now();
    private Boolean avisada = false;

    public Espera() {}
    public Espera(String doctor, LocalDate fecha, String nombre, String dni, String telefono, String email) {
        this.doctor = doctor; this.fecha = fecha; this.nombre = nombre; this.dni = dni; this.telefono = telefono;
        this.email = (email == null || email.isBlank()) ? null : email.trim();
    }

    public Long getId() { return id; }
    public String getDoctor() { return doctor; }
    public LocalDate getFecha() { return fecha; }
    public String getNombre() { return nombre; }
    public String getDni() { return dni; }
    public String getTelefono() { return telefono; }
    public String getEmail() { return email; }
    public LocalDateTime getCreada() { return creada; }
    public boolean isAvisada() { return Boolean.TRUE.equals(avisada); }
    public void setAvisada(boolean avisada) { this.avisada = avisada; }
}
