package com.medcare.service;

import java.util.List;

/** Especialidades de la clínica y reglas generales. Los doctores y sus horarios están en la base de datos. */
public final class Catalogo {

    private Catalogo() {}

    /** Días hacia adelante en los que se puede reservar. */
    public static final int DIAS_MAX = 30;

    /** Horas mínimas de anticipación para que un paciente cancele o reprograme su cita. */
    public static final int HORAS_CAMBIO = 2;

    /** Consultorios físicos de la clínica. */
    public static final List<String> CONSULTORIOS = List.of("C-101", "C-102", "C-103", "C-104", "C-105", "C-106", "C-107", "C-108");

    /** Máximo de pacientes extra (sobreturnos) por doctor y día. */
    public static final int SOBRETURNOS_MAX = 2;

    public static class Especialidad {
        private final String nombre, descripcion, icono;
        public Especialidad(String nombre, String descripcion, String icono) {
            this.nombre = nombre; this.descripcion = descripcion; this.icono = icono;
        }
        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }
        public String getIcono() { return icono; }
    }

    public static final List<Especialidad> ESPECIALIDADES = List.of(
        new Especialidad("Medicina general", "Chequeos, control preventivo y atención de adultos.", "general"),
        new Especialidad("Cardiología", "Cuidado del corazón y control de la presión arterial.", "cardio"),
        new Especialidad("Odontología", "Limpieza, tratamiento de caries y salud bucal.", "odonto"),
        new Especialidad("Pediatría", "Atención de niños y adolescentes, controles de crecimiento.", "pediatria"),
        new Especialidad("Dermatología", "Cuidado de la piel, el cabello y las uñas.", "dermato"),
        new Especialidad("Ginecología", "Salud de la mujer y controles de rutina.", "gineco")
    );

    public static List<String> nombresEspecialidades() {
        return ESPECIALIDADES.stream().map(Especialidad::getNombre).toList();
    }
}
