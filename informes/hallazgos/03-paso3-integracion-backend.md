# Hallazgos — Paso 3: Pruebas de integración backend (MockMvc + H2)

## Resultado

| Clase de prueba | Tests |
|---|---|
| `PacienteControllerIntegrationTest` | 10 |
| `DoctorControllerIntegrationTest` | 13 |
| `CitaControllerIntegrationTest` | 12 |
| `HistoriaClinicaControllerIntegrationTest` | 10 |
| **Total** | **45** |

Suite completa (unitarias + integración): **92/92 en verde**.

Todas usan `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` + H2 en memoria (perfil `test`, `application-test.properties`), con `MODE=PostgreSQL` para reutilizar `schema.sql`/`data.sql` sin duplicarlos. Cada clase queda envuelta en `@Transactional` para rollback automático entre tests.

## BUG-01/BUG-02 (ver Paso 1) — causa raíz confirmada

En el Paso 1 se detectó que `GET /api/citas` y `GET /api/historias-clinicas` devuelven 500 contra el backend real (PostgreSQL). Aquí se confirma con precisión:

- **El bug se reproduce IDÉNTICO contra H2** → no es un problema del driver/versión de PostgreSQL, es un bug real de la aplicación.
- **Causa raíz exacta** (capturada con un test de diagnóstico que serializa directamente con el `ObjectMapper` de la app):

  ```
  com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
  No serializer found for class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
  and no properties discovered to create BeanSerializer
  (through reference chain: ArrayList[0]->Cita["doctor"]->Doctor$HibernateProxy[...]["hibernateLazyInitializer"])
  ```

  Falta el módulo `jackson-datatype-hibernate6` (o equivalente) en el `ObjectMapper` de Spring Boot. Sin él, Jackson no sabe "desenvolver" un proxy LAZY de Hibernate y en vez de la entidad real intenta serializar los campos internos del proxy (`hibernateLazyInitializer`), lo cual falla.

- **Por qué `Paciente` y `Doctor` SÍ funcionan bien**: esas entidades no tienen ninguna relación `@ManyToOne`/`@OneToMany` — son "planas". Solo `Cita` (relación `doctor` LAZY) e `HistoriaClinica` (relaciones `paciente` y `doctor` LAZY) tienen el problema.

- **El bug es intermitente, no constante**, y esto es clave para entenderlo: si el `Doctor`/`Paciente` referenciado ya fue cargado como entidad completa (no proxy) en algún punto anterior de la misma transacción/sesión de Hibernate — por ejemplo, `CitaService.crear()` llama a `doctorRepository.findById()` antes de guardar—, Hibernate reutiliza esa instancia ya resuelta en vez de crear un proxy nuevo, y Jackson SÍ puede serializarla sin problema. Confirmado empíricamente:
  - `POST /api/citas` → 200 OK (el doctor se cargó explícitamente antes de guardar).
  - `POST /api/historias-clinicas` → 200 OK (paciente y doctor se cargan explícitamente antes de guardar).
  - `GET /api/citas`, `GET /api/citas/{id}`, `GET /api/citas/paciente/{id}`, `GET /api/citas/estado/{estado}`, `PUT /api/citas/{id}` → **500** (el doctor llega como proxy sin resolver).
  - `GET /api/historias-clinicas`, `GET /api/historias-clinicas/{id}`, `GET /api/historias-clinicas/paciente/{id}`, `GET /api/historias-clinicas/doctor/{id}` → **500**.
  - Curiosamente `GET /api/citas/doctor/{id}` respondió 200 en el test de doble booking porque el doctor ya había sido cargado momentos antes al crear las citas del propio test — refuerza que el bug depende del estado de la sesión de Hibernate, no del endpoint en sí.

**Impacto:** los módulos de Citas e Historias Clínicas están, en la práctica, casi completamente inutilizables vía API para lectura (HU-03, HU-04, HU-05 no se pueden cumplir). Esto es material central para el informe de análisis estático (Paso 6) y se relaciona con OWASP A05:2021 (Security Misconfiguration) en cuanto a que el error, además de romper la funcionalidad, expone un stack trace completo al cliente (bug ya documentado en el código, `GlobalExceptionHandler`).

## Otros hallazgos nuevos confirmados en integración real

### Inyección SQL demostrada end-to-end
`GET /api/doctores/buscar-especialidad?q=' OR '1'='1' -- ` devuelve **todos** los doctores (4) en vez de 0, confirmando que `DoctorService.buscarPorEspecialidadInsegura` es explotable de verdad a través del endpoint público `/api/doctores/buscar-especialidad`, no solo un método interno teórico. Prioridad alta para el informe OWASP (A03:2021 - Injection).

### Eliminar un doctor con citas asociadas rompe la integridad referencial
Los 4 doctores precargados en `data.sql` tienen al menos una cita asociada (`fk_citas_doctor`). `DoctorService.eliminar()` no verifica esto antes de borrar. El `DELETE` en sí responde 200 (el borrado se difiere hasta el siguiente flush de Hibernate), pero la siguiente consulta a la base de datos dentro de la misma transacción falla con una violación de integridad referencial, expuesta como HTTP 500 con stack trace. Confirma y amplía el bug ya comentado en el código ("no verifica si el doctor tiene citas activas antes de eliminar").

### Búsqueda de pacientes por nombre es case-sensitive
`GET /api/pacientes/buscar?nombre=jua` no encuentra a "Juan" porque `PacienteRepository.buscarPorNombre` usa `LIKE` (case-sensitive) en vez de `ILIKE` o una consulta `ContainingIgnoreCase` como sí tiene `DoctorService.buscarPorEspecialidad`. No es un bug comentado en el código, es un hallazgo nuevo de UX/consistencia detectado al escribir las pruebas.

### `CitaDTO` exige `pacienteId`/`doctorId` incluso para `PUT` (actualizar), aunque `CitaService.actualizar()` los ignora
`@NotNull` en `CitaDTO.pacienteId`/`doctorId` aplica también en `PUT /api/citas/{id}`, obligando al cliente a reenviar esos campos aunque el servicio no los usa para actualizar. Inconsistencia de diseño menor, documentada como hallazgo de calidad.

## Comando para reproducir

```
cd backend
mvn test
```
