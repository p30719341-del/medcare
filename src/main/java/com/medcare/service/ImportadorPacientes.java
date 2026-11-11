package com.medcare.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lee una lista de pacientes desde un Excel (.xlsx) o un CSV, sin librerías externas. Solo lee y valida: no guarda nada. */
public final class ImportadorPacientes {

    public static final int MAX_FILAS = 500;
    private static final int MAX_PARTE = 8_000_000;      // límite de cada parte descomprimida del .xlsx (evita "bombas zip")

    private ImportadorPacientes() {}

    /** Una fila ya interpretada. error != null: la fila no se puede importar. */
    public static final class Fila {
        public final int numero;
        public String nombre = "", dni = "", telefono = "", correo = "", error;
        public Fila(int numero) { this.numero = numero; }
        public int getNumero() { return numero; }
        public String getNombre() { return nombre; }
        public String getDni() { return dni; }
        public String getTelefono() { return telefono; }
        public String getCorreo() { return correo; }
        public String getError() { return error; }
    }

    // ------------------------------------------------------------------ lectura de archivos

    /** Devuelve las filas como texto. Reconoce .xlsx y .csv/.txt por el nombre del archivo. */
    public static List<List<String>> leer(String nombreArchivo, byte[] datos) throws IOException {
        String n = nombreArchivo == null ? "" : nombreArchivo.toLowerCase();
        if (n.endsWith(".xlsx")) return leerXlsx(datos);
        if (n.endsWith(".csv") || n.endsWith(".txt")) return leerCsv(new String(datos, StandardCharsets.UTF_8));
        throw new IOException("Formato no admitido. Sube un archivo .xlsx o .csv");
    }

    public static List<List<String>> leerCsv(String texto) {
        if (texto.startsWith("﻿")) texto = texto.substring(1);
        String primera = texto.lines().findFirst().orElse("");
        char sep = cuenta(primera, ';') >= cuenta(primera, ',') && cuenta(primera, ';') >= cuenta(primera, '\t') ? ';'
                : cuenta(primera, ',') >= cuenta(primera, '\t') ? ',' : '\t';
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder c = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') { c.append('"'); i++; } else comillas = false;
                } else c.append(ch);
            } else if (ch == '"') comillas = true;
            else if (ch == sep) { fila.add(c.toString().trim()); c.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                fila.add(c.toString().trim()); c.setLength(0);
                if (!vacia(fila)) filas.add(fila);
                fila = new ArrayList<>();
            } else c.append(ch);
            if (filas.size() > MAX_FILAS + 5) break;
        }
        fila.add(c.toString().trim());
        if (!vacia(fila)) filas.add(fila);
        return filas;
    }

    private static int cuenta(String s, char ch) { int n = 0; for (char x : s.toCharArray()) if (x == ch) n++; return n; }
    private static boolean vacia(List<String> f) { for (String s : f) if (!s.isEmpty()) return false; return true; }

    public static List<List<String>> leerXlsx(byte[] datos) throws IOException {
        Map<String, byte[]> partes = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(datos))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String nombre = e.getName();
                if (nombre.equals("xl/sharedStrings.xml") || nombre.equals("xl/workbook.xml") || nombre.equals("xl/_rels/workbook.xml.rels")
                        || nombre.startsWith("xl/worksheets/sheet")) {
                    partes.put(nombre, leerLimitado(zip));
                }
            }
        }
        String hoja = "xl/worksheets/sheet1.xml";
        if (!partes.containsKey(hoja)) {
            hoja = partes.keySet().stream().filter(k -> k.startsWith("xl/worksheets/sheet")).sorted().findFirst()
                    .orElseThrow(() -> new IOException("El archivo no parece un Excel válido (.xlsx)."));
        }
        List<String> compartidas = new ArrayList<>();
        if (partes.containsKey("xl/sharedStrings.xml")) {
            Document d = xml(partes.get("xl/sharedStrings.xml"));
            NodeList si = d.getElementsByTagName("si");
            for (int i = 0; i < si.getLength(); i++) compartidas.add(textoDe((Element) si.item(i)));
        }
        Document d = xml(partes.get(hoja));
        NodeList rows = d.getElementsByTagName("row");
        List<List<String>> salida = new ArrayList<>();
        for (int i = 0; i < rows.getLength() && salida.size() <= MAX_FILAS + 5; i++) {
            Element row = (Element) rows.item(i);
            List<String> fila = new ArrayList<>();
            NodeList celdas = row.getElementsByTagName("c");
            for (int j = 0; j < celdas.getLength(); j++) {
                Element c = (Element) celdas.item(j);
                int col = columna(c.getAttribute("r"));
                while (col >= 0 && fila.size() < col) fila.add("");
                if (col > 60) continue;
                String t = c.getAttribute("t"), valor = "";
                NodeList vs = c.getElementsByTagName("v");
                if ("s".equals(t) && vs.getLength() > 0) {
                    int idx = entero(vs.item(0).getTextContent());
                    valor = idx >= 0 && idx < compartidas.size() ? compartidas.get(idx) : "";
                } else if ("inlineStr".equals(t)) {
                    valor = textoDe(c);
                } else if (vs.getLength() > 0) {
                    valor = numero(vs.item(0).getTextContent());
                }
                if (fila.size() == col) fila.add(valor.trim()); else if (col >= 0 && col < fila.size()) fila.set(col, valor.trim());
            }
            if (!vacia(fila)) salida.add(fila);
        }
        return salida;
    }

    private static byte[] leerLimitado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_PARTE) throw new IOException("El archivo es demasiado grande.");
        }
        return out.toByteArray();
    }

    private static Document xml(byte[] b) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // sin XXE
            f.setExpandEntityReferences(false);
            DocumentBuilder db = f.newDocumentBuilder();
            db.setErrorHandler(null);
            return db.parse(new ByteArrayInputStream(b));
        } catch (Exception e) {
            throw new IOException("No se pudo leer el Excel. Guárdalo de nuevo como .xlsx o usa un .csv.");
        }
    }

    private static String textoDe(Element e) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = e.getElementsByTagName("t");
        for (int i = 0; i < ts.getLength(); i++) {
            Node padre = ts.item(i).getParentNode();
            if (padre != null && "rPh".equals(padre.getNodeName())) continue;      // lectura fonética: se ignora
            sb.append(ts.item(i).getTextContent());
        }
        return sb.toString();
    }

    /** "C5" -> 2 ; "AA1" -> 26. */
    static int columna(String ref) {
        if (ref == null || ref.isEmpty()) return -1;
        int n = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) { n = n * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1); i++; }
        return n - 1;
    }

    private static int entero(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; } }

    /** 7.4581236E7 -> 74581236 (los números de Excel pueden venir en notación científica). */
    static String numero(String s) {
        String t = s.trim();
        try { return new BigDecimal(t).stripTrailingZeros().toPlainString(); } catch (NumberFormatException e) { return t; }
    }

    // ------------------------------------------------------------------ interpretación y validación

    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    public static List<Fila> interpretar(List<List<String>> tabla) {
        int cN = 0, cD = 1, cT = 2, cC = 3, inicio = 0;
        if (!tabla.isEmpty()) {
            List<String> cab = tabla.get(0);
            boolean tieneCabecera = false;
            int n = -1, d = -1, t = -1, c = -1;
            for (int i = 0; i < cab.size(); i++) {
                String h = norm(cab.get(i));
                if (h.equals("dni") || h.equals("documento")) { d = i; tieneCabecera = true; }
                else if (h.startsWith("nombre") || h.equals("paciente") || h.equals("apellidos y nombres")) n = i;
                else if (h.startsWith("tel") || h.equals("celular") || h.equals("phone") || h.equals("movil")) t = i;
                else if (h.startsWith("correo") || h.startsWith("email") || h.startsWith("e-mail")) c = i;
            }
            if (tieneCabecera) {
                cN = n >= 0 ? n : 0; cD = d; cT = t >= 0 ? t : 2; cC = c; inicio = 1;
            }
        }
        List<Fila> filas = new ArrayList<>();
        for (int i = inicio; i < tabla.size(); i++) {
            List<String> r = tabla.get(i);
            Fila f = new Fila(i + 1);
            f.nombre = dato(r, cN).replaceAll("\\s+", " ");
            String dni = dato(r, cD).replaceAll("[\\s.-]", "");
            if (dni.matches("\\d{5,7}")) dni = "0".repeat(8 - dni.length()) + dni;       // Excel quita los ceros de la izquierda
            f.dni = dni;
            f.telefono = dato(r, cT);
            f.correo = cC >= 0 ? dato(r, cC) : "";
            f.error = validar(f);
            filas.add(f);
        }
        return filas;
    }

    private static String dato(List<String> r, int i) { return i >= 0 && i < r.size() ? r.get(i) : ""; }

    static String validar(Fila f) {
        if (f.nombre.isEmpty()) return "Falta el nombre.";
        if (f.nombre.length() > 100) return "El nombre es demasiado largo.";
        if (!f.dni.matches("\\d{8}")) return "El DNI debe tener 8 dígitos.";
        int digitos = f.telefono.replaceAll("\\D", "").length();
        if (!f.telefono.matches("[0-9+()\\s-]{6,20}") || digitos < 6 || digitos > 15) return "Teléfono no válido.";
        if (!f.correo.isEmpty() && (f.correo.length() > 100 || !f.correo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) return "Correo no válido.";
        return null;
    }
}
