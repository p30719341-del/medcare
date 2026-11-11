package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mensaje del chat entre el paciente y su doctor, asociado a una cita. El texto se guarda cifrado. */
@Entity
public class Mensaje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Cita cita;

    /** "Paciente" o "Doctor". */
    @Column(length = 10)
    private String autor;

    @Convert(converter = com.medcare.config.CifradoConverter.class)
    @Column(length = 3000)
    private String texto;

    private LocalDateTime fecha = LocalDateTime.now();

    public Mensaje() {}
    public Mensaje(Cita cita, String autor, String texto) { this.cita = cita; this.autor = autor; this.texto = texto; }

    public boolean isDelPaciente() { return "Paciente".equals(autor); }
    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public String getAutor() { return autor; }
    public String getTexto() { return texto; }
    public LocalDateTime getFecha() { return fecha; }
}
