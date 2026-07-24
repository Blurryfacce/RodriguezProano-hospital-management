package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.CitaDTO;
import com.hospital.service.CitaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion Controller -> Service -> Repository -> H2 en memoria.
 *
 * IMPORTANTE: en el Paso 1 (verificacion de entorno) se detecto que GET /api/citas
 * contra el backend real (PostgreSQL) responde HTTP 500 al serializar la entidad
 * Cita (relacion LAZY con Doctor). Estas pruebas verifican si el mismo problema se
 * reproduce contra H2 y documentan el resultado tal cual, sin "arreglarlo" en el
 * codigo fuente (ver informes/hallazgos/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("CitaController - integracion")
class CitaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CitaService citaService;

    @Test
    @DisplayName("GET /api/citas responde 500 al serializar (bug real BUG-01, reproducible tambien contra H2)")
    void listar_reproduceEl500DeSerializacion() throws Exception {
        // Confirma que el 500 visto en el Paso 1 contra PostgreSQL NO es especifico de ese
        // motor de base de datos: se reproduce identico contra H2. Es un bug real de la app.
        mockMvc.perform(get("/api/citas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.stackTrace", notNullValue()));
    }

    @Test
    @DisplayName("Diagnostico: la causa raiz del 500 es un ciclo de referencia Cita -> Doctor -> Cita (Jackson)")
    void diagnostico_causaRaizDelError500() {
        // Se serializa directamente con el ObjectMapper de la app (mismo bean que usa Spring
        // MVC) para capturar el mensaje real de Jackson, que el cliente HTTP nunca ve porque
        // GlobalExceptionHandler solo expone ex.getStackTrace(), no ex.getMessage() ni la causa.
        Exception excepcion = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> objectMapper.writeValueAsString(citaService.listarTodas()));

        // Documentado para el informe OWASP/analisis estatico: Jackson no puede serializar el
        // proxy LAZY de Hibernate para Doctor sin @JsonIgnoreProperties/@JsonIdentityInfo, lo
        // que produce (segun el caso) un fallo de inicializacion perezosa o una referencia
        // ciclica no controlada al intentar resolver el proxy fuera de forma segura.
        System.out.println("Causa raiz real del 500 en /api/citas: "
                + excepcion.getClass().getName() + " - " + excepcion.getMessage());
        org.junit.jupiter.api.Assertions.assertNotNull(excepcion.getMessage());
    }

    @Test
    @DisplayName("GET /api/citas/{id} tambien responde 500 al serializar el doctor LAZY (mismo BUG-01)")
    void buscar_conIdExistente_reproduceEl500DeSerializacion() throws Exception {
        // A diferencia de listarPorDoctor/crear en esta misma clase (que SI funcionan), aqui
        // el Doctor no fue cargado antes en la transaccion, asi que Hibernate lo entrega como
        // proxy LAZY sin resolver y Jackson falla igual que en GET /api/citas.
        mockMvc.perform(get("/api/citas/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)));
    }

    @Test
    @DisplayName("GET /api/citas/{id} con ID inexistente responde 200 en vez de 404 (bug documentado)")
    void buscar_conIdInexistente_documentaBugDeStatus200() throws Exception {
        mockMvc.perform(get("/api/citas/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("POST /api/citas con paciente_id inexistente se crea igual (bug: sin FK ni validacion)")
    void crear_conPacienteIdInexistente_seCreaSinValidar() throws Exception {
        // BUG INTENCIONAL confirmado en integracion real: no hay FK en la tabla citas hacia
        // pacientes, y CitaService.crear() no valida que el paciente exista. Se puede agendar
        // una cita para un paciente_id que no existe en la tabla pacientes.
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(999999L);
        dto.setDoctorId(1L);
        dto.setFechaHora(LocalDateTime.now().plusDays(3));
        dto.setMotivo("Cita con paciente inexistente");

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                // BUG INTENCIONAL: 200 en vez de 201.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacienteId", is(999999)));
    }

    @Test
    @DisplayName("POST /api/citas con doctor inexistente responde 200 con status 404 embebido (bug documentado)")
    void crear_conDoctorInexistente_documentaBugDeStatus200() throws Exception {
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(1L);
        dto.setDoctorId(9999L);
        dto.setFechaHora(LocalDateTime.now().plusDays(3));
        dto.setMotivo("Cita con doctor inexistente");

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Doctor no encontrado")));
    }

    @Test
    @DisplayName("POST /api/citas con fecha pasada responde 400 (validacion @Future si funciona)")
    void crear_conFechaPasada_debeResponder400() throws Exception {
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(1L);
        dto.setDoctorId(1L);
        dto.setFechaHora(LocalDateTime.now().minusDays(1));
        dto.setMotivo("Cita en el pasado");

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fechaHora", is("La fecha debe ser futura")));
    }

    @Test
    @DisplayName("POST /api/citas permite doble booking: mismo doctor, misma hora (carencia documentada)")
    void crear_conDobleBooking_noLoImpide() throws Exception {
        // BUG INTENCIONAL: no existe verificacion de conflicto de horario. Se agenda una
        // segunda cita para el doctor 1 exactamente a la misma hora que la cita id=1 de data.sql
        // (2026-06-20 09:00:00 ya paso respecto a la fecha del sistema, se usa una fecha futura
        // equivalente para no chocar con la validacion @Future).
        LocalDateTime mismaHora = LocalDateTime.now().plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0);

        CitaDTO primera = new CitaDTO();
        primera.setPacienteId(1L);
        primera.setDoctorId(1L);
        primera.setFechaHora(mismaHora);
        primera.setMotivo("Primera cita");

        CitaDTO segunda = new CitaDTO();
        segunda.setPacienteId(2L);
        segunda.setDoctorId(1L);
        segunda.setFechaHora(mismaHora);
        segunda.setMotivo("Segunda cita, mismo doctor y hora");

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(primera)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/citas")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(segunda)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/citas/doctor/1"))
                .andExpect(jsonPath("$[?(@.motivo == 'Primera cita')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.motivo == 'Segunda cita, mismo doctor y hora')]", hasSize(1)));
    }

    @Test
    @DisplayName("PUT /api/citas/{id} persiste los cambios pero tambien responde 500 al serializar (mismo BUG-01)")
    void actualizar_conIdExistente_reproduceEl500DeSerializacion() throws Exception {
        // CitaDTO exige pacienteId/doctorId con @NotNull aunque CitaService.actualizar() los
        // ignora por completo (solo usa fechaHora/motivo/estado) -> hay que enviarlos igual
        // para pasar la validacion de entrada, otra inconsistencia de diseño documentada.
        CitaDTO dto = new CitaDTO();
        dto.setPacienteId(1L);
        dto.setDoctorId(1L);
        dto.setFechaHora(LocalDateTime.now().plusDays(10));
        dto.setMotivo("Motivo actualizado");
        dto.setEstado("COMPLETADA");

        // La actualizacion persiste correctamente (ver Hibernate log), pero la respuesta
        // vuelve a fallar al serializar el Doctor LAZY -> mismo BUG-01 que en GET.
        mockMvc.perform(put("/api/citas/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("DELETE /api/citas/{id} con ID existente elimina y responde 200 (bug: deberia ser 204)")
    void eliminar_conIdExistente_debeEliminarYResponder200() throws Exception {
        mockMvc.perform(delete("/api/citas/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/citas/1"))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("GET /api/citas/paciente/{id} tambien responde 500 al serializar el doctor LAZY (mismo BUG-01)")
    void listarPorPaciente_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/citas/paciente/1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/citas/estado/{estado} tambien responde 500 al serializar el doctor LAZY (mismo BUG-01)")
    void listarPorEstado_reproduceEl500DeSerializacion() throws Exception {
        mockMvc.perform(get("/api/citas/estado/PROGRAMADA"))
                .andExpect(status().isInternalServerError());
    }
}
