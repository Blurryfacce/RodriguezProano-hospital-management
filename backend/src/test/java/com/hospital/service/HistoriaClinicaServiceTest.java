package com.hospital.service;

import com.hospital.dto.HistoriaClinicaDTO;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.model.Doctor;
import com.hospital.model.HistoriaClinica;
import com.hospital.model.Paciente;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.HistoriaClinicaRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoriaClinicaService")
class HistoriaClinicaServiceTest {

    @Mock
    private HistoriaClinicaRepository historiaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private HistoriaClinicaService historiaClinicaService;

    private Paciente paciente;
    private Doctor doctor;
    private HistoriaClinica historia;
    private HistoriaClinicaDTO historiaDTO;

    @BeforeEach
    void setUp() {
        paciente = new Paciente("Juan", "Perez", LocalDate.of(1990, 5, 10),
                "juan@email.com", "0991234567", "Direccion 123");
        paciente.setId(1L);

        doctor = new Doctor("Carlos", "Ramirez", "Cardiologia", "carlos@hospital.com", "0991112233", "C-101");
        doctor.setId(2L);

        historia = new HistoriaClinica(paciente, doctor, "Hipertension", "Losartan 50mg", "Control mensual");
        historia.setId(100L);

        historiaDTO = new HistoriaClinicaDTO();
        historiaDTO.setPacienteId(1L);
        historiaDTO.setDoctorId(2L);
        historiaDTO.setDiagnostico("Hipertension");
        historiaDTO.setTratamiento("Losartan 50mg");
        historiaDTO.setObservaciones("Control mensual");
    }

    @Nested
    @DisplayName("Casos felices")
    class CasosFelices {

        @Test
        @DisplayName("listarTodas retorna las historias ordenadas por fecha descendente")
        void listarTodas_debeRetornarListaOrdenada() {
            when(historiaRepository.findAllByOrderByFechaCreacionDesc())
                    .thenReturn(Collections.singletonList(historia));

            List<HistoriaClinica> resultado = historiaClinicaService.listarTodas();

            assertThat(resultado).containsExactly(historia);
        }

        @Test
        @DisplayName("buscarPorId con ID existente retorna la historia clinica")
        void buscarPorId_conIdExistente_debeRetornarHistoria() {
            when(historiaRepository.findById(100L)).thenReturn(Optional.of(historia));

            HistoriaClinica resultado = historiaClinicaService.buscarPorId(100L);

            assertThat(resultado.getDiagnostico()).isEqualTo("Hipertension");
        }

        @Test
        @DisplayName("crear con paciente y doctor existentes guarda la historia completa")
        void crear_conPacienteYDoctorExistentes_debeGuardarYRetornarHistoria() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(doctorRepository.findById(2L)).thenReturn(Optional.of(doctor));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> {
                HistoriaClinica h = inv.getArgument(0);
                h.setId(101L);
                return h;
            });

            HistoriaClinica resultado = historiaClinicaService.crear(historiaDTO);

            assertThat(resultado.getId()).isEqualTo(101L);
            assertThat(resultado.getPaciente()).isEqualTo(paciente);
            assertThat(resultado.getDoctor()).isEqualTo(doctor);
            assertThat(resultado.getDiagnostico()).isEqualTo("Hipertension");
        }

        @Test
        @DisplayName("crear sin doctorId (opcional) guarda la historia con doctor null")
        void crear_sinDoctor_debeGuardarConDoctorNull() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> inv.getArgument(0));

            historiaDTO.setDoctorId(null);

            HistoriaClinica resultado = historiaClinicaService.crear(historiaDTO);

            assertThat(resultado.getDoctor()).isNull();
            assertThat(resultado.getPaciente()).isEqualTo(paciente);
            verify(doctorRepository, never()).findById(any());
        }

        @Test
        @DisplayName("listarPorPaciente retorna las historias asociadas al pacienteId")
        void listarPorPaciente_debeRetornarHistoriasDelPaciente() {
            when(historiaRepository.findByPacienteId(1L)).thenReturn(Collections.singletonList(historia));

            List<HistoriaClinica> resultado = historiaClinicaService.listarPorPaciente(1L);

            assertThat(resultado).containsExactly(historia);
        }
    }

    @Nested
    @DisplayName("Casos limite (boundary)")
    class CasosLimite {

        @Test
        @DisplayName("crear no sanitiza contenido HTML/script en el diagnostico (XSS documentado)")
        void crear_conDiagnosticoConScript_noSanitiza() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(doctorRepository.findById(2L)).thenReturn(Optional.of(doctor));
            when(historiaRepository.save(any(HistoriaClinica.class))).thenAnswer(inv -> inv.getArgument(0));

            String diagnosticoMalicioso = "<script>alert('xss')</script>";
            historiaDTO.setDiagnostico(diagnosticoMalicioso);

            HistoriaClinica resultado = historiaClinicaService.crear(historiaDTO);

            // BUG INTENCIONAL: el servicio persiste el contenido tal cual, sin sanitizar.
            assertThat(resultado.getDiagnostico()).isEqualTo(diagnosticoMalicioso);
        }

        @Test
        @DisplayName("listarPorDoctor con doctor sin historias retorna lista vacia")
        void listarPorDoctor_sinHistorias_debeRetornarListaVacia() {
            when(historiaRepository.findByDoctorId(999L)).thenReturn(Collections.emptyList());

            List<HistoriaClinica> resultado = historiaClinicaService.listarPorDoctor(999L);

            assertThat(resultado).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Manejo de errores")
    class ManejoDeErrores {

        @Test
        @DisplayName("crear con paciente inexistente lanza ResourceNotFoundException y no guarda")
        void crear_conPacienteInexistente_debeLanzarExcepcion() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> historiaClinicaService.crear(historiaDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Paciente no encontrado");

            verify(historiaRepository, never()).save(any(HistoriaClinica.class));
        }

        @Test
        @DisplayName("crear con doctor inexistente lanza ResourceNotFoundException y no guarda")
        void crear_conDoctorInexistente_debeLanzarExcepcion() {
            when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
            when(doctorRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> historiaClinicaService.crear(historiaDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Doctor no encontrado");

            verify(historiaRepository, never()).save(any(HistoriaClinica.class));
        }

        @Test
        @DisplayName("buscarPorId con ID inexistente lanza ResourceNotFoundException")
        void buscarPorId_conIdInexistente_debeLanzarExcepcion() {
            when(historiaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> historiaClinicaService.buscarPorId(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}
