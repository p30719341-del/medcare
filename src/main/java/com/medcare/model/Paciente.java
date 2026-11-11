package com.medcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Entity
public class Paciente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ingresa el nombre completo")
    private String name;

    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos")
    @Column(unique = true)
    private String dni;

    @NotBlank(message = "Ingresa un teléfono")
    private String phone;

    @NotNull(message = "Selecciona la fecha de la última visita")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate visit;

    @Email(message = "Correo no válido")
    @Size(max = 100)
    private String email;

    private String status = "Activo";

    /** Ficha clínica (la completa el doctor al atender). */
    @Size(max = 300) @Convert(converter = com.medcare.config.CifradoConverter.class) @Column(length = 1000) private String alergias;
    @Size(max = 500) @Convert(converter = com.medcare.config.CifradoConverter.class) @Column(length = 2000) private String antecedentes;
    private Double peso;      // kg
    private Integer talla;    // cm

    public Paciente() {}
    public Paciente(String name, String dni, String phone, LocalDate visit, String status) {
        this.name = name; this.dni = dni; this.phone = phone; this.visit = visit; this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getVisit() { return visit; }
    public void setVisit(LocalDate visit) { this.visit = visit; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = (email == null || email.isBlank()) ? null : email.trim(); }
    public String getAlergias() { return alergias; }
    public void setAlergias(String a) { this.alergias = (a == null || a.isBlank()) ? null : a.trim(); }
    public String getAntecedentes() { return antecedentes; }
    public void setAntecedentes(String a) { this.antecedentes = (a == null || a.isBlank()) ? null : a.trim(); }
    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }
    public Integer getTalla() { return talla; }
    public void setTalla(Integer talla) { this.talla = talla; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
