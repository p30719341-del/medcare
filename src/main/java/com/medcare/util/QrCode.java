package com.medcare.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador de códigos QR sin librerías (modo bytes, corrección de errores nivel M, versiones 1 a 10:
 * hasta 213 caracteres). Se usa para la verificación de recetas y para activar la verificación en dos pasos.
 */
public final class QrCode {

    private static final int[] ECC_POR_BLOQUE = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};   // nivel M, versión 1..10
    private static final int[] BLOQUES = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    private static final int VERSION_MAX = 10;

    private final int version, tam;
    private final boolean[][] modulos;
    private final boolean[][] funcion;

    private QrCode(int version, byte[] datos) {
        this.version = version;
        this.tam = version * 4 + 17;
        this.modulos = new boolean[tam][tam];
        this.funcion = new boolean[tam][tam];
        dibujarFuncion();
        colocarDatos(agregarEcc(datos));
        int mejor = 0, mejorPena = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            aplicarMascara(m);
            dibujarFormato(m);
            int p = penalizacion();
            if (p < mejorPena) { mejor = m; mejorPena = p; }
            aplicarMascara(m);     // XOR otra vez: deshace la máscara
        }
        aplicarMascara(mejor);
        dibujarFormato(mejor);
    }

    /** Codifica el texto (UTF-8). Lanza IllegalArgumentException si es demasiado largo. */
    public static QrCode codificar(String texto) {
        byte[] b = texto.getBytes(StandardCharsets.UTF_8);
        for (int v = 1; v <= VERSION_MAX; v++) {
            int capacidad = datosTotales(v);
            int bitsConteo = v < 10 ? 8 : 16;
            int bits = 4 + bitsConteo + b.length * 8;
            if (bits <= capacidad * 8) return new QrCode(v, armarDatos(b, v, capacidad));
        }
        throw new IllegalArgumentException("Texto demasiado largo para el QR");
    }

    public int getTam() { return tam; }
    public boolean get(int x, int y) { return x >= 0 && y >= 0 && x < tam && y < tam && modulos[y][x]; }

    /** Imagen vectorial lista para incrustar en HTML. */
    public String toSvg(int borde) {
        StringBuilder sb = new StringBuilder();
        int total = tam + borde * 2;
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(total).append(' ').append(total)
          .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"");
        for (int y = 0; y < tam; y++)
            for (int x = 0; x < tam; x++)
                if (modulos[y][x]) sb.append('M').append(x + borde).append(',').append(y + borde).append("h1v1h-1z");
        return sb.append("\"/></svg>").toString();
    }

    // ---------------- Datos y corrección de errores ----------------

    private static int modulosDeDatos(int v) {
        int r = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int n = v / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (v >= 7) r -= 36;
        }
        return r;
    }

    private static int datosTotales(int v) { return modulosDeDatos(v) / 8 - ECC_POR_BLOQUE[v] * BLOQUES[v]; }

    private static byte[] armarDatos(byte[] b, int v, int capacidad) {
        List<Boolean> bits = new ArrayList<>();
        agregar(bits, 0b0100, 4);
        agregar(bits, b.length, v < 10 ? 8 : 16);
        for (byte x : b) agregar(bits, x & 0xFF, 8);
        int max = capacidad * 8;
        agregar(bits, 0, Math.min(4, max - bits.size()));
        while (bits.size() % 8 != 0) bits.add(false);
        for (int pad = 0xEC; bits.size() < max; pad ^= 0xEC ^ 0x11) agregar(bits, pad, 8);
        byte[] out = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) if (bits.get(i)) out[i >> 3] |= 1 << (7 - (i & 7));
        return out;
    }

    private static void agregar(List<Boolean> bits, int valor, int n) {
        for (int i = n - 1; i >= 0; i--) bits.add(((valor >>> i) & 1) != 0);
    }

    private byte[] agregarEcc(byte[] datos) {
        int bloques = BLOQUES[version], ecc = ECC_POR_BLOQUE[version];
        int total = modulosDeDatos(version) / 8;
        int cortos = bloques - total % bloques, largoCorto = total / bloques - ecc;
        byte[][] data = new byte[bloques][], eccs = new byte[bloques][];
        int[] gen = divisor(ecc);
        for (int i = 0, k = 0; i < bloques; i++) {
            int largo = largoCorto + (i < cortos ? 0 : 1);
            data[i] = java.util.Arrays.copyOfRange(datos, k, k + largo);
            k += largo;
            eccs[i] = resto(data[i], gen);
        }
        byte[] out = new byte[total];
        int p = 0;
        for (int i = 0; i <= largoCorto; i++)
            for (int j = 0; j < bloques; j++)
                if (i < data[j].length) out[p++] = data[j][i];
        for (int i = 0; i < ecc; i++)
            for (int j = 0; j < bloques; j++) out[p++] = eccs[j][i];
        return out;
    }

    private static int[] divisor(int grado) {
        int[] r = new int[grado];
        r[grado - 1] = 1;
        int raiz = 1;
        for (int i = 0; i < grado; i++) {
            for (int j = 0; j < grado; j++) {
                r[j] = mult(r[j], raiz);
                if (j + 1 < grado) r[j] ^= r[j + 1];
            }
            raiz = mult(raiz, 2);
        }
        return r;
    }

    private static byte[] resto(byte[] datos, int[] div) {
        int[] r = new int[div.length];
        for (byte b : datos) {
            int f = (b & 0xFF) ^ r[0];
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= mult(div[i], f);
        }
        byte[] out = new byte[r.length];
        for (int i = 0; i < r.length; i++) out[i] = (byte) r[i];
        return out;
    }

    private static int mult(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // ---------------- Dibujo ----------------

    private void set(int x, int y, boolean negro) { modulos[y][x] = negro; funcion[y][x] = true; }

    private void dibujarFuncion() {
        for (int i = 0; i < tam; i++) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0); }
        buscador(3, 3); buscador(tam - 4, 3); buscador(3, tam - 4);
        int[] pos = posicionesAlineacion();
        for (int a = 0; a < pos.length; a++)
            for (int b = 0; b < pos.length; b++)
                if (!((a == 0 && b == 0) || (a == 0 && b == pos.length - 1) || (a == pos.length - 1 && b == 0)))
                    alineacion(pos[a], pos[b]);
        dibujarFormato(0);
        dibujarVersion();
    }

    private void buscador(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy)), x = cx + dx, y = cy + dy;
                if (x >= 0 && x < tam && y >= 0 && y < tam) set(x, y, d != 2 && d != 4);
            }
    }

    private void alineacion(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] posicionesAlineacion() {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int paso = (version == 32) ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, pos = tam - 7; i >= 1; i--, pos -= paso) r[i] = pos;
        return r;
    }

    private void dibujarFormato(int mascara) {
        int datos = (0b00 << 3) | mascara;           // nivel M = 00
        int resto = datos;
        for (int i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
        int bits = ((datos << 10) | resto) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6)); set(8, 8, bit(bits, 7)); set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(tam - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, tam - 15 + i, bit(bits, i));
        set(8, tam - 8, true);
    }

    private void dibujarVersion() {
        if (version < 7) return;
        int resto = version;
        for (int i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >>> 11) * 0x1F25);
        int bits = (version << 12) | resto;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = tam - 11 + i % 3, c = i / 3;
            set(a, c, b); set(c, a, b);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void colocarDatos(byte[] datos) {
        int i = 0;
        for (int derecha = tam - 1; derecha >= 1; derecha -= 2) {
            if (derecha == 6) derecha = 5;
            for (int v = 0; v < tam; v++)
                for (int j = 0; j < 2; j++) {
                    int x = derecha - j;
                    boolean arriba = ((derecha + 1) & 2) == 0;
                    int y = arriba ? tam - 1 - v : v;
                    if (!funcion[y][x] && i < datos.length * 8) {
                        modulos[y][x] = bit(datos[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void aplicarMascara(int m) {
        for (int y = 0; y < tam; y++)
            for (int x = 0; x < tam; x++) {
                boolean invertir;
                switch (m) {
                    case 0: invertir = (x + y) % 2 == 0; break;
                    case 1: invertir = y % 2 == 0; break;
                    case 2: invertir = x % 3 == 0; break;
                    case 3: invertir = (x + y) % 3 == 0; break;
                    case 4: invertir = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invertir = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invertir = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invertir = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invertir && !funcion[y][x]) modulos[y][x] ^= true;
            }
    }

    /** Puntaje simplificado de la norma (bloques del mismo color, cuadros 2x2 y proporción de negros). */
    private int penalizacion() {
        int r = 0;
        for (int y = 0; y < tam; y++) {
            int corridaF = 1, corridaC = 1;
            for (int x = 1; x < tam; x++) {
                corridaF = modulos[y][x] == modulos[y][x - 1] ? corridaF + 1 : 1;
                corridaC = modulos[x][y] == modulos[x - 1][y] ? corridaC + 1 : 1;
                if (corridaF == 5) r += 3; else if (corridaF > 5) r++;
                if (corridaC == 5) r += 3; else if (corridaC > 5) r++;
            }
        }
        int negros = 0;
        for (int y = 0; y < tam; y++)
            for (int x = 0; x < tam; x++) {
                if (modulos[y][x]) negros++;
                if (x + 1 < tam && y + 1 < tam && modulos[y][x] == modulos[y][x + 1]
                        && modulos[y][x] == modulos[y + 1][x] && modulos[y][x] == modulos[y + 1][x + 1]) r += 3;
            }
        int total = tam * tam;
        r += Math.abs(negros * 20 - total * 10) / total * 10;
        return r;
    }
}
