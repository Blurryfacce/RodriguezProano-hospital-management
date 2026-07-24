# Hallazgos — Paso 2: Pruebas unitarias backend

## Resultado

| Clase de prueba | Tests | Cobertura instrucciones | Cobertura lineas | Cobertura metodos |
|---|---|---|---|---|
| `PacienteServiceTest` | 12 | 99.4% | 100% | 100% (11/11) |
| `DoctorServiceTest` | 11 | 90.6% | 90.3% | 90.9% (10/11) |
| `CitaServiceTest` | 14 | 100% | 100% | 100% (12/12) |
| `HistoriaClinicaServiceTest` | 10 | 100% | 100% | 100% (9/9) |
| **Total** | **47** | — | — | — |

Todas las clases superan el umbral de 85% exigido por la rúbrica (nivel "Excelente").

Cada clase incluye ≥3 casos felices, ≥2 casos límite y ≥2 de manejo de errores, organizados en `@Nested` (`CasosFelices`, `CasosLimite`, `ManejoDeErrores`), con mocks aislados vía Mockito (`@Mock`/`@InjectMocks`).

## Limitación de entorno: Mockito no puede mockear `EntityManager` en JDK 24

`DoctorService.buscarPorEspecialidadInsegura` (el método con inyección SQL intencional) usa `EntityManager` inyectado por `@PersistenceContext`. Se intentó escribir un test que mockeara `EntityManager`/`Query` para verificar que el SQL se construye con concatenación insegura, pero:

- Con Mockito 5.11.0 (versión fijada por `spring-boot-starter-parent:3.3.0`), mockear la interfaz `jakarta.persistence.EntityManager` falla en JDK 24 con `MockitoException: Could not modify all classes` (incompatibilidad de byte-buddy con el bytecode de JDK 24).
- Se intentó subir Mockito a 5.18.0 vía `<mockito.version>` en el `pom.xml`, pero esto rompió **toda** la suite (`IllegalState: Could not initialize plugin: interface org.mockito.plugins.MockMaker`), por un conflicto de versión con `byte-buddy` gestionado por el BOM de `spring-boot-starter-parent`.
- Se revirtió el cambio de versión de Mockito para no desestabilizar las 47 pruebas ya validadas. El método `buscarPorEspecialidadInsegura` queda documentado como vulnerabilidad de inyección SQL (test que verifica la construcción del string, sin invocar `EntityManager` real) y su comportamiento real se verificará en el informe OWASP (Paso 7) contra la base de datos real, no como test unitario aislado.

Esto no es un bug del proyecto, sino una limitación conocida de compatibilidad entre Mockito 5.11 y JDK 24 en esta máquina de desarrollo. Si se usara JDK 17 o 21 (como recomienda el PDF) probablemente no ocurriría.

## Comando para reproducir

```
cd backend
mvn test jacoco:report
# Reporte HTML en backend/target/site/jacoco/index.html
# Reporte CSV en backend/target/site/jacoco/jacoco.csv
```
