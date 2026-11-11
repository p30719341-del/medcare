package com.medcare.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

import java.net.URI;
import java.util.Locale;

/** Cambia el idioma de la portada y del login (es / en) y vuelve a la página donde estabas. */
@Controller
public class IdiomaController {

    private final LocaleResolver resolver;

    public IdiomaController(LocaleResolver resolver) { this.resolver = resolver; }

    @GetMapping("/idioma")
    public String cambiar(@RequestParam(defaultValue = "es") String lang, HttpServletRequest req, HttpServletResponse res) {
        resolver.setLocale(req, res, "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.forLanguageTag("es-PE"));
        return "redirect:" + destino(req.getHeader("Referer"), req.getServerName());
    }

    /** Solo vuelve a una ruta de este mismo sitio (evita redirecciones a otras webs). */
    static String destino(String referer, String host) {
        try {
            if (referer == null || referer.isBlank()) return "/";
            URI u = URI.create(referer);
            if (u.getHost() != null && !u.getHost().equalsIgnoreCase(host)) return "/";
            String path = u.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")) return "/";
            if (path.startsWith("/idioma")) return "/";
            return path + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        } catch (RuntimeException e) {
            return "/";
        }
    }
}
