package com.medcare.config;

import com.medcare.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/login", "/reservar", "/reservar/horas", "/styles.css", "/script.js",
                                 "/home.css", "/home.js", "/app.js", "/api/solicitudes", "/api/solicitudes/**", "/img/**", "/error",
                                 "/registro", "/recuperar", "/recuperar/**", "/espera", "/verificar/**",
                                 "/manifest.webmanifest", "/sw.js", "/offline.html", "/idioma", "/h2-console/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/recepcion/**").hasRole("RECEPCION")
                .requestMatchers("/paciente/**").hasRole("PACIENTE")
                .anyRequest().authenticated())      // /cuenta/** : cualquier usuario con sesión
            .formLogin(f -> f
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll());
        http.csrf(c -> c.ignoringRequestMatchers("/h2-console/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    /** Después de iniciar sesión: admin -> panel, doctor -> su agenda, paciente -> su portal (o a cambiar la clave inicial). */
    @Bean
    public AuthenticationSuccessHandler successHandler(UsuarioRepository usuarios, com.medcare.service.ActividadService actividad) {
        return (request, response, auth) -> {
            boolean admin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean cambiar = usuarios.findByUsername(auth.getName()).map(u -> u.isDebeCambiarClave()).orElse(false);
            boolean dosPasos = usuarios.findByUsername(auth.getName())
                    .map(u -> u.isTotpActivo() && !"PACIENTE".equals(u.getRol())).orElse(false);
            if (dosPasos) {      // falta el código de la app autenticadora
                request.getSession().setAttribute(DosPasosInterceptor.PENDIENTE, Boolean.TRUE);
                response.sendRedirect(request.getContextPath() + "/login/2fa");
                return;
            }
            boolean doctor = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
            boolean recepcion = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_RECEPCION"));
            actividad.registrar(auth.getName(), "Inicio de sesión", admin ? "Administrador" : doctor ? "Doctor" : recepcion ? "Recepción" : "Paciente");
            String destino = cambiar ? "/cuenta/clave" : (admin ? "/admin/index" : doctor ? "/doctor/agenda" : recepcion ? "/recepcion/citas" : "/paciente/mi-cita");
            response.sendRedirect(request.getContextPath() + destino);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
