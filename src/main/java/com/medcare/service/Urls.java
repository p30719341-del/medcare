package com.medcare.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Dirección pública de la app (medcare.base-url o variable BASE_URL): la usan los códigos QR de las recetas. */
@Component("urls")
public class Urls {

    private final String base;

    public Urls(@Value("${medcare.base-url:http://localhost:8080}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public String getBase() { return base; }
    public String verificacion(String codigo) { return base + "/verificar/" + codigo; }
}
