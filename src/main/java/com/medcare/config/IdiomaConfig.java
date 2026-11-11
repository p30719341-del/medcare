package com.medcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

/** El idioma (español por defecto, o inglés) se recuerda en una cookie del navegador. */
@Configuration
public class IdiomaConfig {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver r = new CookieLocaleResolver("medcare-lang");
        r.setDefaultLocale(Locale.forLanguageTag("es-PE"));
        r.setCookieMaxAge(Duration.ofDays(365));
        r.setCookieSameSite("Lax");
        return r;
    }
}
