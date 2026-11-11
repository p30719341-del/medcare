package com.medcare;

import com.medcare.model.Cita;
import com.medcare.model.ReservaForm;
import com.medcare.service.ReglaNegocioException;
import com.medcare.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de reserva con la aplicación completa: horarios, choques y cancelaciones. Cada prueba se deshace al terminar. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservaServiceTest {

    @Autowired ReservaService reservas;

    /** Próximo lunes dentro de ~3 semanas (el Dr. Ramírez atiende de lunes a sábado). */
    private LocalDate dia() {
        LocalDate d = LocalDate.now().plusDays(21);
        while (d.getDayOfWeek() != java.time.DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }

    private ReservaForm form(String dni, String tel, LocalTime hora) {
        ReservaForm f = new ReservaForm();
        f.setNombre("Paciente de Prueba");
        f.setDni(dni);
        f.setTelefono(tel);
        f.setEspecialidad("Medicina general");
        f.setDoctor("Dr. Ramírez");
        f.setFecha(dia());
        f.setHora(hora);
        return f;
    }

    @Test
    void unHorarioLibreApareceEnLaListaYSeOcupaAlReservar() {
        LocalTime h = LocalTime.of(10, 0);
        assertTrue(reservas.horasLibres("Dr. Ramírez", dia()).contains(h));
        Cita c = reservas.reservar(form("90000001", "900000001", h)).getCita();
        assertEquals("Pendiente", c.getEstado());
        assertFalse(reservas.horasLibres("Dr. Ramírez", dia()).contains(h));
    }

    @Test
    void cancelarLiberaElHorario() {
        LocalTime h = LocalTime.of(11, 0);
        Cita c = reservas.reservar(form("90000002", "900000002", h)).getCita();
        reservas.cancelar(c.getId(), c.getPaciente());
        assertTrue(reservas.horasLibres("Dr. Ramírez", dia()).contains(h));
    }

    @Test
    void unPacienteNoPuedeCancelarLaCitaDeOtro() {
        Cita a = reservas.reservar(form("90000003", "900000003", LocalTime.of(9, 0))).getCita();
        Cita b = reservas.reservar(form("90000004", "900000004", LocalTime.of(9, 30))).getCita();
        assertThrows(ReglaNegocioException.class, () -> reservas.cancelar(a.getId(), b.getPaciente()));
    }

    @Test
    void noSePuedeReprogramarAUnHorarioOcupado() {
        Cita a = reservas.reservar(form("90000005", "900000005", LocalTime.of(15, 0))).getCita();
        Cita b = reservas.reservar(form("90000006", "900000006", LocalTime.of(15, 30))).getCita();
        assertThrows(Exception.class, () -> reservas.reprogramar(b.getId(), b.getPaciente(), dia(), LocalTime.of(15, 0)));
        assertEquals(LocalTime.of(15, 30), reservas.obtenerCita(b.getId()).getHora());
        assertNotNull(a.getId());
    }

    @Test
    void noHayHorariosUnDomingoNiEnElPasado() {
        LocalDate domingo = dia().plusDays(6);
        assertTrue(reservas.horasLibres("Dr. Ramírez", domingo).isEmpty());
        assertTrue(reservas.horasLibres("Dr. Ramírez", LocalDate.now().minusDays(1)).isEmpty());
        assertTrue(reservas.horasLibres("Doctor Inexistente", dia()).isEmpty());
    }
}
