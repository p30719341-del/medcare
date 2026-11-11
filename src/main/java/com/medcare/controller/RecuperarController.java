package com.medcare.controller;

import com.medcare.service.ClaveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Recuperar contraseña por correo. */
@Controller
public class RecuperarController {

    private final ClaveService claves;

    public RecuperarController(ClaveService claves) { this.claves = claves; }

    @GetMapping("/recuperar")
    public String pedir() { return "recuperar"; }

    @PostMapping("/recuperar")
    public String enviar(@RequestParam(defaultValue = "") String dato, Model model) {
        claves.solicitar(dato);
        model.addAttribute("enviado", true);      // mismo mensaje exista o no la cuenta
        return "recuperar";
    }

    @GetMapping("/recuperar/{token}")
    public String nueva(@PathVariable String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valido", claves.tokenValido(token));
        return "recuperar-nueva";
    }

    @PostMapping("/recuperar/{token}")
    public String guardar(@PathVariable String token, @RequestParam String nueva, @RequestParam String confirmar, Model model) {
        List<String> errores = claves.restablecer(token, nueva, confirmar);
        if (errores.isEmpty()) return "redirect:/login?restablecida";
        model.addAttribute("token", token);
        model.addAttribute("valido", claves.tokenValido(token));
        model.addAttribute("errores", errores);
        return "recuperar-nueva";
    }
}
