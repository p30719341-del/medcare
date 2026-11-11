package com.medcare.config;

import com.medcare.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Mientras el paciente tenga la clave inicial (su DNI), todo el portal lo lleva a cambiarla. */
@Component
public class ClaveObligatoriaInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarios;

    public ClaveObligatoriaInterceptor(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && usuarios.findByUsername(auth.getName()).map(u -> u.isDebeCambiarClave()).orElse(false)) {
            response.sendRedirect(request.getContextPath() + "/cuenta/clave");
            return false;
        }
        return true;
    }
}
