package com.medcare.service;

/** Una regla de la clínica no se cumple (el mensaje está pensado para mostrarse al usuario). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) { super(mensaje); }
}
