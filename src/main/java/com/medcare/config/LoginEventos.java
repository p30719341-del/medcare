package com.medcare.config;

import com.medcare.repository.UsuarioRepository;
import com.medcare.service.ActividadService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Bloquea la cuenta 15 minutos después de 5 contraseñas incorrectas seguidas. */
@Component
public class LoginEventos {

    static final int MAX_INTENTOS = 5;
    static final int MINUTOS_BLOQUEO = 15;

    private final UsuarioRepository usuarios;
    private final ActividadService actividad;

    public LoginEventos(UsuarioRepository usuarios, ActividadService actividad) {
        this.usuarios = usuarios; this.actividad = actividad;
    }

    @EventListener
    public void fallo(AuthenticationFailureBadCredentialsEvent e) {
        String nombre = e.getAuthentication().getName();
        usuarios.findByUsername(nombre).ifPresent(u -> {
            int n = u.getIntentosFallidos() + 1;
            if (n >= MAX_INTENTOS) {
                u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
                n = 0;
                actividad.registrar(nombre, "Cuenta bloqueada", MAX_INTENTOS + " intentos fallidos: bloqueo de " + MINUTOS_BLOQUEO + " minutos");
            }
            u.setIntentosFallidos(n);
            usuarios.save(u);
        });
    }

    @EventListener
    public void ok(AuthenticationSuccessEvent e) {
        usuarios.findByUsername(e.getAuthentication().getName()).ifPresent(u -> {
            if (u.getIntentosFallidos() != 0 || u.getBloqueadoHasta() != null) {
                u.setIntentosFallidos(0);
                u.setBloqueadoHasta(null);
                usuarios.save(u);
            }
        });
    }
}
