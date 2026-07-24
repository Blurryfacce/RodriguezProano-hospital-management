# Análisis Estático de Código — Hospital Management System

Proyecto de Validación y Verificación de Software — EPN 2026A

## 1. Herramientas utilizadas y configuración

| Herramienta | Alcance | Configuración |
|---|---|---|
| **Checkstyle** (`maven-checkstyle-plugin` 3.6.0) | Backend (Java) | Regla `google_checks.xml` (Google Java Style), integrada en `backend/pom.xml`. Ejecutar: `mvn checkstyle:checkstyle` |
| **SpotBugs + FindSecBugs** (`spotbugs-maven-plugin` 4.9.3.0 + `findsecbugs-plugin` 1.13.0) | Backend (bytecode compilado) | Esfuerzo `Max`, umbral `Low`, integrado en `backend/pom.xml`. Ejecutar: `mvn compile spotbugs:spotbugs` |
| **ESLint** 8.57 | Frontend (JavaScript) | `eslint:recommended` + globals declaradas manualmente (arquitectura sin bundler, ver hallazgo 5). Config: `frontend/.eslintrc.json`. Ejecutar: `npx eslint "js/**/*.js"` |

Se usaron 3 herramientas cubriendo backend (calidad + seguridad de bytecode) y frontend, sin necesidad de levantar SonarQube (que requeriría su propio servidor Docker) dado el alcance del proyecto.

## 2. Hallazgos por severidad

| Herramienta | Hallazgos | Clasificación |
|---|---|---|
| Checkstyle | **930** | Todos `warning` (estilo Google Java) — equivalentes a **Minor** |
| SpotBugs + FindSecBugs | **42** | 30 `SECURITY` (prioridad 3 = **Major**), 12 `MALICIOUS_CODE`/`EI_EXPOSE_REP*` (prioridad 2 = **Critical**) |
| ESLint | **16** | Todos `warning` (`no-unused-vars`) — **Minor** |
| **Total** | **988** | |

Desglose Checkstyle por regla:

| Regla | Cantidad |
|---|---|
| `IndentationCheck` | 548 |
| `LeftCurlyCheck` / `RightCurlyAlone` | 112 + 112 |
| `EmptyLineSeparatorCheck` | 56 |
| `CustomImportOrderCheck` | 31 |
| `MissingJavadocMethodCheck` | 23 |
| `MissingJavadocTypeCheck` | 23 |
| `LineLengthCheck` | 13 |
| `AvoidStarImportCheck` | 8 |
| `AbbreviationAsWordInNameCheck` | 4 |

Desglose SpotBugs/FindSecBugs por tipo:

| Tipo | Cantidad | Categoría |
|---|---|---|
| `SPRING_ENDPOINT` (FindSecBugs) | 29 | SECURITY (informativo — marca puntos de entrada para taint analysis) |
| `EI_EXPOSE_REP2` | 7 | MALICIOUS_CODE (Critical) |
| `EI_EXPOSE_REP` | 4 | MALICIOUS_CODE (Critical) |

## 3. Análisis en profundidad de 5 hallazgos relevantes

### Hallazgo 1 — SpotBugs no detectó automáticamente la inyección SQL ya confirmada manualmente
**Herramienta:** SpotBugs + FindSecBugs · **Severidad real:** Alta (aunque la herramienta no lo marcó)

`DoctorService.buscarPorEspecialidadInsegura()` concatena directamente el parámetro del usuario en una query nativa (`"...ILIKE '%" + especialidad + "%'"`) — vulnerabilidad de inyección SQL confirmada de forma manual y automatizada en los Pasos 3 y 5 (PoC end-to-end vía `curl` y Playwright: el payload `' OR '1'='1' -- ` bypasea el filtro y devuelve todos los registros). Sin embargo, **FindSecBugs no generó ningún `SQL_INJECTION_JPA` para este método**.

**Causa investigada:** durante la ejecución de SpotBugs aparece la advertencia `The following classes needed for analysis were missing: makeConcatWithConstants`. Desde Java 9, el compilador usa `invokedynamic` + `StringConcatFactory` para la concatenación de strings (`+`) en vez de `StringBuilder.append()` explícito, que es el patrón bytecode que los detectores clásicos de taint-tracking de SpotBugs/FindSecBugs saben reconocer. Al compilar con JDK 17+, el flujo de datos "parámetro de usuario → SQL nativo" queda ofuscado detrás de esa invocación dinámica y la herramienta no logra rastrearlo.

**Conclusión:** el análisis estático automatizado **no es suficiente por sí solo** para esta vulnerabilidad concreta en proyectos compilados con JDK moderno; hace falta complementarlo con revisión manual de código y pruebas de penetración dirigidas (como se hizo en los Pasos 3 y 5). Es un hallazgo metodológico tan importante como uno de código.

### Hallazgo 2 — Exposición de representación interna mutable (`EI_EXPOSE_REP` / `EI_EXPOSE_REP2`)
**Herramienta:** SpotBugs · **Severidad:** Critical (prioridad 2) · **Ubicación:** `Cita.getDoctor()`/`setDoctor()`, `HistoriaClinica.getDoctor()`/`getPaciente()`/`setDoctor()`/`setPaciente()`

Los getters devuelven directamente la referencia a la entidad `Doctor`/`Paciente` interna, y los setters almacenan directamente la referencia recibida, sin copiar. Cualquier código externo con acceso a esa referencia puede mutar el estado interno del objeto sin pasar por los métodos de la clase. En un contexto JPA esto es un patrón común y de bajo riesgo real (las entidades no suelen tratarse como inmutables), pero SpotBugs lo marca correctamente como una violación del principio de encapsulación — vale la pena registrarlo como deuda técnica menor, no como vulnerabilidad explotable.

### Hallazgo 3 — Inconsistencia masiva de formato de código (`IndentationCheck`, 548 ocurrencias)
**Herramienta:** Checkstyle · **Severidad:** Minor (pero con alto volumen)

Más de la mitad de todos los hallazgos de Checkstyle corresponden a indentación inconsistente respecto al estándar Google Java Style. Esto no afecta la ejecución del programa, pero es un fuerte indicador de que el proyecto nunca tuvo un formateador automático (`google-java-format`, EditorConfig, etc.) integrado al flujo de trabajo. Sumado a los 112 casos de `LeftCurlyCheck`/`RightCurlyAlone` (llaves `{`/`}` en posiciones inconsistentes), sugiere que el código fue escrito/editado por múltiples fuentes sin una convención de estilo forzada — coherente con ser un proyecto "base" preparado para que estudiantes lo analicen.

**Recomendación:** integrar un formateador automático (`google-java-format` o el plugin de Checkstyle en modo `fail`) como *pre-commit hook* o paso de CI, no como ejercicio manual.

### Hallazgo 4 — Ausencia total de Javadoc en la API pública (46 ocurrencias)
**Herramienta:** Checkstyle (`MissingJavadocMethodCheck` + `MissingJavadocTypeCheck`) · **Severidad:** Minor/Major según contexto

Ningún método público de `service`/`controller`/`repository` (ejemplo: `PacienteService.java:33`, `:45`) tiene comentario Javadoc. Para un sistema de gestión hospitalaria — dominio con reglas de negocio no triviales (ej. qué constituye una "cita en conflicto", cuándo es válido un teléfono ecuatoriano) — la falta de documentación en la capa de servicio incrementa el riesgo de que futuros desarrolladores reintroduzcan bugs ya corregidos por no entender el "por qué" de una validación.

### Hallazgo 5 — Arquitectura sin módulos hace que ESLint no pueda verificar el uso real de las funciones (16 falsos positivos `no-unused-vars`)
**Herramienta:** ESLint · **Severidad:** Minor, pero revela un riesgo estructural

Los 16 warnings de `no-unused-vars` (p. ej. `PacientesModule` en `pacientes.js:11`, `formatDate` en `utils.js:16`) son **falsos positivos**: esas funciones/objetos sí se usan, pero desde *otros* archivos cargados como `<script>` planos sin ningún sistema de módulos (`import`/`export`) ni bundler. ESLint solo puede analizar un archivo a la vez y no tiene forma de saber que `app.js` depende de un global definido en `utils.js`.

**Esto no es solo un problema de "ruido" en el linter — es un riesgo real de mantenibilidad:** si alguien renombra `formatDate` en `utils.js`, **ninguna herramienta automática detectará las referencias rotas en `pacientes.js`, `citas.js`, etc.** hasta que un usuario real dispare ese código en el navegador y falle en producción. Un proyecto de este tamaño se beneficiaría de adoptar ES Modules (`<script type="module">` + `import`/`export`) precisamente para que ESLint (y el propio motor de JavaScript) puedan verificar estáticamente estas dependencias.

## 4. Comandos para reproducir

```bash
# Backend
cd backend
mvn checkstyle:checkstyle          # genera target/checkstyle-result.xml
mvn compile spotbugs:spotbugs      # genera target/spotbugsXml.xml
mvn spotbugs:gui                   # (opcional) visor grafico de SpotBugs

# Frontend
cd frontend
npx eslint "js/**/*.js"                        # salida en consola
npx eslint "js/**/*.js" --format json -o eslint-report.json
```

## 5. Nota sobre el formato de entrega

Este informe se generó en Markdown para mantener el ritmo de trabajo del proyecto (todas las herramientas de conversión a PDF disponibles requerían instalación adicional). Se recomienda exportarlo a PDF antes de la entrega final (Word, la extensión "Markdown PDF" de VS Code, o imprimir a PDF desde un visor Markdown) y añadir capturas de pantalla de la consola de Checkstyle/SpotBugs/ESLint como evidencia visual, según pide la rúbrica.
