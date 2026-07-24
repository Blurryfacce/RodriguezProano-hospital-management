# Hallazgos — Paso 5: E2E Doctores (Playwright)

## Resultado
`e2e/doctores.spec.js`: 3/3 tests pasando de forma consistente (re-ejecutado dos veces seguidas para descartar flakiness).

## BUG CRÍTICO: mensaje de ÉXITO falso al eliminar un doctor con citas asociadas

Este es el hallazgo más grave detectado en todo el proyecto hasta ahora, porque combina dos bugs individuales en una interacción que **oculta activamente un fallo real al usuario**:

1. Los 4 doctores precargados (`data.sql`) tienen citas asociadas. `DoctorService.eliminar()` no lo verifica antes de borrar.
2. Verificado contra el backend real: `DELETE /api/doctores/1` responde **HTTP 500 inmediatamente** (viola `fk_citas_doctor` al hacer commit de la transacción del request) y **el doctor NO se elimina**.
3. A diferencia de un DELETE exitoso (que responde 200 con body **vacío**), este 500 **sí trae un body JSON** (el objeto de error que arma `GlobalExceptionHandler`, incluyendo el stack trace filtrado — otro bug ya documentado).
4. Como `apiFetch` nunca revisa `response.ok`, y esta vez `response.json()` **sí logra parsear** algo (el objeto de error), la promesa de `DoctoresAPI.eliminar()` **no se rechaza**.
5. `DoctoresModule.eliminarDoctor()` interpreta la ausencia de excepción como éxito y muestra **"Doctor eliminado exitosamente"**.

**Resultado observable:** el usuario ve un mensaje de éxito claro y sin ambigüedad, pero el doctor sigue existiendo en el sistema sin que nada se lo indique. Se confirmó recargando la sección: Elena Rodríguez sigue en la tabla pese al mensaje de "eliminado exitosamente" (capturas `doctores-06` a `doctores-08`).

En un contexto hospitalario real esto es particularmente serio: un administrador podría creer que retiró a un doctor del sistema (p. ej. tras una baja) y seguir asignándole citas sin saberlo, confiando en un mensaje que es simplemente falso.

## Patrón opuesto, mismo origen: mensaje de ERROR falso al eliminar sin dependencias

Para un doctor **sin** citas asociadas, el `DELETE` sí tiene éxito (200, body vacío), pero entonces `response.json()` falla al no haber nada que parsear, y el frontend muestra **"Error al eliminar doctor"** pese a que el borrado sí se completó (mismo patrón ya documentado para Pacientes en `05-paso5-e2e-pacientes.md`).

**Conclusión unificada:** el bug de fondo es uno solo — `apiFetch` no distingue entre "hubo un error HTTP" y "el body no se pudo parsear como JSON", y tampoco expone `response.ok` al código que lo llama. Dependiendo de si el body de la respuesta viene vacío o con contenido, el resultado visible para el usuario es un **falso negativo** (dice que falló cuando funcionó) o un **falso positivo** (dice que funcionó cuando falló) — el peor de los dos posibles en un sistema de gestión hospitalaria.

## Bug adicional descubierto durante la escritura de estas pruebas: condición de carrera en `showAlert`

Al encadenar dos acciones que muestran alertas seguidas (p. ej. editar y luego eliminar) en menos de 4 segundos, la prueba fallaba de forma intermitente viendo el contenedor de alertas **vacío** en vez del mensaje esperado. Investigado: `showAlert()` programa `setTimeout(() => { container.innerHTML = ''; }, 4000)` **sin cancelar el timeout de una alerta anterior**. Si la segunda alerta se muestra antes de que expire el timer de la primera, ambos timers quedan activos; el de la primera alerta puede disparar y **borrar el contenido de la segunda alerta antes de que el usuario (o la prueba) alcance a verla**.

No es un bug documentado en los comentarios del código. Se agregó un helper (`esperarQueAlertaSeLimpie` en `e2e/helpers.js`) que espera a que cada ciclo de alerta termine antes de la siguiente acción, tanto para estabilizar las pruebas como para dejar registrado el hallazgo.

## SQL Injection confirmada desde la UI real
Escribir `' OR '1'='1' -- ` en el buscador de "Doctores" (que llama a `DoctoresAPI.buscarPorEspecialidad`, el endpoint marcado como vulnerable) hace que la tabla muestre los 4 doctores en vez de 0 — bypass confirmado operando la interfaz real, no solo vía `curl`/tests de integración (ver Paso 3).

## Comando para reproducir
```
cd frontend
npm run test:e2e -- doctores.spec.js
```
