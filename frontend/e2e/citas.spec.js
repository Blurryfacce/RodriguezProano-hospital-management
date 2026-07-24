const { test, expect } = require('@playwright/test');
const { fechaFuturaDatetimeLocal, esperarQueAlertaSeLimpie, esperarModuloListo } = require('./helpers');

/**
 * BUG-01 (ver informes/hallazgos/00, 03): GET /api/citas y variantes fallan con
 * HTTP 500 al serializar el Doctor LAZY de cada Cita (falta jackson-datatype-hibernate6).
 * Estas pruebas documentan el impacto REAL en el usuario: el modulo de Citas nunca
 * puede LISTAR nada via UI, aunque crear una cita nueva si funciona en el backend.
 */
test.describe('Flujo de Citas Medicas', () => {
    test('el dashboard muestra pacientes/doctores correctos pero "Citas hoy" y "Edad promedio" quedan sin calcular (BUG-01 en cascada)', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('#stat-total-pacientes')).toHaveText('5');
        await expect(page.locator('#stat-total-doctores')).toHaveText('4');
        // cargarDashboard() usa Promise.all([...]).catch(()=>[]) por llamada individual,
        // pero apiFetch nunca rechaza (ni con 500), asi que citas.filter(...) truena sobre
        // el objeto de error devuelto por /api/citas, abortando el resto de la funcion antes
        // de llegar a calcular "citas hoy" y "edad promedio" -> quedan en el placeholder "—".
        await expect(page.locator('#stat-citas-hoy')).toHaveText('—');
        await expect(page.locator('#stat-edad-promedio')).toHaveText('—');
        await page.screenshot({ path: 'e2e/screenshots/citas-00-dashboard-stats-incompletas-bug01.png' });
    });

    test('la tabla de citas aparece vacia al navegar (BUG-01: el listado siempre falla)', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="citas"]');
        await expect(page.locator('#section-citas')).toHaveClass(/active/);

        // renderTabla() ni siquiera llega a mostrar el placeholder "No hay citas
        // registradas": revienta en citas.map(...) antes de esa rama, asi que la tabla
        // queda literalmente vacia (0 <tr>), sin ningun mensaje para el usuario.
        await expect(page.locator('#citas-table tbody tr')).toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/citas-01-tabla-vacia-sin-mensaje-bug01.png' });
    });

    test('crear una cita muestra "creada exitosamente" pero la tabla sigue sin mostrarla (BUG-01)', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="citas"]');
        await expect(page.locator('#section-citas')).toHaveClass(/active/);
        await esperarModuloListo(page);

        await page.click('#btn-nueva-cita');
        await expect(page.locator('#modal-cita')).toHaveClass(/show/);

        // Los <select> SI se llenan correctamente: vienen de PacientesAPI/DoctoresAPI.listar(),
        // que no tienen el problema de serializacion (son entidades "planas").
        await page.selectOption('#cita-paciente', { label: 'Juan Perez' });
        await page.selectOption('#cita-doctor', { index: 1 });
        await page.fill('#cita-fecha-hora', fechaFuturaDatetimeLocal(10));
        await page.fill('#cita-motivo', 'Consulta E2E');
        await page.screenshot({ path: 'e2e/screenshots/citas-02-formulario-crear.png' });

        await page.click('#cita-form button[type="submit"]');

        // El POST SI funciona (el doctor se carga explicitamente antes de guardar, ver
        // Paso 3): se muestra el mensaje de exito real.
        await expect(page.locator('#alert-container')).toContainText('Cita creada exitosamente');
        await page.screenshot({ path: 'e2e/screenshots/citas-03-creada-exitosamente.png' });
        await esperarQueAlertaSeLimpie(page);

        // Pero cargarCitas() (llamado automaticamente tras guardar) vuelve a fallar con
        // el mismo 500 al listar, asi que la tabla NUNCA refleja la cita recien creada:
        // el usuario no tiene forma de confirmar visualmente que su cita quedo registrada.
        await expect(page.locator('#citas-table tbody tr')).toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/citas-04-tabla-sigue-vacia-tras-crear-bug01.png' });
    });

    test('no valida doble booking: se pueden crear dos citas para el mismo doctor a la misma hora', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="citas"]');
        await expect(page.locator('#section-citas')).toHaveClass(/active/);
        await esperarModuloListo(page);

        const mismaFecha = fechaFuturaDatetimeLocal(15);

        for (const [paciente, motivo] of [['Juan Perez', 'Primera cita doble booking'], ['Maria Garcia', 'Segunda cita doble booking']]) {
            await page.click('#btn-nueva-cita');
            await expect(page.locator('#modal-cita')).toHaveClass(/show/);
            await page.selectOption('#cita-paciente', { label: paciente });
            await page.selectOption('#cita-doctor', { index: 1 }); // mismo doctor en ambas iteraciones
            await page.fill('#cita-fecha-hora', mismaFecha);
            await page.fill('#cita-motivo', motivo);
            await page.click('#cita-form button[type="submit"]');
            await expect(page.locator('#alert-container')).toContainText('Cita creada exitosamente');
            await esperarQueAlertaSeLimpie(page);
        }

        // BUG INTENCIONAL confirmado end-to-end: ninguna de las dos creaciones fue
        // rechazada por conflicto de horario (no existe esa validacion ni en front ni backend).
        await page.screenshot({ path: 'e2e/screenshots/citas-05-doble-booking-permitido.png' });
    });
});
