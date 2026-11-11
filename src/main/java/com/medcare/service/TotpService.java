package com.medcare.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/** Verificación en dos pasos con códigos de 6 dígitos que cambian cada 30 s (TOTP, RFC 6238; Google Authenticator, Authy, etc.). */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom azar = new SecureRandom();

    /** Secreto nuevo de 160 bits en Base32 (32 caracteres). */
    public String nuevoSecreto() {
        byte[] b = new byte[20];
        azar.nextBytes(b);
        return base32(b);
    }

    /** Dirección que lee la app autenticadora (se muestra como QR). */
    public String uri(String usuario, String secreto) {
        return "otpauth://totp/MedCare:" + codificar(usuario) + "?secret=" + secreto + "&issuer=MedCare&algorithm=SHA1&digits=6&period=30";
    }

    /** true si el código corresponde al instante actual (se tolera ±30 s por diferencia de relojes). */
    public boolean verificar(String secreto, String codigo) { return verificar(secreto, codigo, System.currentTimeMillis()); }

    public boolean verificar(String secreto, String codigo, long ahoraMs) {
        if (secreto == null || codigo == null) return false;
        String c = codigo.replaceAll("\\s", "");
        if (!c.matches("\\d{6}")) return false;
        long paso = ahoraMs / 30_000L;
        boolean ok = false;
        for (int d = -1; d <= 1; d++) ok |= codigo(secreto, paso + d).equals(c);   // sin cortocircuito
        return ok;
    }

    /** Código TOTP de un paso de tiempo dado. */
    public String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(secreto), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] b) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte x : b) {
            buffer = (buffer << 8) | (x & 0xFF);
            bits += 8;
            while (bits >= 5) { sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] decodificar(String s) {
        String t = s.replace(" ", "").replace("=", "").toUpperCase();
        byte[] out = new byte[t.length() * 5 / 8];
        int buffer = 0, bits = 0, k = 0;
        for (char ch : t.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Secreto no válido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) { out[k++] = (byte) ((buffer >> (bits - 8)) & 0xFF); bits -= 8; }
        }
        return out;
    }

    private static String codificar(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
