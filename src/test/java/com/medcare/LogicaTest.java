package com.medcare;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.service.Cie10;
import com.medcare.service.Ics;
import com.medcare.service.ImportadorPacientes;
import com.medcare.service.PoliticaClave;
import com.medcare.service.TotpService;
import com.medcare.util.Cifrado;
import com.medcare.util.QrCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de la lógica pura (no arrancan Spring): horarios, claves, cifrado, 2 pasos, QR, CIE-10 y calendario. */
class LogicaTest {

    // ---------- Horarios del doctor ----------

    private Doctor doctor(int inicio, int fin, int duracion, int pausaIni, int pausaFin) {
        Doctor d = new Doctor("Dr. Prueba", "Medicina general", "x", null);
        d.setDias("1,2,3,4,5");
        d.setInicio(LocalTime.of(inicio, 0));
        d.setFin(LocalTime.of(fin, 0));
        d.setDuracion(duracion);
        d.setPausaInicio(LocalTime.of(pausaIni, 0));
        d.setPausaFin(LocalTime.of(pausaFin, 0));
        return d;
    }

    @Test
    void horariosEstandarSaltanElAlmuerzo() {
        List<LocalTime> h = doctor(8, 18, 30, 13, 14).horarios();
        assertEquals(18, h.size());
        assertEquals(LocalTime.of(8, 0), h.get(0));
        assertEquals(LocalTime.of(17, 30), h.get(h.size() - 1));
        assertFalse(h.contains(LocalTime.of(13, 0)));
        assertFalse(h.contains(LocalTime.of(13, 30)));
        assertTrue(h.contains(LocalTime.of(14, 0)));
    }

    @Test
    void horariosDeCitasDe20Minutos() {
        assertEquals(21, doctor(8, 16, 20, 12, 13).horarios().size());
    }

    @Test
    void doctorSinDuracionNoTieneHorarios() {
        assertTrue(doctor(8, 18, 0, 13, 14).horarios().isEmpty());
    }

    @Test
    void diasDeAtencion() {
        Doctor d = doctor(8, 18, 30, 13, 14);
        assertTrue(d.atiende(java.time.DayOfWeek.MONDAY));
        assertFalse(d.atiende(java.time.DayOfWeek.SUNDAY));
    }

    // ---------- Contraseñas ----------

    @Test
    void claveValidaNoTieneErrores() {
        assertTrue(PoliticaClave.validar("Clinica2026", "74581236", "74581236", "Clinica2026").isEmpty());
    }

    @Test
    void claveCortaSinNumeroOIgualAlUsuarioSeRechaza() {
        assertFalse(PoliticaClave.validar("abc1", null, "u", null).isEmpty());
        assertFalse(PoliticaClave.validar("sololetras", null, "u", null).isEmpty());
        assertFalse(PoliticaClave.validar("74581236", null, "74581236", null).isEmpty());
        assertFalse(PoliticaClave.validar("Clinica2026", "Clinica2026", "u", null).isEmpty());
        assertFalse(PoliticaClave.validar("Clinica2026", null, "u", "otra").isEmpty());
    }

    // ---------- Cifrado de datos sensibles ----------

    @Test
    void cifradoIdaYVuelta() {
        Cifrado.usarClave("clave-de-prueba");
        String c = Cifrado.cifrar("Alergia a la penicilina");
        assertTrue(c.startsWith(Cifrado.PREFIJO));
        assertFalse(c.contains("penicilina"));
        assertEquals("Alergia a la penicilina", Cifrado.descifrar(c));
    }

    @Test
    void cifradoUsaUnIvDistintoCadaVezYNoCifraDosVeces() {
        Cifrado.usarClave("clave-de-prueba");
        String a = Cifrado.cifrar("dato"), b = Cifrado.cifrar("dato");
        assertNotEquals(a, b);
        assertEquals(a, Cifrado.cifrar(a));
    }

    @Test
    void datosAntiguosSinCifrarSeLeenIgual() {
        assertEquals("texto plano", Cifrado.descifrar("texto plano"));
        assertNull(Cifrado.descifrar(null));
        assertNull(Cifrado.cifrar(null));
    }

    @Test
    void conOtraClaveNoSeDescifra() {
        Cifrado.usarClave("clave-uno");
        String c = Cifrado.cifrar("secreto");
        Cifrado.usarClave("clave-dos");
        assertNotEquals("secreto", Cifrado.descifrar(c));
        Cifrado.usarClave("clave-de-prueba");
    }

    // ---------- Verificación en 2 pasos (TOTP) ----------

    @Test
    void totpCoincideConElVectorDeLaRfc6238() {
        // Secreto "12345678901234567890" en Base32; en t=59 s el código de 8 dígitos es 94287082 -> 6 dígitos: 287082
        String secreto = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", new TotpService().codigo(secreto, 59 / 30));
    }

    @Test
    void totpAceptaElCodigoActualYRechazaOtros() {
        TotpService t = new TotpService();
        String s = t.nuevoSecreto();
        assertEquals(32, s.length());
        long ahora = 1_700_000_000_000L;
        String ok = t.codigo(s, ahora / 30_000L);
        assertTrue(t.verificar(s, ok, ahora));
        assertTrue(t.verificar(s, ok.substring(0, 3) + " " + ok.substring(3), ahora));   // con espacio
        assertFalse(t.verificar(s, ok, ahora + 10 * 60_000L));                              // 10 minutos después
        assertFalse(t.verificar(s, "abc", ahora));
        assertFalse(t.verificar(null, ok, ahora));
    }

    // ---------- QR ----------

    @Test
    void qrEsCuadradoYDeterminista() {
        QrCode a = QrCode.codificar("http://localhost:8080/verificar/ABC123");
        QrCode b = QrCode.codificar("http://localhost:8080/verificar/ABC123");
        assertTrue(a.getTam() >= 21);
        assertEquals(a.getTam(), b.getTam());
        for (int y = 0; y < a.getTam(); y++) for (int x = 0; x < a.getTam(); x++) assertEquals(a.get(x, y), b.get(x, y));
        assertTrue(a.toSvg(4).startsWith("<svg"));
    }

    @Test
    void qrDeTextoGigantesco() {
        assertThrows(IllegalArgumentException.class, () -> QrCode.codificar("x".repeat(5000)));
    }

    @Test
    void qrTieneLosTresCuadradosDeEsquina() {
        QrCode q = QrCode.codificar("hola");
        int n = q.getTam();
        assertTrue(q.get(0, 0) && q.get(6, 0) && q.get(0, 6));
        assertTrue(q.get(n - 1, 0) && q.get(0, n - 1));
    }

    // ---------- CIE-10 ----------

    @Test
    void cie10() {
        assertEquals("Hipertensión arterial", Cie10.nombre("i10"));
        assertNull(Cie10.nombre("ZZZ"));
        assertTrue(Cie10.formatoValido("J02.9"));
        assertTrue(Cie10.formatoValido("i10"));
        assertFalse(Cie10.formatoValido("10J"));
        assertFalse(Cie10.formatoValido(null));
        assertTrue(Cie10.todos().size() > 30);
    }

    // ---------- Reglas de la cita ----------

    @Test
    void citaCambiableSoloSiFaltanMasDeDosHoras() {
        Cita lejos = new Cita(LocalDate.now().plusDays(3), LocalTime.of(10, 0), "Cardiología", "Dra. Salas", "Pendiente", null);
        Cita cancelada = new Cita(LocalDate.now().plusDays(3), LocalTime.of(10, 0), "Cardiología", "Dra. Salas", "Cancelada", null);
        Cita pasada = new Cita(LocalDate.now().minusDays(1), LocalTime.of(10, 0), "Cardiología", "Dra. Salas", "Pendiente", null);
        assertTrue(lejos.isCambiable());
        assertFalse(cancelada.isCambiable());
        assertFalse(pasada.isCambiable());
    }

    @Test
    void estadosDeLaCita() {
        Cita c = new Cita(LocalDate.now(), LocalTime.of(9, 0), "x", "y", "Pendiente", null);
        assertTrue(c.isActiva());
        assertEquals("wait", c.getClaseEstado());
        c.setEstado("Cancelada");
        assertFalse(c.isActiva());
        assertEquals("bad", c.getClaseEstado());
        c.setEstado("Atendida");
        assertEquals("ok", c.getClaseEstado());
    }

    @Test
    void citaVirtualSoloSiSePide() {
        Cita c = new Cita();
        assertEquals("Presencial", c.getModalidad());
        c.setModalidad("virtual");
        assertTrue(c.isVirtual());
    }

    // ---------- Archivo de calendario (.ics) ----------

    @Test
    void icsTieneLosCamposBasicos() {
        Cita c = new Cita(LocalDate.of(2026, 11, 3), LocalTime.of(9, 30), "Cardiología", "Dra. Salas, MD", "Confirmada", null);
        String s = new String(Ics.evento(c, 30), StandardCharsets.UTF_8);
        assertTrue(s.startsWith("BEGIN:VCALENDAR"));
        assertTrue(s.contains("DTSTART;TZID=America/Lima:20261103T093000"));
        assertTrue(s.contains("DTEND;TZID=America/Lima:20261103T100000"));
        assertTrue(s.contains("Dra. Salas\\, MD"));       // la coma se escapa
        assertTrue(s.trim().endsWith("END:VCALENDAR"));
    }
    // ---------- Importar pacientes ----------

    @Test
    void importarCsvConPuntoYComaYComillas() {
        var tabla = ImportadorPacientes.leerCsv("nombre;dni;telefono;correo\nAna Pérez;12345678;987654321;ana@correo.com\n\"Gómez, Luis\";87654321;912345678;\nMal DNI;123;999999999;\n");
        var filas = ImportadorPacientes.interpretar(tabla);
        assertEquals(3, filas.size());
        assertNull(filas.get(0).getError());
        assertEquals("Gómez, Luis", filas.get(1).getNombre());
        assertNotNull(filas.get(2).getError());
    }
}
