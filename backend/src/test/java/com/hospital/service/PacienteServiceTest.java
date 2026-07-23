package com.hospital.service;

import com.hospital.dto.PacienteDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Paciente;
import com.hospital.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PacienteService")
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PacienteService pacienteService;

    private Paciente paciente;
    private PacienteDTO pacienteDTO;

    @BeforeEach
    void setUp() {
        paciente = new Paciente("Juan", "Perez", LocalDate.of(1990, 5, 10),
                "juan.perez@email.com", "0991234567", "Av. Amazonas N45-123");
        paciente.setId(1L);

        pacienteDTO = new PacienteDTO();
        pacienteDTO.setNombre("Juan");
        pacienteDTO.setApellido("Perez");
        pacienteDTO.setFechaNacimiento(LocalDate.of(1990, 5, 10));
        pacienteDTO.setEmail("juan.perez@email.com");
        pacienteDTO.setTelefono("0991234567");
        pacienteDTO.setDireccion("Av. Amazonas N45-123");
        pacienteDTO.setActivo(true);
    }

    @Nested
    @DisplayName("Casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodos retorna todos los pacientes del repositorio")
        void listarTodos_debeRetornarListaDePacientes() {
            Paciente otro = new Paciente("Maria", "Garcia", LocalDate.of(1985, 1, 1),
                    "maria@email.com", "0987654321", "Calle Loja");
            otro.setId(2L);
            when(pacienteRepository.findAll()).thenReturn(Arrays.asList(paciente, otro));

            List<Paciente> resultado = pacienteService.listarTodos();

            assertThat(resultado).hasSize(2).containsExactly(paciente, otro);
            verify(pacienteRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("buscarPorId con ID existente retorna el paciente")
        void buscarPorId_conIdExistente_debeRetornarPaciente() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

            Paciente resultado = pacienteService.buscarPorId(1L);

            assertThat(resultado).isEqualTo(paciente);
            assertThat(resultado.getNombre()).isEqualTo("Juan");
        }

        @Test
        @DisplayName("crear guarda y retorna el paciente mapeado desde el DTO")
        void crear_conDatosValidos_debeGuardarYRetornarPaciente() {
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> {
                Paciente p = invocation.getArgument(0);
                p.setId(10L);
                return p;
            });

            Paciente resultado = pacienteService.crear(pacienteDTO);

            assertThat(resultado.getId()).isEqualTo(10L);
            assertThat(resultado.getNombre()).isEqualTo("Juan");
            assertThat(resultado.getEmail()).isEqualTo("juan.perez@email.com");
            assertThat(resultado.getActivo()).isTrue();
            verify(pacienteRepository).save(any(Paciente.class));
        }

        @Test
        @DisplayName("actualizar con ID existente sobreescribe los campos y guarda")
        void actualizar_conIdExistente_debeActualizarYRetornarPaciente() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(pacienteRepository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

            PacienteDTO cambios = new PacienteDTO();
            cambios.setNombre("Juan Carlos");
            cambios.setApellido("Perez Lopez");
            cambios.setFechaNacimiento(LocalDate.of(1990, 5, 10));
            cambios.setEmail("nuevo@email.com");
            cambios.setTelefono("0999999999");
            cambios.setDireccion("Nueva direccion");
            cambios.setActivo(false);

            Paciente resultado = pacienteService.actualizar(1L, cambios);

            assertThat(resultado.getNombre()).isEqualTo("Juan Carlos");
            assertThat(resultado.getEmail()).isEqualTo("nuevo@email.com");
            assertThat(resultado.getActivo()).isFalse();
            verify(pacienteRepository).save(paciente);
        }

        @Test
        @DisplayName("eliminar con ID existente elimina el paciente")
        void eliminar_conIdExistente_debeEliminarPaciente() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

            pacienteService.eliminar(1L);

            verify(pacienteRepository).delete(paciente);
        }
    }

    @Nested
    @DisplayName("Casos limite (boundary)")
    class CasosLimite {

        @Test
        @DisplayName("calcularEdadPromedio con lista vacia produce division por cero (bug documentado)")
        void calcularEdadPromedio_conListaVacia_documentaDivisionPorCero() {
            when(pacienteRepository.findAll()).thenReturn(Collections.emptyList());

            double resultado = pacienteService.calcularEdadPromedio();

            // BUG INTENCIONAL en el servicio: 0.0 / 0 produce NaN en vez de manejar el caso vacio.
            assertThat(resultado).isNaN();
        }

        @Test
        @DisplayName("calcularEdadPromedio ignora pacientes sin fecha de nacimiento en la suma pero los cuenta en el divisor")
        void calcularEdadPromedio_conFechaNacimientoNula_noSumaPeroSiCuenta() {
            Paciente sinFecha = new Paciente("Sin", "Fecha", null, "x@x.com", "0999999999", "dir");
            sinFecha.setId(3L);
            when(pacienteRepository.findAll()).thenReturn(Arrays.asList(paciente, sinFecha));

            double resultado = pacienteService.calcularEdadPromedio();

            // La edad de "paciente" se divide entre 2 (total de la lista), no entre 1 (con fecha valida).
            int edadEsperada = LocalDate.of(1990, 5, 10).until(LocalDate.now()).getYears();
            assertThat(resultado).isEqualTo(edadEsperada / 2.0);
        }

        @Test
        @DisplayName("buscarPorEmail con email null delega en el repositorio sin validar (bug documentado)")
        void buscarPorEmail_conEmailNull_delegaSinValidar() {
            when(pacienteRepository.findByEmail(null)).thenReturn(null);

            Paciente resultado = pacienteService.buscarPorEmail(null);

            assertThat(resultado).isNull();
            verify(pacienteRepository).findByEmail(null);
        }
    }

    @Nested
    @DisplayName("Manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("buscarPorId con ID inexistente lanza ResourceNotFoundException")
        void buscarPorId_conIdInexistente_debeLanzarExcepcion() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pacienteService.buscarPorId(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("actualizar con ID inexistente lanza ResourceNotFoundException y no guarda")
        void actualizar_conIdInexistente_debeLanzarExcepcion() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pacienteService.actualizar(99L, pacienteDTO))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(pacienteRepository, never()).save(any(Paciente.class));
        }

        @Test
        @DisplayName("eliminar con ID inexistente lanza ResourceNotFoundException y no elimina")
        void eliminar_conIdInexistente_debeLanzarExcepcion() {
            when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pacienteService.eliminar(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(pacienteRepository, never()).delete(any(Paciente.class));
        }
    }
}
