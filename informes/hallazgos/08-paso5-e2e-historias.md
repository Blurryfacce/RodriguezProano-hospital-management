# Hallazgos — Paso 5: E2E Historias Clínicas (Playwright)

## Resultado
`e2e/historias.spec.js`: 3/3 tests pasando de forma consistente (ejecutado dos veces seguidas, y una tercera vez junto con el resto de la suite E2E — 12/12 en total).

## Mismo BUG-01 que Citas, con un giro irónico

`HistoriaClinica` tiene **dos** relaciones `@ManyToOne` LAZY (`paciente` y `doctor`), así que sufre el mismo patrón que `Cita` (ver `07-paso5-e2e-citas.md`): la tabla nunca se puede listar vía UI (0 filas, sin siquiera el placeholder "sin registros"), aunque **crear** una historia sí funciona (el servicio carga `paciente`/`doctor` explícitamente antes de guardar).

**Hallazgo interesante no anticipado:** el diagnóstico permite HTML/scripts sin sanitizar — confirmado ya en Paso 3 directamente contra la API (`HistoriaClinicaControllerIntegrationTest`), donde se probó que el backend persiste y devuelve el payload tal cual. Sin embargo, **al intentar demostrar la ejecución del XSS navegando la UI real, resulta imposible**: como la tabla de historias nunca se llena (BUG-01), no existen filas ni botones "Ver" que hacer clic para disparar `HistoriasModule.verHistoria()` (la función que efectivamente hace `innerHTML = detalle` con el diagnóstico sin escapar). Es decir: **BUG-01 termina "blindando" accidentalmente a los usuarios de la UI contra el XSS del módulo de Historias**, simplemente porque un bug no relacionado impide llegar al punto donde el otro se dispara.

Esto no significa que la vulnerabilidad no sea real ni explotable — sigue estando ahí:
- Se confirma con evidencia directa contra la API (Paso 3) que el payload se almacena y se devuelve sin escapar.
- Si se corrige BUG-01 (agregando `jackson-datatype-hibernate6` o cambiando a DTOs) sin corregir también la sanitización del diagnóstico, el XSS se volvería inmediatamente explotable vía la UI, porque el botón "Ver" empezaría a funcionar.
- Un atacante que use la API directamente (no la UI) podría explotar el `POST` para almacenar el payload de todas formas, afectando a cualquier otro cliente (actual o futuro) que sí logre leer y renderizar esos datos.

Se documenta este hallazgo explícitamente porque es un buen ejemplo de por qué **la ausencia de síntomas visibles no prueba la ausencia de vulnerabilidad** — un punto relevante para el informe OWASP (Paso 7): dos bugs independientes pueden enmascararse mutuamente sin que eso signifique que alguno de los dos esté "arreglado".

El test verifica explícitamente que `window.__xssEjecutado` (que el payload intenta setear via `onerror`) permanece `false` tras la creación, confirmando que en el estado actual de la app el payload NO llega a ejecutarse por esta vía — no porque esté mitigado, sino porque BUG-01 impide alcanzar el punto de renderizado.

## Comando para reproducir
```
cd frontend
npm run test:e2e -- historias.spec.js
npm run test:e2e            # suite completa (12 tests)
npm run test:e2e:report     # reporte HTML con capturas de los 4 flujos
```
