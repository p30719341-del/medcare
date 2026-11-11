package com.medcare.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Catálogo breve de diagnósticos frecuentes con su código CIE-10 (la lista completa tiene miles; el doctor puede escribir otro código válido). */
public final class Cie10 {

    public static final class Item {
        private final String codigo, nombre;
        Item(String codigo, String nombre) { this.codigo = codigo; this.nombre = nombre; }
        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
    }

    private static final Pattern FORMATO = Pattern.compile("^[A-Z]\\d{2}(\\.\\d{1,2})?$");
    private static final String[][] DATOS = {
        {"J00", "Rinofaringitis aguda (resfriado común)"}, {"J02.9", "Faringitis aguda"}, {"J03.9", "Amigdalitis aguda"},
        {"J06.9", "Infección aguda de vías respiratorias superiores"}, {"J20.9", "Bronquitis aguda"}, {"J45.9", "Asma"},
        {"J18.9", "Neumonía"}, {"J30.4", "Rinitis alérgica"}, {"A09", "Diarrea y gastroenteritis infecciosa"},
        {"K29.7", "Gastritis"}, {"K21.9", "Reflujo gastroesofágico"}, {"K59.0", "Estreñimiento"},
        {"I10", "Hipertensión arterial"}, {"I25.9", "Cardiopatía isquémica crónica"}, {"I48", "Fibrilación auricular"},
        {"E11.9", "Diabetes mellitus tipo 2"}, {"E78.5", "Dislipidemia"}, {"E66.9", "Obesidad"}, {"E03.9", "Hipotiroidismo"},
        {"M54.5", "Lumbalgia"}, {"M25.5", "Dolor articular"}, {"M79.1", "Mialgia"},
        {"G43.9", "Migraña"}, {"R51", "Cefalea"}, {"R50.9", "Fiebre"}, {"R05", "Tos"}, {"R10.4", "Dolor abdominal"},
        {"N39.0", "Infección de vías urinarias"}, {"N76.0", "Vaginitis aguda"}, {"N92.6", "Menstruación irregular"},
        {"Z34.9", "Control de embarazo normal"}, {"Z00.0", "Examen médico general (control de rutina)"},
        {"Z00.1", "Control de salud del niño"}, {"Z23", "Vacunación"}, {"Z01.2", "Examen dental"},
        {"K02.9", "Caries dental"}, {"K05.1", "Gingivitis crónica"}, {"K08.1", "Pérdida de dientes"},
        {"L20.9", "Dermatitis atópica"}, {"L70.0", "Acné vulgar"}, {"L30.9", "Dermatitis"}, {"B35.9", "Dermatofitosis (hongos)"},
        {"H10.9", "Conjuntivitis"}, {"H66.9", "Otitis media"}, {"F41.9", "Ansiedad"}, {"F32.9", "Episodio depresivo"},
    };

    private static final List<Item> ITEMS = new ArrayList<>();
    static { for (String[] d : DATOS) ITEMS.add(new Item(d[0], d[1])); }

    private Cie10() {}

    public static List<Item> todos() { return ITEMS; }

    /** Nombre del código si está en el catálogo; si no, null. */
    public static String nombre(String codigo) {
        if (codigo == null) return null;
        for (Item i : ITEMS) if (i.codigo.equalsIgnoreCase(codigo.trim())) return i.nombre;
        return null;
    }

    /** true si el texto tiene forma de código CIE-10 (letra, dos dígitos y opcionalmente punto y 1-2 dígitos). */
    public static boolean formatoValido(String codigo) {
        return codigo != null && FORMATO.matcher(codigo.trim().toUpperCase()).matches();
    }
}
