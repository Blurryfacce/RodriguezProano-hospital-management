# Hallazgos — Paso 5: E2E Citas (Playwright)

## Resultado
`e2e/citas.spec.js`: 4/4 tests pasando de forma consistente (ejecutado dos veces seguidas).

## Impacto real de BUG-01 (Paso 1/3) confirmado visualmente en el navegador

Estas pruebas muestran, con capturas de pantalla, la consecuencia práctica del bug de serialización (`Cita.doctor` LAZY sin `jackson-datatype-hibernate6`) para un usuario real del sistema:

1. **Dashboard**: `stat-total-pacientes` y `stat-total-doctores` muestran los valores correctos (5 y 4), pero `stat-citas-hoy` y `stat-edad-promedio` quedan permanentemente en `—`. Causa: `cargarDashboard()` hace `citas.filter(...)` sobre el objeto de error que retorna `/api/citas` (500), lo cual lanza una excepción que aborta el resto de la función antes de llegar a calcular esas dos estadísticas — pese a que `edadPromedio()` no depende de citas para nada, queda colateralmente afectada por el orden del código.
2. **Tabla de Citas**: al navegar a la sección, la tabla queda **completamente vacía, sin ningún mensaje** — ni siquiera el placeholder "No hay citas registradas", porque `renderTabla()` truena en `citas.map(...)` antes de llegar a esa rama condicional.
3. **Crear una cita SÍ funciona**: el formulario envía el `POST` correctamente (el backend carga el `Doctor` de forma explícita antes de guardar, así que no es un proxy LAZY sin resolver — ver Paso 3) y el usuario ve "Cita creada exitosamente". Pero la recarga automática de la tabla (`cargarCitas()` al final de `guardarCita()`) vuelve a fallar con el mismo 500, así que **la tabla sigue vacía después de crear una cita exitosamente**. El usuario no tiene ninguna forma, dentro de la UI, de confirmar que su cita quedó registrada.
4. **Doble booking confirmado sin restricción**: se crearon dos citas para el mismo doctor a la misma fecha/hora exacta sin ningún rechazo, confirmando en el navegador real la carencia ya señalada en el código (`// BUG INTENCIONAL: No hay metodo para verificar conflictos de horario`).

## Reto de test descubierto: falta de "señal de listo" para módulos con listado roto

Al escribir la prueba de creación, el clic en "+ Nueva Cita" a veces no abría el modal. Investigado: `CitasModule.init()` ejecuta `Promise.all([cargarCitas(), cargarDoctores(), cargarPacientes()])` y solo **después** llama a `setupListeners()` (que conecta el botón). En Pacientes/Doctores, esperar a que aparezca una fila en la tabla da, de paso, tiempo suficiente para que `setupListeners()` ya se haya ejecutado. En Citas, como la tabla **nunca** se llena (BUG-01), no había ninguna señal natural que esperar, así que el test podía intentar el clic antes de que el listener estuviera conectado. Se agregó `esperarModuloListo()` en `e2e/helpers.js` (espera a `networkidle`) para cubrir este caso — útil también para Historias Clínicas, que tiene el mismo patrón.

## Comando para reproducir
```
cd frontend
npm run test:e2e -- citas.spec.js
```
