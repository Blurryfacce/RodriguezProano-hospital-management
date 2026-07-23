package com.hospital.service;

import com.hospital.dto.CitaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Cita;
import com.hospital.model.Doctor;
import com.hospital.repository.CitaRepository;
import com.hospital.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CitaService")
class CitaServiceTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private CitaService citaService;

    private Doctor doctor;
    private Cita cita;
    private CitaDTO citaDTO;

    @BeforeEach
    void setUp() {
        doctor = new Doctor("Carlos", "Ramirez", "Cardiologia", "carlos@hospital.com", "0991112233", "C-101");
        doctor.setId(1L);

        cita = new Cita(2L, doctor, LocalDateTime.now().plusDays(1), "Control", "PROGRAMADA");
        cita.setId(10L);

        citaDTO = new CitaDTO();
        citaDTO.setPacienteId(2L);
        citaDTO.setDoctorId(1L);
        citaDTO.setFechaHora(LocalDateTime.now().plusDays(1));
        citaDTO.setMotivo("Control");
        citaDTO.setEstado("PROGRAMADA");
    }

    @Nested
    @DisplayName("Casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodas retorna todas las citas del repositorio")
        void listarTodas_debeRetornarListaDeCitas() {
            when(citaRepository.findAll()).thenReturn(Collections.singletonList(cita));

            List<Cita> resultado = citaService.listarTodas();

            assertThat(resultado).containsExactly(cita);
        }

        @Test
        @DisplayName("buscarPorId con ID existente retorna la cita")
        void buscarPorId_conIdExistente_debeRetornarCita() {
            when(citaRepository.findById(10L)).thenReturn(Optional.of(cita));

            Cita resultado = citaService.buscarPorId(10L);

            assertThat(resultado.getMotivo()).isEqualTo("Control");
        }

        @Test
        @DisplayName("crear con doctor existente guarda la cita asociada")
        void crear_conDoctorExistente_debeGuardarYRetornarCita() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> {
                Cita c = inv.getArgument(0);
                c.setId(20L);
                return c;
            });

            Cita resultado = citaService.crear(citaDTO);

            assertThat(resultado.getId()).isEqualTo(20L);
            assertThat(resultado.getDoctor()).isEqualTo(doctor);
            assertThat(resultado.getPacienteId()).isEqualTo(2L);
            assertThat(resultado.getEstado()).isEqualTo("PROGRAMADA");
        }

        @Test
        @DisplayName("actualizar con ID existente modifica fecha, motivo y estado")
        void actualizar_conIdExistente_debeActualizarYRetornarCita() {
            when(citaRepository.findById(10L)).thenReturn(Optional.of(cita));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            CitaDTO cambios = new CitaDTO();
            LocalDateTime nuevaFecha = LocalDateTime.now().plusDays(5);
            cambios.setFechaHora(nuevaFecha);
            cambios.setMotivo("Seguimiento");
            cambios.setEstado("COMPLETADA");

            Cita resultado = citaService.actualizar(10L, cambios);

            assertThat(resultado.getFechaHora()).isEqualTo(nuevaFecha);
            assertThat(resultado.getMotivo()).isEqualTo("Seguimiento");
            assertThat(resultado.getEstado()).isEqualTo("COMPLETADA");
        }

        @Test
        @DisplayName("listarPorPaciente retorna las citas asociadas al pacienteId")
        void listarPorPaciente_debeRetornarCitasDelPaciente() {
            when(citaRepository.findByPacienteId(2L)).thenReturn(Collections.singletonList(cita));

            List<Cita> resultado = citaService.listarPorPaciente(2L);

            assertThat(resultado).containsExactly(cita);
        }
    }

    @Nested
    @DisplayName("Casos limite (boundary)")
    class CasosLimite {

        @Test
        @DisplayName("crear sin estado en el DTO usa PROGRAMADA por defecto")
        void crear_sinEstadoEnDTO_debeUsarEstadoPorDefecto() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            citaDTO.setEstado(null);

            Cita resultado = citaService.crear(citaDTO);

            assertThat(resultado.getEstado()).isEqualTo("PROGRAMADA");
        }

        @Test
        @DisplayName("crear no valida que el pacienteId exista realmente (bug documentado, sin FK en BD)")
        void crear_conPacienteIdInexistente_noLoValida() {
            when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
            when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

            citaDTO.setPacienteId(999999L);

            Cita resultado = citaService.crear(citaDTO);

            // BUG INTENCIONAL: no existe verificacion contra PacienteRepository ni FK en la tabla citas.
            assertThat(resultado.getPacienteId()).isEqualTo(999999L);
        }

        @Test
        @DisplayName("listarPorRangoFechas con inicio posterior a fin no valida el orden (bug documentado)")
        void listarPorRangoFechas_conInicioMayorQueFin_delegaSinValidar() {
            LocalDateTime inicio = LocalDateTime.now().plusDays(10);
            LocalDateTime fin = LocalDateTime.now();
            when(citaRepository.findByFechaHoraBetween(inicio, fin)).thenReturn(Collections.emptyList());

            List<Cita> resultado = citaService.listarPorRangoFechas(inicio, fin);

            assertThat(resultado).isEmpty();
            verify(citaRepository).findByFechaHoraBetween(inicio, fin);
        }
    }

    @Nested
    @DisplayName("Manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("crear con doctor inexistente lanza ResourceNotFoundException y no guarda la cita")
        void crear_conDoctorInexistente_debeLanzarExcepcion() {
            when(doctorRepository.findById(99L)).thenReturn(Optional.empty());
            citaDTO.setDoctorId(99L);

            assertThatThrownBy(() -> citaService.crear(citaDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Doctor no encontrado");

            verify(citaRepository, never()).save(any(Cita.class));
        }

        @Test
        @DisplayName("buscarPorId con ID inexistente lanza ResourceNotFoundException")
        void buscarPorId_conIdInexistente_debeLanzarExcepcion() {
            when(citaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> citaService.buscarPorId(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("actualizar con ID inexistente lanza ResourceNotFoundException y no guarda")
        void actualizar_conIdInexistente_debeLanzarExcepcion() {
            when(citaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> citaService.actualizar(999L, citaDTO))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(citaRepository, never()).save(any(Cita.class));
        }
    }
}
