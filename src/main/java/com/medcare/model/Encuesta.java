package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Encuesta de satisfacción de una cita atendida (una por cita): tres notas de 1 a 5 y una sugerencia. */
@Entity
public class Encuesta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Cita cita;

    private String doctor;
    private int atencion;
    private int puntualidad;
    private int instalaciones;

    @Column(length = 300)
    private String sugerencia;

    private LocalDate fecha = LocalDate.now();

    public Encuesta() {}
    public Encuesta(Cita cita, int atencion, int puntualidad, int instalaciones, String sugerencia) {
        this.cita = cita; this.doctor = cita.getDoctor();
        this.atencion = atencion; this.puntualidad = puntualidad; this.instalaciones = instalaciones;
        this.sugerencia = sugerencia;
    }

    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public String getDoctor() { return doctor; }
    public int getAtencion() { return atencion; }
    public int getPuntualidad() { return puntualidad; }
    public int getInstalaciones() { return instalaciones; }
    public String getSugerencia() { return sugerencia; }
    public LocalDate getFecha() { return fecha; }
}
