package com.medcare.controller;

import com.medcare.model.Usuario;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.PoliticaClave;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/** Cambio de contraseña (obligatorio en el primer ingreso de un paciente con la clave inicial). */
@Controller
public class CuentaController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public CuentaController(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios; this.encoder = encoder;
    }

    @GetMapping("/cuenta/clave")
    public String formulario(Authentication auth, Model model) {
        preparar(auth, model);
        return "clave";
    }

    @PostMapping("/cuenta/clave")
    public String cambiar(Authentication auth, Model model, RedirectAttributes ra,
                          @RequestParam String actual, @RequestParam String nueva, @RequestParam String confirmar) {
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        List<String> errores = new ArrayList<>();
        if (!encoder.matches(actual, u.getPassword())) errores.add("La contraseña actual no es correcta.");
        errores.addAll(PoliticaClave.validar(nueva, actual, u.getUsername(), confirmar));
        if (!errores.isEmpty()) {
            preparar(auth, model);
            model.addAttribute("errores", errores);
            return "clave";
        }
        u.setPassword(encoder.encode(nueva));
        u.setDebeCambiarClave(false);
        usuarios.save(u);
        ra.addFlashAttribute("msg", "Contraseña actualizada.");
        return "redirect:" + destino(auth);
    }

    private void preparar(Authentication auth, Model model) {
        boolean forzado = usuarios.findByUsername(auth.getName()).map(Usuario::isDebeCambiarClave).orElse(false);
        model.addAttribute("forzado", forzado);
        model.addAttribute("volver", destino(auth));
    }

    private String destino(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean doctor = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        boolean recepcion = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RECEPCION"));
        return admin ? "/admin/index" : doctor ? "/doctor/agenda" : recepcion ? "/recepcion/citas" : "/paciente/mi-cita";
    }
}
