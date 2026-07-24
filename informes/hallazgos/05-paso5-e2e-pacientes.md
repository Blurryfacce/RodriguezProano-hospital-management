# Hallazgos — Paso 5: E2E Pacientes (Playwright)

## Resultado
`e2e/pacientes.spec.js`: 2/2 tests pasando, contra el stack real (Postgres en Docker + backend Spring Boot + frontend estatico), con `webServer` de Playwright levantando automáticamente frontend/backend si no están corriendo (`reuseExistingServer: true` para reusar lo que ya esté activo) y un `globalSetup` que garantiza Docker/Postgres arriba antes de todo.

## BUG REAL descubierto en este flujo: mensaje de error engañoso al eliminar

Al hacer clic en "Eliminar" sobre un paciente:
1. El backend procesa el `DELETE /api/pacientes/{id}` y responde **200 OK con body vacío** (`ResponseEntity.ok().build()`).
2. `apiFetch` (ver `api.js`) intenta `await response.json()` **incluso cuando el body está vacío** — esto es un bug ya comentado genéricamente en el código (`// BUG INTENCIONAL: ... Para DELETE, intenta parsear JSON aunque el body este vacio`), pero aquí se comprueba su **consecuencia real y visible para el usuario**.
3. `response.json()` lanza `SyntaxError: Unexpected end of JSON input` al no haber contenido que parsear.
4. Esa excepción se propaga hasta `PacientesModule.eliminarPaciente`, cuyo `catch` muestra **`"Error al eliminar paciente"`** — un mensaje de error para una operación que en realidad **sí tuvo éxito** en el backend.
5. Se confirmó navegando fuera y volviendo a la sección Pacientes (fuerza un nuevo `GET`): el paciente **ya no está** en la tabla, es decir, el borrado se ejecutó correctamente pese al mensaje de error.

**Impacto:** un usuario real vería "Error al eliminar paciente" y probablemente reintentaría la acción, sin saber que ya se completó. Esto aplica a **cualquier operación DELETE** de la aplicación (Doctores, Citas también usan el mismo `apiFetch`), así que se documentará de forma consolidada en el informe OWASP/análisis estático (Paso 6-7) como un problema de fiabilidad de la capa de comunicación HTTP, no solo de Pacientes.

## Otros hallazgos confirmados visualmente
- El frontend **no pide confirmación** antes de eliminar (se verificó que no aparece ningún `dialog` nativo).
- La búsqueda por nombre es **sensible a mayúsculas/minúsculas** ("Juan" encuentra resultados, "juan" no) — consistente con el hallazgo de Paso 3 sobre `LIKE` vs `ILIKE` en el backend.

## Nota metodológica
Al escribir este test se investigaron dos falsos "bugs" que en realidad eran errores de la propia prueba, documentados aquí para evitar repetir el análisis:
- Un primer intento asumía que la tabla queda con **0 filas** (`<tr>`) cuando una búsqueda no encuentra resultados. En realidad `renderTabla()` siempre inserta una fila placeholder ("No hay pacientes registrados"), así que el conteo de `<tr>` nunca baja a 0 — hay que verificar el **texto**, no la cantidad de filas.
- Los primeros intentos de la prueba de creación fallaban de forma intermitente al leer el `#alert-container` inmediatamente después del click; el mensaje de éxito se mostró correctamente una vez se investigó con logging de red/consola del navegador — no era un bug de la app, sino falta de tiempo de asentamiento en el primer diagnóstico manual.

## Comando para reproducir
```
cd frontend
npm run test:e2e -- pacientes.spec.js
npm run test:e2e:report   # abre el reporte HTML con capturas
```
