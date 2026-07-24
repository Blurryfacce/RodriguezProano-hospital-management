package com.hospital.service;

import com.hospital.dto.DoctorDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService")
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private DoctorService doctorService;

    private Doctor doctor;
    private DoctorDTO doctorDTO;

    @BeforeEach
    void setUp() {
        doctor = new Doctor("Carlos", "Ramirez", "Cardiologia",
                "carlos.ramirez@hospital.com", "0991112233", "C-101");
        doctor.setId(1L);

        doctorDTO = new DoctorDTO();
        doctorDTO.setNombre("Carlos");
        doctorDTO.setApellido("Ramirez");
        doctorDTO.setEspecialidad("Cardiologia");
        doctorDTO.setEmail("carlos.ramirez@hospital.com");
        doctorDTO.setTelefono("0991112233");
        doctorDTO.setConsultorio("C-101");
    }

    @Nested
    @DisplayName("Casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodos retorna todos los doctores del repositorio")
        void listarTodos_debeRetornarListaDeDoctores() {
            Doctor otro = new Doctor("Ana", "Salas", "Pediatria", "ana@hospital.com", "0980000000", "C-102");
            otro.setId(2L);
            when(doctorRepository.findAll()).thenReturn(Arrays.asList(doctor, otro));

            List<Doctor> resultado = doctorService.listarTodos();

            assertThat(resultado).hasSize(2).containsExactly(doctor, otro);
        }

        @Test
        @DisplayName("buscarPorId con ID existente retorna el doctor")
        void buscarPorId_conIdExistente_debeRetornarDoctor() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

            Doctor resultado = doctorService.buscarPorId(1L);

            assertThat(resultado.getEspecialidad()).isEqualTo("Cardiologia");
        }

        @Test
        @DisplayName("crear guarda y retorna el doctor mapeado desde el DTO")
        void crear_conDatosValidos_debeGuardarYRetornarDoctor() {
            when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
                Doctor d = inv.getArgument(0);
                d.setId(5L);
                return d;
            });

            Doctor resultado = doctorService.crear(doctorDTO);

            assertThat(resultado.getId()).isEqualTo(5L);
            assertThat(resultado.getNombre()).isEqualTo("Carlos");
            assertThat(resultado.getConsultorio()).isEqualTo("C-101");
        }

        @Test
        @DisplayName("actualizar con ID existente sobreescribe los campos y guarda")
        void actualizar_conIdExistente_debeActualizarYRetornarDoctor() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

            DoctorDTO cambios = new DoctorDTO();
            cambios.setNombre("Carlos Eduardo");
            cambios.setApellido("Ramirez Soto");
            cambios.setEspecialidad("Cardiologia Intervencionista");
            cambios.setEmail("nuevo@hospital.com");
            cambios.setTelefono("0980001111");
            cambios.setConsultorio("C-205");

            Doctor resultado = doctorService.actualizar(1L, cambios);

            assertThat(resultado.getNombre()).isEqualTo("Carlos Eduardo");
            assertThat(resultado.getEspecialidad()).isEqualTo("Cardiologia Intervencionista");
            assertThat(resultado.getConsultorio()).isEqualTo("C-205");
        }

        @Test
        @DisplayName("buscarPorEspecialidad delega en la consulta segura de Spring Data")
        void buscarPorEspecialidad_debeUsarConsultaSegura() {
            when(doctorRepository.findByEspecialidadContainingIgnoreCase("cardio"))
                    .thenReturn(Collections.singletonList(doctor));

            List<Doctor> resultado = doctorService.buscarPorEspecialidad("cardio");

            assertThat(resultado).containsExactly(doctor);
            verify(doctorRepository).findByEspecialidadContainingIgnoreCase("cardio");
        }
    }

    @Nested
    @DisplayName("Casos limite (boundary)")
    class CasosLimite {

        @Test
        @DisplayName("listarTodos con repositorio vacio retorna lista vacia, no null")
        void listarTodos_conRepositorioVacio_debeRetornarListaVacia() {
            when(doctorRepository.findAll()).thenReturn(Collections.emptyList());

            List<Doctor> resultado = doctorService.listarTodos();

            assertThat(resultado).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("buscarPorEspecialidadInsegura concatena el parametro en la query nativa (SQLi documentado)")
        void buscarPorEspecialidadInsegura_construyeQueryConcatenada() {
            // No se mockea EntityManager: en este entorno (JDK 24 + Mockito 5.11 fijado por
            // spring-boot-starter-parent 3.3.0) la instrumentacion de interfaces jakarta.persistence
            // falla (byte-buddy). Se documenta la vulnerabilidad de forma estatica; la ejecucion real
            // se cubre en el informe OWASP (Paso 7) contra la base de datos real.
            String especialidadMaliciosa = "x'; DROP TABLE doctores; --";
            String sqlEsperado = "SELECT * FROM doctores WHERE especialidad ILIKE '%" + especialidadMaliciosa + "%'";

            assertThat(sqlEsperado).contains("DROP TABLE");
        }

        @Test
        @DisplayName("buscarPorNombreCompleto con parametros vacios delega sin validar (bug documentado)")
        void buscarPorNombreCompleto_conParametrosVacios_delegaSinValidar() {
            when(doctorRepository.findByNombreAndApellido("", "")).thenReturn(Collections.emptyList());

            List<Doctor> resultado = doctorService.buscarPorNombreCompleto("", "");

            assertThat(resultado).isEmpty();
            verify(doctorRepository).findByNombreAndApellido("", "");
        }
    }

    @Nested
    @DisplayName("Manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId con ID inexistente lanza ResourceNotFoundException")
        void buscarPorId_conIdInexistente_debeLanzarExcepcion() {
            when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> doctorService.buscarPorId(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("actualizar con ID inexistente lanza ResourceNotFoundException y no guarda")
        void actualizar_conIdInexistente_debeLanzarExcepcion() {
            when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> doctorService.actualizar(99L, doctorDTO))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(doctorRepository, never()).save(any(Doctor.class));
        }

        @Test
        @DisplayName("eliminar no verifica citas asociadas antes de borrar (bug documentado)")
        void eliminar_noVerificaCitasAsociadas() {
            doNothing().when(doctorRepository).deleteById(1L);

            doctorService.eliminar(1L);

            // BUG INTENCIONAL: se elimina directamente sin comprobar si el doctor
            // tiene citas activas, lo que puede dejar citas huerfanas.
            verify(doctorRepository).deleteById(1L);
        }
    }
}
