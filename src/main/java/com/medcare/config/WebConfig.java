package com.medcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ClaveObligatoriaInterceptor claveObligatoria;
    private final DosPasosInterceptor dosPasos;

    public WebConfig(ClaveObligatoriaInterceptor claveObligatoria, DosPasosInterceptor dosPasos) {
        this.claveObligatoria = claveObligatoria; this.dosPasos = dosPasos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dosPasos).addPathPatterns("/**").excludePathPatterns(
                "/login", "/login/2fa", "/logout", "/styles.css", "/script.js", "/home.css", "/home.js", "/img/**", "/error", "/verificar/**",
                "/manifest.webmanifest", "/sw.js", "/offline.html", "/idioma");
        registry.addInterceptor(claveObligatoria).addPathPatterns("/paciente/**", "/doctor/**", "/recepcion/**");
    }
}
