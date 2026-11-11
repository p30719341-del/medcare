package com.medcare.service;

import com.medcare.model.Cita;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cómo se paga. Modos (medcare.pagos.modo):
 *  - simulado (por defecto): recepción o el admin marcan la cita como cobrada.
 *  - yape: el paciente ve el número de Yape/Plin para pagar y recepción confirma el pago al recibir el comprobante.
 * Una pasarela con tarjeta (Culqi, Niubiz, Izipay...) necesita una cuenta de comercio propia: no está incluida.
 */
@Component("pagos")
public class PagosInfo {

    private final String modo, numero, titular;

    public PagosInfo(@Value("${medcare.pagos.modo:simulado}") String modo,
                     @Value("${medcare.pagos.yape-numero:}") String numero,
                     @Value("${medcare.pagos.yape-titular:}") String titular) {
        this.modo = modo == null ? "simulado" : modo.trim().toLowerCase();
        this.numero = numero == null ? "" : numero.trim();
        this.titular = titular == null ? "" : titular.trim();
    }

    public String getModo() { return modo; }
    public boolean isYape() { return "yape".equals(modo) && !numero.isEmpty(); }

    public String instruccion(Cita c) {
        return "Paga S/ " + c.getPrecio() + " por Yape o Plin al " + numero + (titular.isEmpty() ? "" : " (" + titular + ")")
                + " y muestra el comprobante en recepción. Cuando lo confirmen, aquí verás tu boleta.";
    }
}
