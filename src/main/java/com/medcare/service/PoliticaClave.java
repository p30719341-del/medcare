package com.medcare.service;

import java.util.ArrayList;
import java.util.List;

/** Reglas de contraseña (las usan el cambio de clave y la recuperación). */
public final class PoliticaClave {
    private PoliticaClave() {}

    /** @param actual clave vigente (null si no aplica, por ejemplo al recuperar) */
    public static List<String> validar(String nueva, String actual, String usuario, String confirmar) {
        List<String> e = new ArrayList<>();
        if (nueva == null) nueva = "";
        if (nueva.length() < 8) e.add("La nueva contraseña debe tener al menos 8 caracteres.");
        if (!nueva.matches(".*[A-Za-z].*") || !nueva.matches(".*\\d.*")) e.add("Debe incluir al menos una letra y un número.");
        if (nueva.equals(usuario)) e.add("No uses tu usuario (o DNI) como contraseña.");
        if (actual != null && nueva.equals(actual)) e.add("La nueva contraseña debe ser distinta de la actual.");
        if (confirmar != null && !nueva.equals(confirmar)) e.add("La confirmación no coincide.");
        return e;
    }
}
