package com.medcare.config;

import com.medcare.util.Cifrado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Al arrancar, cifra los datos médicos que una versión anterior guardó en texto plano
 * (los valores ya cifrados llevan el prefijo "enc1:" y se dejan igual). Se ejecuta antes que DataLoader.
 */
@Component
@Order(0)
public class CifradoMigracion implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CifradoMigracion.class);
    private static final String[][] CAMPOS = {
        {"cita", "diagnostico"}, {"cita", "receta"}, {"cita", "triaje"},
        {"paciente", "alergias"}, {"paciente", "antecedentes"}, {"mensaje", "texto"},
    };

    private final JdbcTemplate jdbc;

    public CifradoMigracion(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        int total = 0;
        for (String[] c : CAMPOS) {
            try {
                List<Map<String, Object>> filas = jdbc.queryForList("select id, " + c[1] + " as valor from " + c[0] + " where " + c[1] + " is not null");
                for (Map<String, Object> f : filas) {
                    String v = String.valueOf(f.get("valor"));
                    if (v.isEmpty() || v.startsWith(Cifrado.PREFIJO)) continue;
                    jdbc.update("update " + c[0] + " set " + c[1] + " = ? where id = ?", Cifrado.cifrar(v), f.get("id"));
                    total++;
                }
            } catch (Exception e) {
                log.debug("Sin datos que cifrar en {}.{}: {}", c[0], c[1], e.getMessage());   // la tabla aún no existe: no hay nada que migrar
            }
        }
        if (total > 0) log.info("Se cifraron {} dato(s) médicos que estaban en texto plano.", total);
        if (Cifrado.usaClaveDemo()) {
            log.warn("Los datos médicos se cifran con la clave de DEMOSTRACIÓN. Define MEDCARE_CLAVE_CIFRADO antes de usar datos reales (y no la pierdas).");
        }
    }
}
