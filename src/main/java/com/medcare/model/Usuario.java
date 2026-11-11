package com.medcare.model;

import jakarta.persistence.*;

@Entity
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt

    @Column(nullable = false)
    private String rol; // ADMIN | DOCTOR | PACIENTE

    /** true cuando la clave es la inicial (el DNI) y debe cambiarse en el primer ingreso. */
    private Boolean debeCambiarClave = false;
    @OneToOne
    private Paciente paciente; // null para el administrador y los doctores

    @ManyToOne
    private Doctor doctor;     // solo para el rol DOCTOR

    @Column(length = 100)
    private String email;      // para recuperar la contraseña (admin y doctores; el paciente usa el de su ficha)

    /** Bloqueo temporal por intentos fallidos de ingreso. */
    private Integer intentosFallidos = 0;
    private java.time.LocalDateTime bloqueadoHasta;

    /** Verificación en dos pasos (TOTP, compatible con Google Authenticator). */
    @Column(length = 64)
    private String totpSecreto;
    private Boolean totpActivo = false;

    /** Enlace de recuperación de contraseña (vence en 1 hora). */
    private String tokenReset;
    private java.time.LocalDateTime tokenVence;

    public Usuario() {}
    public Usuario(String username, String password, String rol, Paciente paciente) {
        this.username = username; this.password = password; this.rol = rol; this.paciente = paciente;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public boolean isDebeCambiarClave() { return Boolean.TRUE.equals(debeCambiarClave); }
    public void setDebeCambiarClave(boolean v) { this.debeCambiarClave = v; }
    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = (email == null || email.isBlank()) ? null : email.trim(); }
    public int getIntentosFallidos() { return intentosFallidos == null ? 0 : intentosFallidos; }
    public void setIntentosFallidos(int n) { this.intentosFallidos = n; }
    public java.time.LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(java.time.LocalDateTime t) { this.bloqueadoHasta = t; }
    public boolean isBloqueado() { return bloqueadoHasta != null && bloqueadoHasta.isAfter(java.time.LocalDateTime.now()); }
    public String getTokenReset() { return tokenReset; }
    public void setTokenReset(String t) { this.tokenReset = t; }
    public java.time.LocalDateTime getTokenVence() { return tokenVence; }
    public void setTokenVence(java.time.LocalDateTime t) { this.tokenVence = t; }
    public String getTotpSecreto() { return totpSecreto; }
    public void setTotpSecreto(String s) { this.totpSecreto = s; }
    public boolean isTotpActivo() { return Boolean.TRUE.equals(totpActivo); }
    public void setTotpActivo(boolean a) { this.totpActivo = a; }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
}
