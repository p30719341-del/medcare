package com.medcare.service;

import com.medcare.model.Usuario;
import com.medcare.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Recuperar contraseña: se envía un enlace de un solo uso que vence en 1 hora. */
@Service
public class ClaveService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final NotificacionService notificaciones;
    private final ActividadService actividad;
    private final String baseUrl;
    private final SecureRandom azar = new SecureRandom();

    public ClaveService(UsuarioRepository usuarios, PasswordEncoder encoder, NotificacionService notificaciones,
                        ActividadService actividad, @Value("${medcare.base-url:http://localhost:8080}") String baseUrl) {
        this.usuarios = usuarios; this.encoder = encoder; this.notificaciones = notificaciones;
        this.actividad = actividad; this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Siempre responde igual (exista o no la cuenta) para no revelar quién está registrado. */
    @Transactional
    public void solicitar(String dato) {
        String d = dato == null ? "" : dato.trim();
        if (d.isEmpty()) return;
        Optional<Usuario> u = usuarios.findByUsername(d);
        if (u.isEmpty() && d.contains("@")) {
            u = usuarios.findByPacienteEmailIgnoreCase(d);
            if (u.isEmpty()) u = usuarios.findByEmailIgnoreCase(d);
        }
        if (u.isEmpty()) return;
        Usuario x = u.get();
        String email = x.getPaciente() != null ? x.getPaciente().getEmail() : x.getEmail();
        if (email == null || email.isBlank()) return;
        byte[] b = new byte[32];
        azar.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        x.setTokenReset(token);
        x.setTokenVence(LocalDateTime.now().plusHours(1));
        usuarios.save(x);
        String nombre = x.getPaciente() != null ? x.getPaciente().getName() : x.getUsername();
        notificaciones.claveRecuperacion(email, nombre, baseUrl + "/recuperar/" + token);
        actividad.registrar(x.getUsername(), "Recuperación de contraseña solicitada", "");
    }

    public boolean tokenValido(String token) {
        return token != null && !token.isBlank() && usuarios.findByTokenReset(token)
                .filter(u -> u.getTokenVence() != null && u.getTokenVence().isAfter(LocalDateTime.now())).isPresent();
    }

    /** Devuelve la lista de errores; vacía si la contraseña se cambió. */
    @Transactional
    public List<String> restablecer(String token, String nueva, String confirmar) {
        Optional<Usuario> u = usuarios.findByTokenReset(token)
                .filter(x -> x.getTokenVence() != null && x.getTokenVence().isAfter(LocalDateTime.now()));
        if (u.isEmpty()) return List.of("El enlace venció o ya se usó. Pide uno nuevo.");
        Usuario x = u.get();
        List<String> errores = PoliticaClave.validar(nueva, null, x.getUsername(), confirmar);
        if (!errores.isEmpty()) return errores;
        x.setPassword(encoder.encode(nueva));
        x.setDebeCambiarClave(false);
        x.setTokenReset(null);
        x.setTokenVence(null);
        x.setIntentosFallidos(0);
        x.setBloqueadoHasta(null);
        usuarios.save(x);
        actividad.registrar(x.getUsername(), "Contraseña restablecida", "");
        return List.of();
    }
}
