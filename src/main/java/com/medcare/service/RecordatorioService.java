package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Recordatorio por correo de las citas de las próximas 24 horas. Corre solo cada hora. */
@Service
public class RecordatorioService {

    private final CitaRepository citas;
    private final NotificacionService notificaciones;
    private final ActividadService actividad;
    private final boolean automatico;
    private final WhatsAppService whatsapp;
    private volatile java.time.LocalDateTime ultimaEjecucion;
    private volatile int ultimoTotal;

    public RecordatorioService(CitaRepository citas, NotificacionService notificaciones, ActividadService actividad, WhatsAppService whatsapp,
                               @Value("${medcare.recordatorios.enabled:true}") boolean automatico) {
        this.citas = citas; this.notificaciones = notificaciones; this.actividad = actividad; this.automatico = automatico; this.whatsapp = whatsapp;
    }

    public boolean isAutomatico() { return automatico; }
    public java.time.LocalDateTime getUltimaEjecucion() { return ultimaEjecucion; }
    public int getUltimoTotal() { return ultimoTotal; }

    @Scheduled(cron = "0 0 * * * *")     // al inicio de cada hora
    public void automatico() {
        if (automatico) enviarProximos();
    }

    /** Envía el recordatorio de las citas que empiezan en las próximas 24 h y aún no se recordaron. Devuelve cuántas. */
    @Transactional
    public int enviarProximos() {
        LocalDateTime ahora = LocalDateTime.now(), limite = ahora.plusHours(24);
        LocalDate hoy = ahora.toLocalDate();
        List<Cita> candidatas = citas.findByFechaBetweenAndEstadoIn(hoy, limite.toLocalDate(), List.of("Pendiente", "Confirmada"));
        int n = 0;
        for (Cita c : candidatas) {
            LocalDateTime cuando = c.getFecha().atTime(c.getHora());
            if (c.isRecordada() || !cuando.isAfter(ahora) || cuando.isAfter(limite)) continue;
            notificaciones.recordatorio(c);
            whatsapp.recordatorio(c);
            c.setRecordada(true);
            citas.save(c);
            n++;
        }
        if (n > 0) actividad.registrar("Recordatorios enviados", n + " cita(s)");
        ultimaEjecucion = LocalDateTime.now(); ultimoTotal = n;
        return n;
    }
}
