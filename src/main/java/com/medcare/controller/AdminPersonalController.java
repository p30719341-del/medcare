package com.medcare.controller;

import com.medcare.model.Usuario;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.ActividadService;
import com.medcare.service.PoliticaClave;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

/** Administrador: ver el personal con acceso (admin, doctores, recepción), crear recepcionistas y desbloquear cuentas. */
@Controller
@RequestMapping("/admin/personal")
public class AdminPersonalController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final ActividadService actividad;

    public AdminPersonalController(UsuarioRepository usuarios, PasswordEncoder encoder, ActividadService actividad) {
        this.usuarios = usuarios; this.encoder = encoder; this.actividad = actividad;
    }

    @GetMapping
    public String lista(Model model) {
        List<Usuario> personal = usuarios.findAll().stream().filter(u -> !"PACIENTE".equals(u.getRol()))
                .sorted(Comparator.comparing(Usuario::getRol).thenComparing(Usuario::getUsername)).toList();
        model.addAttribute("personal", personal);
        return "personal";
    }

    @PostMapping("/nuevo")
    public String crear(@RequestParam String usuario, @RequestParam String clave, @RequestParam(defaultValue = "") String email, RedirectAttributes ra) {
        String u = usuario == null ? "" : usuario.trim().toLowerCase();
        if (!u.matches("[a-z][a-z0-9._]{2,19}")) {
            ra.addFlashAttribute("error", "El usuario debe tener de 3 a 20 caracteres: letras minúsculas, números, punto o guion bajo, y empezar con una letra.");
        } else if (usuarios.findByUsername(u).isPresent()) {
            ra.addFlashAttribute("error", "Ese usuario ya existe.");
        } else {
            List<String> errores = PoliticaClave.validar(clave, null, u, clave);
            if (!errores.isEmpty()) {
                ra.addFlashAttribute("error", errores.get(0));
            } else {
                Usuario r = new Usuario(u, encoder.encode(clave), "RECEPCION", null);
                r.setEmail(email);
                r.setDebeCambiarClave(true);          // la cambia en su primer ingreso
                usuarios.save(r);
                actividad.registrar("Recepcionista creado", u);
                ra.addFlashAttribute("msg", "Recepcionista creado. Deberá cambiar su clave al entrar.");
            }
        }
        return "redirect:/admin/personal";
    }

    @PostMapping("/{id}/desbloquear")
    public String desbloquear(@PathVariable Long id, RedirectAttributes ra) {
        usuarios.findById(id).ifPresent(u -> {
            u.setIntentosFallidos(0); u.setBloqueadoHasta(null); usuarios.save(u);
            actividad.registrar("Cuenta desbloqueada", u.getUsername());
        });
        ra.addFlashAttribute("msg", "Cuenta desbloqueada.");
        return "redirect:/admin/personal";
    }

    /** Solo se pueden eliminar cuentas de recepción (no el admin ni los doctores). */
    @PostMapping("/{id}/borrar")
    public String borrar(@PathVariable Long id, RedirectAttributes ra) {
        usuarios.findById(id).filter(u -> "RECEPCION".equals(u.getRol())).ifPresentOrElse(u -> {
            usuarios.delete(u);
            actividad.registrar("Recepcionista eliminado", u.getUsername());
            ra.addFlashAttribute("msg", "Cuenta eliminada.");
        }, () -> ra.addFlashAttribute("error", "Solo se pueden eliminar cuentas de recepción."));
        return "redirect:/admin/personal";
    }
}
