package com.medcare.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Mientras falte el código de la verificación en dos pasos, solo se puede ver la pantalla de ese código. */
@Component
public class DosPasosInterceptor implements HandlerInterceptor {

    public static final String PENDIENTE = "MEDCARE_2FA_PENDIENTE";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession s = request.getSession(false);
        if (s != null && s.getAttribute(PENDIENTE) != null) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
                response.sendRedirect(request.getContextPath() + "/login/2fa");
                return false;
            }
        }
        return true;
    }
}
