package com.medcare.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado AES-256-GCM de los datos médicos sensibles (diagnóstico, receta, alergias...).
 * La clave sale de la variable de entorno MEDCARE_CLAVE_CIFRADO (o de -Dmedcare.cifrado.clave).
 * Si no la defines se usa una clave de demostración: sirve para probar, NO para datos reales.
 * Los valores guardados llevan el prefijo "enc1:"; un valor sin prefijo (datos de versiones anteriores) se lee tal cual.
 */
public final class Cifrado {

    public static final String PREFIJO = "enc1:";
    private static final String CLAVE_DEMO = "medcare-clave-de-demostracion-cambiala";
    private static final SecureRandom AZAR = new SecureRandom();
    private static volatile SecretKeySpec clave;

    private Cifrado() {}

    private static SecretKeySpec clave() {
        SecretKeySpec k = clave;
        if (k == null) {
            String s = System.getProperty("medcare.cifrado.clave");
            if (s == null || s.isBlank()) s = System.getenv("MEDCARE_CLAVE_CIFRADO");
            if (s == null || s.isBlank()) s = CLAVE_DEMO;
            k = clave = derivar(s);
        }
        return k;
    }

    /** true si se está usando la clave de demostración (el panel del sistema lo advierte). */
    public static boolean usaClaveDemo() {
        String s = System.getProperty("medcare.cifrado.clave");
        if (s == null || s.isBlank()) s = System.getenv("MEDCARE_CLAVE_CIFRADO");
        return s == null || s.isBlank();
    }

    static SecretKeySpec derivar(String secreto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(h, "AES");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Para pruebas: fija otra clave. */
    public static void usarClave(String secreto) { clave = derivar(secreto); }

    public static String cifrar(String texto) {
        if (texto == null || texto.isEmpty() || texto.startsWith(PREFIJO)) return texto;
        try {
            byte[] iv = new byte[12];
            AZAR.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, todo, 0, iv.length);
            System.arraycopy(enc, 0, todo, iv.length, enc.length);
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (Exception e) { throw new IllegalStateException("No se pudo cifrar", e); }
    }

    public static String descifrar(String guardado) {
        if (guardado == null || !guardado.startsWith(PREFIJO)) return guardado;   // dato antiguo sin cifrar
        try {
            byte[] todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(128, todo, 0, 12));
            return new String(c.doFinal(todo, 12, todo.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[dato cifrado: la clave MEDCARE_CLAVE_CIFRADO no coincide]";
        }
    }
}
