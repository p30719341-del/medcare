package com.medcare.controller;

import com.medcare.model.Paciente;
import com.medcare.service.ActividadService;
import com.medcare.service.ImportadorPacientes;
import com.medcare.service.ImportadorPacientes.Fila;
import com.medcare.service.iPacienteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Importar pacientes desde un Excel (.xlsx) o CSV. Cada paciente nuevo recibe su acceso (usuario = DNI, clave inicial = DNI). */
@Controller
@RequestMapping("/admin/importar")
public class AdminImportarController {

    private static final long MAX_BYTES = 2_000_000;

    private final iPacienteService pacientes;
    private final ActividadService actividad;

    public AdminImportarController(iPacienteService pacientes, ActividadService actividad) {
        this.pacientes = pacientes; this.actividad = actividad;
    }

    @GetMapping
    public String formulario(Model model) {
        model.addAttribute("maxFilas", ImportadorPacientes.MAX_FILAS);
        return "importar";
    }

    @GetMapping("/plantilla.csv")
    public ResponseEntity<byte[]> plantilla() {
        String csv = "﻿Nombre;DNI;Telefono;Correo\r\nAna Pérez Rojas;70123456;987654321;ana@ejemplo.com\r\nLuis Soto Vega;71234567;956123456;\r\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-pacientes.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping
    public String importar(@RequestParam("archivo") MultipartFile archivo, Model model) {
        model.addAttribute("maxFilas", ImportadorPacientes.MAX_FILAS);
        if (archivo == null || archivo.isEmpty()) { model.addAttribute("error", "Elige un archivo."); return "importar"; }
        if (archivo.getSize() > MAX_BYTES) { model.addAttribute("error", "El archivo supera los 2 MB."); return "importar"; }
        List<Fila> filas;
        try {
            filas = ImportadorPacientes.interpretar(ImportadorPacientes.leer(archivo.getOriginalFilename(), archivo.getBytes()));
        } catch (IOException e) {
            model.addAttribute("error", e.getMessage());
            return "importar";
        }
        if (filas.isEmpty()) { model.addAttribute("error", "No encontramos pacientes en el archivo."); return "importar"; }
        if (filas.size() > ImportadorPacientes.MAX_FILAS) {
            model.addAttribute("error", "El archivo tiene más de " + ImportadorPacientes.MAX_FILAS + " filas. Divídelo en partes.");
            return "importar";
        }
        int creados = 0, repetidos = 0;
        Set<String> vistos = new HashSet<>();
        List<Fila> problemas = new ArrayList<>();
        for (Fila f : filas) {
            if (f.error != null) { problemas.add(f); continue; }
            if (!vistos.add(f.dni) || pacientes.existeDni(f.dni)) { repetidos++; continue; }
            try {
                Paciente p = new Paciente(f.nombre, f.dni, f.telefono, LocalDate.now(), "Activo");
                p.setEmail(f.correo);
                pacientes.save(p);
                creados++;
            } catch (RuntimeException e) {
                f.error = "No se pudo guardar.";
                problemas.add(f);
            }
        }
        actividad.registrar("Importación de pacientes", creados + " creados, " + repetidos + " repetidos, " + problemas.size() + " con error");
        model.addAttribute("creados", creados);
        model.addAttribute("repetidos", repetidos);
        model.addAttribute("problemas", problemas);
        model.addAttribute("total", filas.size());
        return "importar";
    }
}
