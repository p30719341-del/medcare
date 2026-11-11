package com.medcare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas de permisos por rol con la aplicación completa (base H2 en memoria, ver application-test.properties). */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SeguridadWebTest {

    @Autowired MockMvc mvc;

    @Test
    void portadaYLoginSonPublicos() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/registro")).andExpect(status().isOk());
    }

    @Test
    void sinSesionTodoLoPrivadoRedirigeAlLogin() throws Exception {
        for (String url : new String[]{"/admin/index", "/admin/citas", "/doctor/agenda", "/paciente/mi-cita", "/recepcion/citas", "/cuenta/clave"}) {
            mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void unPacienteNoEntraAlPanelNiALaAgendaDelDoctor() throws Exception {
        mvc.perform(get("/admin/index")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reportes")).andExpect(status().isForbidden());
        mvc.perform(get("/doctor/agenda")).andExpect(status().isForbidden());
        mvc.perform(get("/recepcion/citas")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void unDoctorNoEntraAlAdminNiAlPortalDelPaciente() throws Exception {
        mvc.perform(get("/admin/citas")).andExpect(status().isForbidden());
        mvc.perform(get("/paciente/mi-cita")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPCION")
    void recepcionNoVeLosReportesNiLasCopias() throws Exception {
        mvc.perform(get("/admin/reportes")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/respaldos")).andExpect(status().isForbidden());
    }

    @Test
    void sinTokenCsrfLosFormulariosSeRechazan() throws Exception {
        mvc.perform(post("/reservar")).andExpect(status().isForbidden());
    }

    @Test
    void laVerificacionDeRecetasEsPublicaYNoRompeConCodigosFalsos() throws Exception {
        mvc.perform(get("/verificar/CODIGO-FALSO")).andExpect(status().isOk());
    }
}
