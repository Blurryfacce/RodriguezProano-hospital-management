# Hallazgos — Capa de servicio (backend)

Lectura de `PacienteService`, `DoctorService`, `CitaService`, `HistoriaClinicaService` y sus DTOs/repositorios antes de escribir pruebas unitarias (Paso 2). El código ya trae comentarios `// BUG INTENCIONAL` del profesor — se listan aquí agrupados para referencia rápida al armar los informes de análisis estático y OWASP.

## PacienteService
- `buscarPorId`: no valida IDs negativos o nulos antes de consultar (delega todo a `ResourceNotFoundException` si no existe — comportamiento observable, no un crash).
- `crear`: no valida si el email ya existe → permite pacientes duplicados por email.
- `actualizar`: sobreescribe todos los campos sin chequear nulls individuales (excepto `activo`) — un DTO parcial puede borrar datos existentes (nombre/apellido/email/telefono/direccion) si vienen `null` desde el cliente.
- `eliminar`: borrado físico (`repository.delete`), sin verificar si el paciente tiene citas/historias asociadas → **riesgo de integridad referencial**, agravado por BUG-03 del PDF (tabla `citas` sin FK a `pacientes`).
- `buscarPorNombre`: si `nombre` es `null`, el query nativo (`buscarPorNombre` en el repository, con concatenación `'%' || :nombre || '%'`) puede lanzar excepción — a verificar con test de límite. Además, aunque usa `:nombre` parametrizado (no hay SQL injection aquí, el bug real de SQLi está en `DoctorService`), es una query nativa innecesaria cuando `findByApellidoContainingIgnoreCase` ya existe como alternativa segura vía Spring Data.
- `buscarPorEmail`: no valida `null`/vacío; `repository.findByEmail` devuelve un único `Paciente` (no `Optional`), por lo que si hay 0 resultados devuelve `null` sin lanzar `ResourceNotFoundException` — inconsistente con `buscarPorId`.
- `calcularEdadPromedio`: **división por cero** si `pacientes` está vacío (`0.0/0 = NaN` en Java double, no una excepción, pero sigue siendo un resultado inválido para el Dashboard/HU-05).

## DoctorService
- `eliminar`: `deleteById` sin verificar citas activas asociadas al doctor.
- `buscarPorEspecialidadInsegura`: **inyección SQL real** — concatena el parámetro directamente en una query nativa (`"...ILIKE '%" + especialidad + "%'"`). Existe un método hermano seguro (`buscarPorEspecialidad`) que usa Spring Data derivado. Este hallazgo es candidato principal para el informe OWASP (A03:2021 – Injection). **No está expuesto en el Controller actual** (revisar en Paso 7 si el endpoint lo llama o queda como método "dead code" alcanzable solo internamente — de cualquier forma cuenta como vulnerabilidad de código).
- `buscarPorNombreCompleto`: no valida parámetros vacíos; delega en `findByNombreAndApellido` (coincidencia exacta, no parcial — el nombre del método puede confundir a quien lo use esperando un "nombre completo" libre).

## CitaService
- Clase completa sin `@Transactional` en métodos que escriben (`crear`, `actualizar`, `eliminar`) → si algo falla a mitad de una operación de escritura no hay rollback garantizado.
- `crear`: no valida que `pacienteId` exista realmente como paciente (coherente con la ausencia de FK en la tabla `citas`) — se puede crear una cita con un `pacienteId` inexistente. Tampoco valida doble booking (mismo doctor + misma hora), contradiciendo el criterio de aceptación de HU-03.
- `listarPorDoctor` / `listarPorEstado`: problema N+1 documentado en comentarios — cada `Cita` carga su `Doctor` (LAZY) por separado al serializar.
- Sin método para detectar conflictos de horario (doble booking) — carencia funcional completa, no solo un bug puntual.
- **Relacionado con BUG-01 (Paso 1):** `listarTodas()`/`listarPorX()` devuelven la entidad `Cita` con `doctor` LAZY directamente; esto es lo que probablemente dispara el 500 al serializar vía Jackson en el endpoint real. Los tests unitarios de este Paso 2 no lo van a reproducir (se mockea el repository), pero si se replica en Paso 3 (MockMvc+H2) quedará documentado ahí.

## HistoriaClinicaService
- `listarTodas`: sin paginación (`findAllByOrderByFechaCreacionDesc` trae todo) — riesgo de OOM con datasets grandes.
- `crear`: no sanitiza `diagnostico`/`tratamiento`/`observaciones` — permite HTML/script embebido → **XSS almacenado** si el frontend lo renderiza sin escapar (a confirmar en Paso 7 revisando `historias.js`).
- Igual que Cita, `paciente`/`doctor` son relaciones LAZY que se serializan en el Controller directamente sobre la entidad — mismo patrón que BUG-02.

## DTOs — inconsistencias de validación (Jakarta Validation)
| DTO | Campo | Problema |
|---|---|---|
| `PacienteDTO` | `apellido` | Sin `@NotBlank` (solo `nombre` lo tiene) — contradice HU-01 ("nombre, apellido" obligatorios) |
| `PacienteDTO` | `fechaNacimiento` | `@Past` sin límite inferior — acepta fechas absurdas (ej. año 1800) |
| `DoctorDTO` | `especialidad` | Sin `@NotBlank` pese a ser "requerida" según HU-02 (confirma la nota del propio PDF) |
| `CitaDTO` | `fechaHora` | `@Future` en vez de `@FutureOrPresent` — impide agendar una cita para el instante actual exacto |

## Repositorios — nota adicional
- `PacienteRepository.buscarPorNombre`: aunque parametrizada (`:nombre`), es una query nativa redundante — se documentará como hallazgo de calidad (no de seguridad) en el análisis estático.
- `PacienteRepository.findByEmail`: devuelve `Paciente` (no `Optional<Paciente>`), fuente de `null` no controlado en el service.

Estos hallazgos alimentarán directamente los informes de Análisis Estático (Paso 6) y OWASP (Paso 7). Las pruebas unitarias del Paso 2 se diseñan para **documentar el comportamiento real** de estos métodos (incluyendo los bugs), no para corregirlos.
