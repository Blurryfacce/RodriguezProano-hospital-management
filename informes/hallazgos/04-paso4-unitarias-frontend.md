# Hallazgos — Paso 4: Pruebas unitarias frontend (Jest)

## Resultado

| Archivo de prueba | Tests | Cobertura (stmts/branch/funcs/lines) |
|---|---|---|
| `utils.test.js` | 29 | 100% / 100% / 100% / 100% |
| `api.test.js` | 30 | 100% / 100% / 100% / 100% |
| **Total** | **59** | **100% en ambos archivos** |

## Reto técnico: probar scripts "vanilla" sin `module.exports`

`utils.js` y `api.js` estan escritos para cargarse via `<script src="...">` en el navegador: declaran `function`/`const` a nivel global, sin ningun `module.exports`/`export`. Jest (Node) no puede `require()` este tipo de archivo directamente porque no expone nada.

**Solución (sin modificar los archivos fuente):** se creó `js/__tests__/helpers/loadScript.js`, que:
1. Lee el archivo fuente como texto.
2. Lo ejecuta dentro de un sandbox aislado (`vm.createContext` + `vm.Script.runInContext`), inyectando ahí los globals que cada script necesita (`document`, `fetch`, `setTimeout`, segun corresponda).
3. Expone las funciones/objetos declarados (`function`/`const` de nivel superior) como propiedades de un objeto que el test puede desestructurar.

Esto permite probar el comportamiento real de los archivos sin tocarlos, cumpliendo la restricción de no modificar el código base.

## Reto técnico #2: cobertura real con código ejecutado en `vm`

Jest normalmente no puede medir cobertura de código que corre dentro de un `vm.createContext` aislado, porque su instrumentación (Istanbul, via `babel-jest`) solo se aplica a módulos cargados con `require`/`import`. La primera versión de `loadScript.js` daba **0% de cobertura** pese a que las 49 pruebas pasaban.

**Solución:** se instrumenta el código manualmente con `babel-plugin-istanbul` (usando la ruta absoluta del archivo real como `filename`, para que el reporte final lo atribuya a `js/utils.js`/`js/api.js` y no a un archivo temporal) antes de ejecutarlo en el sandbox. Los contadores de cobertura instrumentados quedan en el objeto de contexto del sandbox (`context.__coverage__`), y se fusionan manualmente con `global.__coverage__` (que es lo que Jest lee al finalizar cada archivo de test).

**Bug propio detectado y corregido durante la implementación:** la primera versión de la fusión ocurría inmediatamente después de cargar el script (dentro de `loadFrontendScript`), es decir, *antes* de que el test llamara a las funciones. Como cada `beforeEach`/llamada crea un sandbox nuevo, el merge capturaba una foto en cero y las llamadas posteriores mutaban un objeto que ya no estaba conectado a `global.__coverage__`. Se corrigió registrando todos los contextos creados y haciendo la fusión una sola vez en un `afterAll` (cuando ya se ejecutaron todas las llamadas de todos los tests del archivo). Esto llevó la cobertura real de ~9-26% a 100%.

## Hallazgos de comportamiento (más allá de los ya comentados en el código)

- **`validateEmail`**: el comentario `// BUG INTENCIONAL: regex incorrecta... acepta emails sin TLD como "usuario@dominio" y rechaza emails validos con +` **no se corresponde con el comportamiento real** de la regex. Verificado empíricamente: `"usuario@dominio"` es rechazado (correcto) y `"user+tag@example.com"` es aceptado (correcto, `+` está en la clase de caracteres permitida). El comentario del código es engañoso. El defecto real y verificable de esta regex es otro: acepta dominios con puntos consecutivos inválidos (`"user@domain..com"`) y TLDs de un solo carácter (`"a@b.c"`).
- **`PacientesAPI.buscarPorNombre`**: el comentario dice que "no sanitiza el parámetro (XSS reflejado)", pero la función sí aplica `encodeURIComponent` antes de insertar el valor en la URL, lo cual impide que `<`, `>`, `"` lleguen crudos. Si existe riesgo real de XSS reflejado en el módulo de búsqueda de pacientes, estaría en el punto donde el resultado se renderiza en el DOM (`pacientes.js`), no en `api.js`. Se documenta esta precisión para el informe OWASP (Paso 7), donde se revisará `pacientes.js`.
- **Bug nuevo no documentado en comentarios — `apiFetch` pierde `Content-Type` al recibir headers personalizados**: el objeto `config` se construye como `{ headers: { 'Content-Type': ..., ...options.headers }, ...options }`. Como `options` también trae su propia clave `headers`, el segundo spread (`...options`) sobreescribe por completo el objeto `headers` ya fusionado. Cualquier llamada que pase headers personalizados pierde `Content-Type: application/json` en vez de combinarlo. Ninguna de las funciones de `PacientesAPI`/`DoctoresAPI`/etc. pasa headers personalizados actualmente, así que no se manifiesta en el uso actual, pero es una trampa para futuras extensiones.

## Comando para reproducir

```
cd frontend
npm test              # ejecuta la suite
npm run test:coverage # ejecuta con reporte de cobertura (texto + HTML en frontend/coverage/)
```
