package com.medcare.model;

import jakarta.persistence.*;

/** Receta frecuente que un doctor guarda para reutilizarla al atender (ej. "Faringitis aguda"). */
@Entity
public class PlantillaReceta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del doctor dueño de la plantilla. */
    @Column(nullable = false)
    private String doctor;

    @Column(length = 80, nullable = false)
    private String titulo;

    @Column(length = 10)
    private String cie10;

    @Column(length = 1000)
    private String diagnostico;

    @Column(length = 2000)
    private String receta;

    public PlantillaReceta() {}
    public PlantillaReceta(String doctor, String titulo, String cie10, String diagnostico, String receta) {
        this.doctor = doctor; this.titulo = titulo; this.cie10 = cie10; this.diagnostico = diagnostico; this.receta = receta;
    }

    public Long getId() { return id; }
    public String getDoctor() { return doctor; }
    public String getTitulo() { return titulo; }
    public String getCie10() { return cie10; }
    public String getDiagnostico() { return diagnostico; }
    public String getReceta() { return receta; }
}
