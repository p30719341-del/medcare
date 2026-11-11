package com.medcare.service;

import com.medcare.model.Doctor;
import com.medcare.model.Usuario;
import com.medcare.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

/** Crea el usuario con el que cada doctor entra a su agenda. */
@Service
public class DoctorAccesoService {

    public static final String CLAVE_INICIAL = "doctor123";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public DoctorAccesoService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios; this.encoder = encoder;
    }

    /** "Dra. Quispe" -> usuario "quispe". Si ya existe, agrega un número. forzarCambio: pedir cambiar la clave al primer ingreso. */
    public String crear(Doctor d, boolean forzarCambio) {
        String base = usuarioBase(d.getNombre());
        String user = base;
        for (int i = 2; usuarios.findByUsername(user).isPresent(); i++) user = base + i;
        Usuario u = new Usuario(user, encoder.encode(CLAVE_INICIAL), "DOCTOR", null);
        u.setDoctor(d);
        u.setDebeCambiarClave(forzarCambio);
        usuarios.save(u);
        return user;
    }

    static String usuarioBase(String nombre) {
        String n = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        String[] partes = n.split("[^a-z]+");
        String ultimo = "";
        for (String p : partes) if (!p.isEmpty()) ultimo = p;
        return ultimo.isEmpty() ? "doctor" : ultimo;
    }
}
