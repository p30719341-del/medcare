package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Solicitud de atención enviada desde la portada (Laboratorio 12): datos + imagen de evidencia opcional. */
@Entity
public class Solicitud {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String nombre;

    @Column(length = 100, nullable = false)
    private String correo;

    @Column(length = 60, nullable = false)
    private String servicio;

    @Column(length = 500, nullable = false)
    private String descripcion;

    @Column(length = 20)
    private String estado = "Recibida";

    private LocalDateTime fecha = LocalDateTime.now();

    // Imagen de evidencia (opcional). Sin @Lob, igual que Examen, para que también funcione en PostgreSQL.
    @Column(length = 100)
    private String imagenNombre;
    @Column(length = 30)
    private String imagenTipo;
    private long imagenTamano;
    @Column(length = 3_000_000)
    private byte[] imagen;

    public Solicitud() {}

    public boolean tieneImagen() { return imagen != null && imagen.length > 0; }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v; }
    public String getCorreo() { return correo; }
    public void setCorreo(String v) { this.correo = v; }
    public String getServicio() { return servicio; }
    public void setServicio(String v) { this.servicio = v; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String v) { this.descripcion = v; }
    public String getEstado() { return estado; }
    public LocalDateTime getFecha() { return fecha; }
    public String getImagenNombre() { return imagenNombre; }
    public String getImagenTipo() { return imagenTipo; }
    public long getImagenTamano() { return imagenTamano; }
    public byte[] getImagen() { return imagen; }
    public void setImagen(String nombre, String tipo, byte[] bytes) {
        this.imagenNombre = nombre; this.imagenTipo = tipo; this.imagen = bytes; this.imagenTamano = bytes.length;
    }
}
