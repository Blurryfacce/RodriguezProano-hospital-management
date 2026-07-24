package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.DoctorDTO;
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
 * Incluye una prueba de concepto de inyeccion SQL real contra
 * GET /api/doctores/buscar-especialidad (ver informes/hallazgos/03-owasp-preliminar.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("DoctorController - integracion")
class DoctorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/doctores retorna los 4 doctores precargados por data.sql")
    void listar_debeRetornarDoctoresPrecargados() throws Exception {
        mockMvc.perform(get("/api/doctores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("GET /api/doctores/{id} con ID existente retorna el doctor")
    void buscar_conIdExistente_debeRetornarDoctor() throws Exception {
        mockMvc.perform(get("/api/doctores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Elena")))
                .andExpect(jsonPath("$.especialidad", is("Cardiologia")));
    }

    @Test
    @DisplayName("GET /api/doctores/{id} con ID inexistente responde 200 en vez de 404 (bug documentado)")
    void buscar_conIdInexistente_documentaBugDeStatus200() throws Exception {
        mockMvc.perform(get("/api/doctores/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("GET /api/doctores/{id} del doctor con especialidad NULL (deuda tecnica del esquema) responde 200")
    void buscar_doctorConEspecialidadNull_debeResponderOk() throws Exception {
        // Diego Morales (id=4 en data.sql) tiene especialidad NULL: la tabla doctores
        // no tiene restriccion NOT NULL, y el DTO tampoco exige @NotBlank en especialidad.
        mockMvc.perform(get("/api/doctores/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Diego")))
                .andExpect(jsonPath("$.especialidad", nullValue()));
    }

    @Test
    @DisplayName("POST /api/doctores con datos validos crea el doctor y responde 200 (bug: deberia ser 201)")
    void crear_conDatosValidos_debeCrearDoctorYResponder200() throws Exception {
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Patricia");
        dto.setApellido("Vega");
        dto.setEspecialidad("Neurologia");
        dto.setEmail("patricia.vega@hospital.com");
        dto.setTelefono("0980112233");
        dto.setConsultorio("CONS-402");

        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.especialidad", is("Neurologia")));

        mockMvc.perform(get("/api/doctores"))
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @DisplayName("POST /api/doctores sin especialidad es aceptado (bug: DTO no exige @NotBlank pese a ser requerida)")
    void crear_sinEspecialidad_esAceptadoPeseASerRequeridaEnHU02() throws Exception {
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Roberto");
        dto.setApellido("Nunez");
        dto.setEmail("roberto.nunez@hospital.com");

        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad", nullValue()));
    }

    @Test
    @DisplayName("POST /api/doctores sin nombre (obligatorio) responde 400 con errores de validacion")
    void crear_sinNombre_debeResponder400ConErrores() throws Exception {
        DoctorDTO dto = new DoctorDTO();
        dto.setApellido("SinNombre");

        mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre", is("El nombre es obligatorio")));
    }

    @Test
    @DisplayName("PUT /api/doctores/{id} con ID existente actualiza los datos")
    void actualizar_conIdExistente_debeActualizarDoctor() throws Exception {
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Elena");
        dto.setApellido("Rodriguez");
        dto.setEspecialidad("Cardiologia Intervencionista");
        dto.setEmail("elena.rodriguez@hospital.com");
        dto.setTelefono("0943210987");
        dto.setConsultorio("CONS-101");

        mockMvc.perform(put("/api/doctores/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidad", is("Cardiologia Intervencionista")));
    }

    @Test
    @DisplayName("DELETE /api/doctores/{id} sin citas asociadas elimina y responde 200 (bug: deberia ser 204)")
    void eliminar_conIdExistenteSinCitas_debeEliminarYResponder200() throws Exception {
        // Se crea un doctor nuevo (sin citas asociadas) porque los 4 doctores precargados
        // en data.sql SI tienen citas -> eliminarlos viola la FK (ver test siguiente).
        DoctorDTO dto = new DoctorDTO();
        dto.setNombre("Temporal");
        dto.setApellido("SinCitas");
        dto.setEspecialidad("Medicina General");
        dto.setEmail("temporal@hospital.com");

        String respuesta = mockMvc.perform(post("/api/doctores")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andReturn().getResponse().getContentAsString();
        Long idCreado = objectMapper.readTree(respuesta).get("id").asLong();

        mockMvc.perform(delete("/api/doctores/" + idCreado))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/doctores/" + idCreado))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("DELETE /api/doctores/{id} con citas asociadas viola la FK y responde 500 (bug real, no solo comentado)")
    void eliminar_conCitasAsociadas_fallaPorViolacionDeIntegridadReferencial() throws Exception {
        // BUG REAL confirmado en integracion: DoctorService.eliminar() no verifica citas
        // asociadas antes de borrar (como indica el comentario "BUG INTENCIONAL" en el codigo).
        // Los 4 doctores de data.sql tienen al menos una cita (fk_citas_doctor), asi que
        // eliminar cualquiera de ellos deja una violacion de integridad referencial pendiente
        // que Hibernate reporta en el siguiente flush -> HTTP 500 con stack trace filtrado
        // (agrava BUG documentado de information leakage en GlobalExceptionHandler).
        mockMvc.perform(delete("/api/doctores/3"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/doctores"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.stackTrace", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/doctores/buscar-especialidad con un valor normal filtra correctamente")
    void buscarPorEspecialidad_conValorNormal_debeFiltrarCorrectamente() throws Exception {
        mockMvc.perform(get("/api/doctores/buscar-especialidad").param("q", "Cardiologia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre", is("Elena")));
    }

    @Test
    @DisplayName("GET /api/doctores/buscar-especialidad es vulnerable a SQL Injection real (PoC end-to-end)")
    void buscarPorEspecialidad_conPayloadSqlInjection_bypasseaElFiltro() throws Exception {
        // Payload clasico de bypass: cierra la comilla del LIKE, agrega una condicion
        // siempre verdadera y comenta el resto de la query con "--".
        // Query resultante en el servidor:
        //   SELECT * FROM doctores WHERE especialidad ILIKE '%' OR '1'='1' -- %'
        String payload = "' OR '1'='1' -- ";

        mockMvc.perform(get("/api/doctores/buscar-especialidad").param("q", payload))
                .andExpect(status().isOk())
                // Si la query estuviera parametrizada correctamente, este payload no deberia
                // coincidir con ninguna especialidad real y la lista vendria vacia.
                // Al ser vulnerable, la condicion "OR '1'='1'" hace que se devuelvan TODOS los
                // doctores, confirmando la inyeccion SQL (OWASP A03:2021 - Injection).
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("GET /api/doctores/buscar-nombre filtra por nombre y apellido exactos")
    void buscarPorNombreCompleto_conCoincidenciaExacta_debeRetornarResultado() throws Exception {
        mockMvc.perform(get("/api/doctores/buscar-nombre")
                        .param("nombre", "Elena")
                        .param("apellido", "Rodriguez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
