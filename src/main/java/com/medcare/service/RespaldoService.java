package com.medcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copias de seguridad de la base de datos. Con H2 (la base por defecto) se genera un archivo .sql.gz con
 * todo el contenido. Con MySQL hay que usar mysqldump (el panel lo avisa). Se guardan las últimas 7.
 */
@Service
public class RespaldoService {

    private static final Logger log = LoggerFactory.getLogger(RespaldoService.class);
    static final Pattern NOMBRE = Pattern.compile("respaldo-\\d{8}-\\d{6}\\.sql\\.gz");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int GUARDAR = 7;

    /** Una copia ya creada. */
    public static class Copia {
        private final String nombre; private final long tamano; private final LocalDateTime fecha;
        public Copia(String nombre, long tamano, LocalDateTime fecha) { this.nombre = nombre; this.tamano = tamano; this.fecha = fecha; }
        public String getNombre() { return nombre; }
        public String getTamanoTexto() { return tamano >= 1_000_000 ? String.format("%.1f MB", tamano / 1_000_000.0) : Math.max(1, tamano / 1000) + " KB"; }
        public LocalDateTime getFecha() { return fecha; }
    }

    private final JdbcTemplate jdbc;
    private final ActividadService actividad;
    private final Path carpeta;
    private final boolean automatico;
    private final boolean esH2;

    public RespaldoService(JdbcTemplate jdbc, ActividadService actividad,
                           @Value("${medcare.backup.dir:./data/respaldos}") String dir,
                           @Value("${medcare.backup.enabled:true}") boolean automatico,
                           @Value("${spring.datasource.url:}") String url) {
        this.jdbc = jdbc; this.actividad = actividad; this.automatico = automatico;
        this.carpeta = Paths.get(dir).toAbsolutePath().normalize();
        this.esH2 = url.startsWith("jdbc:h2:");
    }

    public boolean isSoportado() { return esH2; }

    /** Copia diaria a las 2:30 a. m. */
    @Scheduled(cron = "0 30 2 * * *")
    public void diaria() {
        if (!automatico || !esH2) return;
        try { crear("sistema"); } catch (Exception e) { log.warn("No se pudo crear la copia de seguridad: {}", e.getMessage()); }
    }

    public Copia crear(String usuario) {
        if (!esH2) throw new ReglaNegocioException("Con MySQL las copias se hacen con mysqldump (ver README).");
        try {
            Files.createDirectories(carpeta);
            String nombre = "respaldo-" + LocalDateTime.now().format(SELLO) + ".sql.gz";
            String ruta = carpeta.resolve(nombre).toString().replace("\\", "/").replace("'", "''");
            jdbc.execute("SCRIPT TO '" + ruta + "' COMPRESSION GZIP");
            limpiarAntiguas();
            actividad.registrar(usuario, "Copia de seguridad", nombre);
            return new Copia(nombre, Files.size(carpeta.resolve(nombre)), LocalDateTime.now());
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo guardar la copia: " + e.getMessage());
        }
    }

    public List<Copia> listar() {
        List<Copia> r = new ArrayList<>();
        if (!Files.isDirectory(carpeta)) return r;
        try (Stream<Path> s = Files.list(carpeta)) {
            s.filter(p -> NOMBRE.matcher(p.getFileName().toString()).matches()).forEach(p -> {
                try {
                    r.add(new Copia(p.getFileName().toString(), Files.size(p),
                            LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), java.time.ZoneId.systemDefault())));
                } catch (IOException ignorado) { }
            });
        } catch (IOException ignorado) { }
        r.sort(Comparator.comparing(Copia::getNombre).reversed());
        return r;
    }

    /** Ruta de una copia por su nombre; el nombre debe tener el formato exacto (evita salirse de la carpeta). */
    public Path ruta(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) throw new ReglaNegocioException("Copia no encontrada.");
        Path p = carpeta.resolve(nombre).normalize();
        if (!p.startsWith(carpeta) || !Files.isRegularFile(p)) throw new ReglaNegocioException("Copia no encontrada.");
        return p;
    }

    private void limpiarAntiguas() {
        List<Copia> todas = listar();
        for (int i = GUARDAR; i < todas.size(); i++) {
            try { Files.deleteIfExists(carpeta.resolve(todas.get(i).getNombre())); } catch (IOException ignorado) { }
        }
    }
}
