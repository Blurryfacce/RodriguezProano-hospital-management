# Hallazgos — Paso 1: Verificación de entorno

Fecha: 2026-07-23

## Estado del entorno

| Componente | Estado |
|---|---|
| PostgreSQL 16 (Docker, `hospital-db`) | OK, healthy |
| Backend Spring Boot (`:8080`) | OK, corriendo |
| Frontend estático (`:3000`) | OK, corriendo |

## Incidencias de entorno (no relacionadas con el código del proyecto)

1. Volumen Docker `hospital-management_pgdata` preexistente con credenciales distintas a las de `docker-compose.yml` → causaba `FATAL: la autentificación password falló para el usuario admin`. Se resolvió recreando el volumen (`docker compose down -v && up -d`).
2. Conflicto de puerto 5432 entre un PostgreSQL 17 nativo de Windows (servicio `postgresql-x64-17`) y el contenedor Docker del proyecto. Se detuvo el servicio nativo temporalmente para evitar el conflicto.

Ninguna de las dos incidencias es un bug del proyecto; son particularidades de la máquina de desarrollo.

## Bugs reales encontrados en el código base (candidatos a "bug no listado" — FAQ del proyecto)

### BUG-01: `GET /api/citas` devuelve 500 Internal Server Error
- **Endpoint:** `GET /api/citas`
- **Evidencia:** Respuesta HTTP 500 con cuerpo `{"status":500,"error":"Error interno del servidor","stackTrace":[...]}`.
- **Traza:** El primer frame corresponde a `AbstractJackson2HttpMessageConverter.writeInternal`, es decir, el fallo ocurre serializando la respuesta JSON, no en la consulta a base de datos (los `SELECT` de Hibernate en el log se completan correctamente).
- **Hipótesis:** `CitaController.listar()` devuelve la entidad JPA `Cita` directamente (no un DTO). `Cita.doctor` es `@ManyToOne(fetch = FetchType.LAZY)`. Con `spring.jpa.open-in-view=true` (valor por defecto, sin configurar explícitamente — Spring emite warning `HHH... open-in-view is enabled by default`), la sesión de Hibernate debería seguir abierta durante el renderizado de la vista, pero algo en la cadena de serialización falla igualmente. Pendiente de causa raíz exacta (a profundizar en el informe OWASP / análisis estático).
- **Impacto funcional:** El módulo de Citas es completamente inutilizable vía API — bloquea HU-03 y el conteo de "citas del día" en el Dashboard (HU-05).
- **No se modifica el código fuente** — se documentará como hallazgo y se diseñarán pruebas que constaten el comportamiento real.

### BUG-02: `GET /api/historias-clinicas` devuelve 500 Internal Server Error
- Mismo patrón que BUG-01. El log muestra múltiples repeticiones de `SELECT ... FROM historias_clinicas ... ORDER BY fecha_creacion DESC` intercaladas con `SELECT ... FROM pacientes WHERE id=?`, sugiriendo N+1 al resolver relaciones lazy durante la serialización, con fallo final en Jackson.
- **Impacto funcional:** Bloquea HU-04 vía API.

### BUG-03: Ausencia total de logging de errores server-side (posible OWASP A09:2021 — Security Logging and Monitoring Failures)
- Las excepciones capturadas por `GlobalExceptionHandler.handleGeneral()` **no se loguean** en el servidor (no aparece ninguna línea `ERROR` en consola/log para las peticiones que fallan con 500). Solo se sabe que ocurrió un error porque el stack trace se filtra al cliente en el JSON de respuesta.
- Esto agrava BUG-01/BUG-02: en producción, sin acceso al log de la app, sería casi imposible diagnosticar el problema porque no queda rastro server-side.

## Bugs intencionales ya señalados en comentarios del código (`// BUG INTENCIONAL`)

Confirmados leyendo el código fuente (no modificado):

| Archivo | Línea aprox. | Descripción | Categoría OWASP tentativa |
|---|---|---|---|
| `GlobalExceptionHandler.java` | 25 | `ResourceNotFoundException` devuelve HTTP 200 en vez de 404 | A04:2021 Insecure Design |
| `GlobalExceptionHandler.java` | 41 | Errores de validación exponen el mensaje completo de la excepción en el campo `debug` | A05:2021 Security Misconfiguration |
| `GlobalExceptionHandler.java` | 54 | Errores 500 exponen el stack trace completo en la respuesta JSON | A05:2021 Security Misconfiguration |
| `Doctor.java` | 20-21 | `especialidad` sin `@Column(nullable = false)` ni validación Jakarta | Deuda técnica / integridad de datos |
| `Cita.java` | 14-17 | `pacienteId` es un `Long` simple, sin `@ManyToOne`/FK real hacia `Paciente` — permite IDs de pacientes inexistentes | A08:2021 Software and Data Integrity Failures |

## Datos precargados verificados

| Endpoint | Esperado | Resultado |
|---|---|---|
| `GET /api/pacientes` | 5 registros | ✅ 5 registros, HTTP 200 |
| `GET /api/doctores` | 4 registros | ✅ 4 registros, HTTP 200 |
| `GET /api/citas` | 5 registros | ❌ HTTP 500 (BUG-01) |
| `GET /api/historias-clinicas` | 3 registros | ❌ HTTP 500 (BUG-02) |

## Nota sobre fechas de datos semilla

Las citas precargadas en `data.sql` tienen fechas en junio de 2026 (ej. `2026-06-20`), anteriores a la fecha actual del entorno (2026-07-23). Esto es inconsistente con el criterio de aceptación de HU-03 ("la fecha y hora deben ser futuras"), aunque al ser datos semilla fijos no bloquea la validación de la API en sí — se anotará como observación menor.
