package com.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.PacienteDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integracion Controller -> Service -> Repository -> H2 en memoria.
 * Documentan el comportamiento REAL de la API, incluyendo los bugs intencionales
 * del codigo base (ver informes/hallazgos/): 200 en vez de 201/204, y
 * ResourceNotFoundException devuelta como HTTP 200 en vez de 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PacienteController - integracion")
class PacienteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/pacientes retorna los 5 pacientes precargados por data.sql")
    void listar_debeRetornarPacientesPrecargados() throws Exception {
        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].nombre", is("Juan")))
                .andExpect(jsonPath("$[0].apellido", is("Perez")));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} con ID existente retorna el paciente")
    void buscar_conIdExistente_debeRetornarPaciente() throws Exception {
        mockMvc.perform(get("/api/pacientes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.nombre", is("Juan")))
                .andExpect(jsonPath("$.email", is("juan.perez@email.com")));
    }

    @Test
    @DisplayName("GET /api/pacientes/{id} con ID inexistente responde 200 en vez de 404 (bug documentado)")
    void buscar_conIdInexistente_documentaBugDeStatus200() throws Exception {
        // BUG INTENCIONAL en GlobalExceptionHandler: ResourceNotFoundException se mapea a HTTP 200.
        mockMvc.perform(get("/api/pacientes/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Recurso no encontrado")))
                .andExpect(jsonPath("$.message", containsString("9999")));
    }

    @Test
    @DisplayName("POST /api/pacientes con datos validos crea el paciente y responde 200 (bug: deberia ser 201)")
    void crear_conDatosValidos_debeCrearPacienteYResponder200() throws Exception {
        PacienteDTO dto = new PacienteDTO();
        dto.setNombre("Lucia");
        dto.setApellido("Fernandez");
        dto.setFechaNacimiento(LocalDate.of(1995, 4, 12));
        dto.setEmail("lucia.fernandez@email.com");
        dto.setTelefono("0987001122");
        dto.setDireccion("Calle Nueva 100");

        mockMvc.perform(post("/api/pacientes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                // BUG INTENCIONAL: el controller retorna 200 OK en vez de 201 Created.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nombre", is("Lucia")))
                .andExpect(jsonPath("$.activo", is(true)));

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    @DisplayName("POST /api/pacientes sin nombre (obligatorio) responde 400 con errores de validacion")
    void crear_sinNombre_debeResponder400ConErrores() throws Exception {
        PacienteDTO dto = new PacienteDTO();
        dto.setApellido("SinNombre");

        mockMvc.perform(post("/api/pacientes")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors.nombre", is("El nombre es obligatorio")))
                // BUG INTENCIONAL: se filtra el mensaje interno de la excepcion en el campo "debug".
                .andExpect(jsonPath("$.debug", notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/pacientes/{id} con ID existente actualiza los datos")
    void actualizar_conIdExistente_debeActualizarPaciente() throws Exception {
        PacienteDTO dto = new PacienteDTO();
        dto.setNombre("Juan Actualizado");
        dto.setApellido("Perez");
        dto.setFechaNacimiento(LocalDate.of(1985, 3, 15));
        dto.setEmail("juan.actualizado@email.com");
        dto.setTelefono("0991234567");
        dto.setDireccion("Nueva direccion 456");
        dto.setActivo(true);

        mockMvc.perform(put("/api/pacientes/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Juan Actualizado")))
                .andExpect(jsonPath("$.email", is("juan.actualizado@email.com")));
    }

    @Test
    @DisplayName("DELETE /api/pacientes/{id} con ID existente elimina y responde 200 (bug: deberia ser 204)")
    void eliminar_conIdExistente_debeEliminarYResponder200() throws Exception {
        // BUG INTENCIONAL: el controller retorna 200 OK en vez de 204 No Content.
        mockMvc.perform(delete("/api/pacientes/5"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("GET /api/pacientes/buscar filtra por nombre parcial respetando mayusculas/minusculas")
    void buscarPorNombre_conCoincidenciaParcial_debeRetornarResultados() throws Exception {
        mockMvc.perform(get("/api/pacientes/buscar").param("nombre", "Jua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre", is("Juan")));
    }

    @Test
    @DisplayName("GET /api/pacientes/buscar en minusculas no encuentra 'Juan' (LIKE case-sensitive, bug de UX documentado)")
    void buscarPorNombre_conMinusculas_noEncuentraCoincidencias() throws Exception {
        // BUG: buscarPorNombre usa LIKE (case-sensitive) en vez de ILIKE o *ContainingIgnoreCase,
        // a diferencia de DoctorService.buscarPorEspecialidad que si es case-insensitive.
        // Una busqueda de "jua" no encuentra a "Juan" pese a ser una coincidencia razonable para el usuario.
        mockMvc.perform(get("/api/pacientes/buscar").param("nombre", "jua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/pacientes/estadisticas/edad-promedio responde 200 con un numero")
    void edadPromedio_debeResponderConUnNumero() throws Exception {
        mockMvc.perform(get("/api/pacientes/estadisticas/edad-promedio"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(emptyOrNullString())));
    }
}
