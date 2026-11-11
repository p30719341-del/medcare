package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Reseña (1 a 5 estrellas) que un paciente deja sobre una cita atendida. Una por cita. */
@Entity
public class Resena {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Cita cita;

    private String doctor;
    private int estrellas;

    @Column(length = 300)
    private String comentario;

    private String autor;      // nombre y apellido abreviado, por ejemplo "María L."
    private LocalDate fecha;

    public Resena() {}

    public Resena(Cita cita, int estrellas, String comentario, String autor) {
        this.cita = cita; this.doctor = cita.getDoctor(); this.estrellas = estrellas;
        this.comentario = comentario; this.autor = autor; this.fecha = LocalDate.now();
    }

    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public String getDoctor() { return doctor; }
    public int getEstrellas() { return estrellas; }
    /** Por ejemplo "★★★★☆". */
    public String getEstrellasTexto() { return "★".repeat(estrellas) + "☆".repeat(5 - estrellas); }
    public String getComentario() { return comentario; }
    public String getAutor() { return autor; }
    public LocalDate getFecha() { return fecha; }
}
