package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.util.QrCode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF de la receta/diagnóstico de una cita atendida, sin librerías externas
 * (PDF 1.4 con las fuentes estándar Helvetica y Helvetica-Bold).
 */
public final class RecetaPdf {

    private RecetaPdf() {}

    private static final class Linea {
        final String texto; final boolean negrita; final int tam;
        Linea(String texto, boolean negrita, int tam) { this.texto = texto; this.negrita = negrita; this.tam = tam; }
    }

    private static final int ANCHO_MAX = 88;          // caracteres por línea (aprox.) a 11 pt
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    public static byte[] generar(Cita c) { return generar(c, null); }

    /** Receta con un código QR (si se da la dirección de verificación) que lleva a /verificar/{codigo}. */
    public static byte[] generar(Cita c, String urlVerificacion) {
        List<Linea> l = new ArrayList<>();
        l.add(new Linea("MedCare  -  Receta médica", true, 20));
        l.add(new Linea("", false, 11));
        l.add(new Linea("Paciente: " + c.getPaciente().getName(), true, 12));
        l.add(new Linea("DNI: " + c.getPaciente().getDni(), false, 11));
        l.add(new Linea("Fecha de atención: " + c.getFecha().format(FECHA) + ", " + c.getHora() + " h", false, 11));
        if (c.getFamiliar() != null) l.add(new Linea("Atendido: " + c.getFamiliar(), false, 11));
        l.add(new Linea("Doctor: " + c.getDoctor() + "  -  " + c.getEspecialidad(), false, 11));
        if (c.getPaciente().getAlergias() != null) l.add(new Linea("Alergias: " + c.getPaciente().getAlergias(), true, 11));
        l.add(new Linea("", false, 11));
        l.add(new Linea("Diagnóstico", true, 13));
        envolver(vacioSi(c.getDiagnostico(), "Sin diagnóstico registrado."), l, false);
        l.add(new Linea("", false, 11));
        l.add(new Linea("Receta e indicaciones", true, 13));
        envolver(vacioSi(c.getReceta(), "Sin receta registrada."), l, false);
        l.add(new Linea("", false, 11));
        l.add(new Linea("Documento generado por una maqueta académica con datos ficticios.", false, 9));
        l.add(new Linea("No tiene validez médica ni legal.", false, 9));
        if (urlVerificacion == null || c.getCodigoReceta() == null) return construir(l, "");
        String qr = dibujarQr(urlVerificacion, 455, 712, 92)
                + "BT /F1 7 Tf 455 703 Td (Escanea para verificar) Tj ET\nBT /F1 7 Tf 455 695 Td (c\u00f3digo " + c.getCodigoReceta() + ") Tj ET\n";
        return construir(l, qr);
    }

    /** Boleta de demostración de una cita pagada (no tiene validez tributaria). */
    public static byte[] generarBoleta(Cita c) {
        List<Linea> l = new ArrayList<>();
        l.add(new Linea("MedCare  -  Boleta de venta electrónica", true, 18));
        l.add(new Linea("N° " + c.getNumeroBoleta(), true, 13));
        l.add(new Linea("", false, 11));
        l.add(new Linea("Fecha de emisión: " + java.time.LocalDate.now().format(FECHA), false, 11));
        l.add(new Linea("Cliente: " + c.getPaciente().getName(), false, 11));
        l.add(new Linea("DNI: " + c.getPaciente().getDni(), false, 11));
        l.add(new Linea("", false, 11));
        l.add(new Linea("Detalle", true, 13));
        l.add(new Linea("Consulta de " + c.getEspecialidad() + " con " + c.getDoctor() + " (" + c.getModalidad().toLowerCase(Locale.ROOT) + ")", false, 11));
        l.add(new Linea("Fecha de la cita: " + c.getFecha().format(FECHA) + ", " + c.getHora() + " h", false, 11));
        if (c.getFamiliar() != null) l.add(new Linea("Paciente atendido: " + c.getFamiliar(), false, 11));
        l.add(new Linea("", false, 11));
        l.add(new Linea(String.format(Locale.ROOT, "Importe total: S/ %d.00", c.getPrecio()), true, 14));
        l.add(new Linea("Estado: PAGADO", true, 11));
        l.add(new Linea("", false, 11));
        l.add(new Linea("Comprobante de demostración generado por una maqueta académica.", false, 9));
        l.add(new Linea("No tiene validez tributaria.", false, 9));
        return construir(l, "");
    }

    /** Instrucciones PDF que dibujan el QR como cuadritos negros sobre fondo blanco. */
    private static String dibujarQr(String texto, int x0, int y0, int lado) {
        QrCode q = QrCode.codificar(texto);
        int n = q.getTam();
        double borde = 2, m = lado / (n + 2 * borde);
        StringBuilder sb = new StringBuilder("q 1 g " + x0 + " " + y0 + " " + lado + " " + lado + " re f 0 g\n");
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (q.get(x, y)) {
                    sb.append(String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f re\n",
                            x0 + (x + borde) * m, y0 + lado - (y + borde + 1) * m, m, m));
                }
        return sb.append("f Q\n").toString();
    }

    /** PDF sencillo de texto (para el resumen de reportes). Las líneas que empiezan con "# " salen como subtítulo. */
    public static byte[] generarTexto(String titulo, List<String> filas) {
        List<Linea> l = new ArrayList<>();
        l.add(new Linea(titulo, true, 18));
        l.add(new Linea("", false, 11));
        for (String f : filas) {
            if (f.startsWith("# ")) { l.add(new Linea("", false, 8)); l.add(new Linea(f.substring(2), true, 13)); }
            else envolver(f, l, false);
        }
        return construir(l, "");
    }

    private static String vacioSi(String s, String porDefecto) { return s == null || s.isBlank() ? porDefecto : s; }

    private static void envolver(String texto, List<Linea> destino, boolean negrita) {
        for (String parrafo : texto.replace("\r", "").split("\n", -1)) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.split(" ")) {
                while (palabra.length() > ANCHO_MAX) {               // palabra larguísima
                    if (actual.length() > 0) { destino.add(new Linea(actual.toString(), negrita, 11)); actual.setLength(0); }
                    destino.add(new Linea(palabra.substring(0, ANCHO_MAX), negrita, 11));
                    palabra = palabra.substring(ANCHO_MAX);
                }
                if (actual.length() + palabra.length() + (actual.length() > 0 ? 1 : 0) > ANCHO_MAX) {
                    destino.add(new Linea(actual.toString(), negrita, 11));
                    actual.setLength(0);
                }
                if (actual.length() > 0) actual.append(' ');
                actual.append(palabra);
            }
            destino.add(new Linea(actual.toString(), negrita, 11));
        }
    }

    private static byte[] construir(List<Linea> lineas, String extraPrimeraPagina) {
        // 1) repartir las líneas en páginas
        List<StringBuilder> paginas = new ArrayList<>();
        StringBuilder pag = new StringBuilder();
        int y = 800;
        for (Linea ln : lineas) {
            int salto = ln.tam + 6;
            if (y - salto < 60) { paginas.add(pag); pag = new StringBuilder(); y = 800; }
            if (!ln.texto.isEmpty()) {
                pag.append("BT /").append(ln.negrita ? "F2" : "F1").append(' ').append(ln.tam)
                   .append(" Tf 50 ").append(y).append(" Td (").append(escapar(ln.texto)).append(") Tj ET\n");
            }
            y -= salto;
        }
        paginas.add(pag);
        paginas.get(0).append(extraPrimeraPagina);

        // 2) objetos: 1 catálogo, 2 páginas, 3-4 fuentes, luego (página, contenido) por cada página
        List<byte[]> objetos = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < paginas.size(); i++) kids.append(5 + 2 * i).append(" 0 R ");
        objetos.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        objetos.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + paginas.size() + " >>"));
        objetos.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objetos.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < paginas.size(); i++) {
            objetos.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + (6 + 2 * i) + " 0 R >>"));
            byte[] contenido = bytes(paginas.get(i).toString());
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            s.writeBytes(bytes("<< /Length " + contenido.length + " >>\nstream\n"));
            s.writeBytes(contenido);
            s.writeBytes(bytes("endstream"));
            objetos.add(s.toByteArray());
        }

        // 3) escribir el archivo con la tabla de referencias
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("%PDF-1.4\n"));
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < objetos.size(); i++) {
            pos.add(out.size());
            out.writeBytes(bytes((i + 1) + " 0 obj\n"));
            out.writeBytes(objetos.get(i));
            out.writeBytes(bytes("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : pos) x.append(String.format("%010d 00000 n \n", p));
        x.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(bytes(x.toString()));
        return out.toByteArray();
    }

    /** Escapa los caracteres especiales de una cadena PDF y reemplaza lo que Latin-1 no puede representar. */
    private static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '\\' || ch == '(' || ch == ')') b.append('\\').append(ch);
            else if (ch < 32 || ch == 127) b.append(' ');
            else if (ch > 255) b.append('?');
            else b.append(ch);
        }
        return b.toString();
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
}
