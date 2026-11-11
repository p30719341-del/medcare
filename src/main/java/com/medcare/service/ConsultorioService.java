package com.medcare.service;

import com.medcare.model.Cita;
import com.medcare.model.Doctor;
import com.medcare.repository.CitaRepository;
import com.medcare.repository.DoctorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Asigna a cada cita presencial un consultorio libre a esa hora, para que dos doctores nunca compartan sala. */
@Service
public class ConsultorioService {

    private final CitaRepository citas;
    private final DoctorRepository doctores;

    public ConsultorioService(CitaRepository citas, DoctorRepository doctores) {
        this.citas = citas; this.doctores = doctores;
    }

    /**
     * Elige el consultorio: el preferido del doctor si está libre; si no, el primero libre.
     * ignorarCita: la cita que se está moviendo (no cuenta como ocupante). Lanza ReglaNegocioException si no queda ninguno.
     */
    public String asignar(String doctor, LocalDate fecha, LocalTime hora, Long ignorarCita) {
        List<Cita> mismas = citas.findByFechaAndHora(fecha, hora).stream()
                .filter(c -> c.isActiva() && !"Virtual".equals(c.getModalidad()) && c.getConsultorio() != null
                        && (ignorarCita == null || !ignorarCita.equals(c.getId())))
                .toList();
        // El mismo doctor atendiendo a otro paciente a la misma hora (sobreturno) usa su misma sala.
        for (Cita c : mismas) if (c.getDoctor().equals(doctor)) return c.getConsultorio();
        Set<String> ocupados = new HashSet<>();
        for (Cita c : mismas) ocupados.add(c.getConsultorio());
        String preferido = doctores.findByNombre(doctor).map(Doctor::getConsultorio).orElse(null);
        if (preferido != null && !ocupados.contains(preferido)) return preferido;
        for (String s : Catalogo.CONSULTORIOS) if (!ocupados.contains(s)) return s;
        throw new ReglaNegocioException("No hay consultorio libre a esa hora. Elige otro horario.");
    }
}
