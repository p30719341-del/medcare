package com.medcare.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registro del propio paciente (/registro). */
public class RegistroForm {
    @NotBlank(message = "Ingresa tu nombre completo")
    @Size(max = 80, message = "Máximo 80 caracteres")
    private String nombre;
    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos")
    private String dni;
    @Pattern(regexp = "[0-9 +()-]{7,15}", message = "Ingresa un teléfono válido")
    private String telefono;
    @NotBlank(message = "Ingresa tu correo (lo usarás para recuperar tu contraseña)")
    @Email(message = "Correo no válido")
    @Size(max = 100, message = "Máximo 100 caracteres")
    private String email;
    private String clave;
    private String confirmar;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getConfirmar() { return confirmar; }
    public void setConfirmar(String confirmar) { this.confirmar = confirmar; }
}
