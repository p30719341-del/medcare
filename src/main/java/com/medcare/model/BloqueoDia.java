package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Día en que un doctor no atiende (vacaciones, congreso, emergencia). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"doctor", "fecha"}))
public class BloqueoDia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String doctor;       // nombre del doctor (igual que en Cita)
    private LocalDate fecha;
    @Column(length = 120)
    private String motivo;

    public BloqueoDia() {}
    public BloqueoDia(String doctor, LocalDate fecha, String motivo) { this.doctor = doctor; this.fecha = fecha; this.motivo = motivo; }

    public Long getId() { return id; }
    public String getDoctor() { return doctor; }
    public LocalDate getFecha() { return fecha; }
    public String getMotivo() { return motivo; }
}
