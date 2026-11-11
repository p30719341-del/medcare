package com.medcare.controller;

import com.medcare.config.DosPasosInterceptor;
import com.medcare.model.Usuario;
import com.medcare.repository.UsuarioRepository;
import com.medcare.service.ActividadService;
import com.medcare.service.TotpService;
import com.medcare.util.QrCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Verificación en dos pasos para administrador y doctores: activarla, desactivarla y pedir el código al ingresar. */
@Controller
public class DosPasosController {

    private static final String INTENTOS = "MEDCARE_2FA_INTENTOS";
    private static final int MAX_INTENTOS = 5;

    private final UsuarioRepository usuarios;
    private final TotpService totp;
    private final ActividadService actividad;

    public DosPasosController(UsuarioRepository usuarios, TotpService totp, ActividadService actividad) {
        this.usuarios = usuarios; this.totp = totp; this.actividad = actividad;
    }

    // ---------- Pedir el código después de la contraseña ----------

    @GetMapping("/login/2fa")
    public String pedir(HttpServletRequest req, Model model) {
        if (req.getSession().getAttribute(DosPasosInterceptor.PENDIENTE) == null) return "redirect:/";
        return "dos-pasos";
    }

    @PostMapping("/login/2fa")
    public String verificar(HttpServletRequest req, Authentication auth, Model model, @RequestParam String codigo) {
        HttpSession s = req.getSession();
        if (s.getAttribute(DosPasosInterceptor.PENDIENTE) == null) return "redirect:/";
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        if (totp.verificar(u.getTotpSecreto(), codigo)) {
            s.removeAttribute(DosPasosInterceptor.PENDIENTE);
            s.removeAttribute(INTENTOS);
            actividad.registrar(u.getUsername(), "Ingreso con 2 pasos", "Código correcto");
            return "redirect:" + (u.isDebeCambiarClave() ? "/cuenta/clave" : destino(u));
        }
        int n = s.getAttribute(INTENTOS) == null ? 1 : (Integer) s.getAttribute(INTENTOS) + 1;
        actividad.registrar(u.getUsername(), "Código 2 pasos incorrecto", "Intento " + n + " de " + MAX_INTENTOS);
        if (n >= MAX_INTENTOS) {              // demasiados intentos: se cierra la sesión
            SecurityContextHolder.clearContext();
            s.invalidate();
            return "redirect:/login?error";
        }
        s.setAttribute(INTENTOS, n);
        model.addAttribute("error", "Código incorrecto. Te quedan " + (MAX_INTENTOS - n) + " intentos.");
        return "dos-pasos";
    }

    // ---------- Activar / desactivar ----------

    @GetMapping("/cuenta/2fa")
    public String estado(Authentication auth, Model model, RedirectAttributes ra) {
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        if ("PACIENTE".equals(u.getRol())) return "redirect:/paciente/mi-cita";
        model.addAttribute("activo", u.isTotpActivo());
        model.addAttribute("rol", u.getRol());
        if (!u.isTotpActivo()) {
            if (u.getTotpSecreto() == null) { u.setTotpSecreto(totp.nuevoSecreto()); usuarios.save(u); }
            model.addAttribute("secreto", u.getTotpSecreto());
            model.addAttribute("qrSvg", QrCode.codificar(totp.uri(u.getUsername(), u.getTotpSecreto())).toSvg(2));
        }
        return "cuenta-2fa";
    }

    @PostMapping("/cuenta/2fa/activar")
    public String activar(Authentication auth, RedirectAttributes ra, @RequestParam String codigo) {
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        if ("PACIENTE".equals(u.getRol())) return "redirect:/paciente/mi-cita";
        if (u.isTotpActivo()) return "redirect:/cuenta/2fa";
        if (!totp.verificar(u.getTotpSecreto(), codigo)) {
            ra.addFlashAttribute("error", "Ese código no coincide. Revisa que la hora de tu celular sea automática e inténtalo de nuevo.");
            return "redirect:/cuenta/2fa";
        }
        u.setTotpActivo(true);
        usuarios.save(u);
        actividad.registrar("Verificación en 2 pasos activada", u.getUsername());
        ra.addFlashAttribute("msg", "Verificación en dos pasos activada. Desde ahora te pediremos un código al ingresar.");
        return "redirect:/cuenta/2fa";
    }

    @PostMapping("/cuenta/2fa/desactivar")
    public String desactivar(Authentication auth, RedirectAttributes ra, @RequestParam String codigo) {
        Usuario u = usuarios.findByUsername(auth.getName()).orElseThrow();
        if (!u.isTotpActivo()) return "redirect:/cuenta/2fa";
        if (!totp.verificar(u.getTotpSecreto(), codigo)) {
            ra.addFlashAttribute("error", "Código incorrecto. No se desactivó.");
            return "redirect:/cuenta/2fa";
        }
        u.setTotpActivo(false);
        u.setTotpSecreto(null);
        usuarios.save(u);
        actividad.registrar("Verificación en 2 pasos desactivada", u.getUsername());
        ra.addFlashAttribute("msg", "Verificación en dos pasos desactivada.");
        return "redirect:/cuenta/2fa";
    }

    private static String destino(Usuario u) {
        return "ADMIN".equals(u.getRol()) ? "/admin/index" : "DOCTOR".equals(u.getRol()) ? "/doctor/agenda" : "RECEPCION".equals(u.getRol()) ? "/recepcion/citas" : "/paciente/mi-cita";
    }
}
