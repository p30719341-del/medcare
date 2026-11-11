package com.medcare.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Registro de quién hizo qué (auditoría). */
@Entity
public class Actividad {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime fecha = LocalDateTime.now();
    private String usuario;
    private String accion;
    @Column(length = 300)
    private String detalle;

    public Actividad() {}
    public Actividad(String usuario, String accion, String detalle) {
        this.usuario = usuario; this.accion = accion;
        this.detalle = detalle != null && detalle.length() > 300 ? detalle.substring(0, 300) : detalle;
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public String getUsuario() { return usuario; }
    public String getAccion() { return accion; }
    public String getDetalle() { return detalle; }
}
