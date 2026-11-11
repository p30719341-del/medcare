package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Archivo adjunto a una cita (análisis, radiografía, informe). Se guarda en la base de datos. */
@Entity
public class Examen {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Cita cita;

    @Column(length = 100)
    private String nombre;          // nombre del archivo, ya depurado

    @Column(length = 100)
    private String descripcion;     // opcional: "Radiografía de tórax"

    private String tipo;            // application/pdf | image/png | image/jpeg (detectado por el contenido)
    private long tamano;
    private String subidoPor;       // Paciente | Doctor | Demo
    private LocalDateTime fecha = LocalDateTime.now();
    private Boolean simulado = false;   // true: archivo de demostración creado por la maqueta

    // Sin @Lob: así es bytea en PostgreSQL (con @Lob sería un "large object" que falla fuera de una transacción)
    @Column(length = 4_000_000)
    private byte[] contenido;

    public Examen() {}
    public Examen(Cita cita, String nombre, String descripcion, String tipo, byte[] contenido, String subidoPor, boolean simulado) {
        this.cita = cita; this.nombre = nombre; this.descripcion = descripcion; this.tipo = tipo;
        this.contenido = contenido; this.tamano = contenido.length; this.subidoPor = subidoPor; this.simulado = simulado;
    }

    public boolean isImagen() { return tipo != null && tipo.startsWith("image/"); }
    public String getTamanoTexto() { return tamano >= 1_000_000 ? String.format("%.1f MB", tamano / 1_000_000.0) : Math.max(1, tamano / 1000) + " KB"; }
    public String getTitulo() { return descripcion != null ? descripcion : nombre; }

    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getTipo() { return tipo; }
    public long getTamano() { return tamano; }
    public String getSubidoPor() { return subidoPor; }
    public LocalDateTime getFecha() { return fecha; }
    public boolean isSimulado() { return Boolean.TRUE.equals(simulado); }
    public byte[] getContenido() { return contenido; }
}
