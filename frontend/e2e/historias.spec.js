const { test, expect } = require('@playwright/test');
const { esperarQueAlertaSeLimpie, esperarModuloListo } = require('./helpers');

/**
 * Mismo BUG-01 que Citas (ver 07-paso5-e2e-citas.md): HistoriaClinica tiene DOS
 * relaciones LAZY (paciente, doctor), asi que el listado tambien falla siempre.
 */
test.describe('Flujo de Historias Clinicas', () => {
    test('la tabla de historias aparece vacia al navegar (BUG-01)', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="historias"]');
        await expect(page.locator('#section-historias')).toHaveClass(/active/);

        await expect(page.locator('#historias-table tbody tr')).toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/historias-01-tabla-vacia-bug01.png' });
    });

    test('crear una historia con payload XSS se guarda sin sanitizar, pero BUG-01 impide verla en la UI', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="historias"]');
        await expect(page.locator('#section-historias')).toHaveClass(/active/);
        await esperarModuloListo(page);

        await page.click('#btn-nueva-historia');
        await expect(page.locator('#modal-historia')).toHaveClass(/show/);

        const payloadXSS = '<img src=x onerror="window.__xssEjecutado = true">';

        await page.selectOption('#historia-paciente', { label: 'Juan Perez' });
        await page.selectOption('#historia-doctor', { index: 1 });
        // BUG INTENCIONAL confirmado en Paso 3 (integracion) y aqui end-to-end: el
        // campo diagnostico no se sanitiza ni en el frontend ni en el backend.
        await page.fill('#historia-diagnostico', payloadXSS);
        await page.fill('#historia-tratamiento', 'Tratamiento de prueba E2E');
        await page.screenshot({ path: 'e2e/screenshots/historias-02-formulario-con-payload-xss.png' });

        await page.click('#historia-form button[type="submit"]');

        // El POST SI funciona (paciente/doctor se cargan explicitamente antes de
        // guardar, igual que en Citas) -> se muestra el mensaje de exito real.
        await expect(page.locator('#alert-container')).toContainText('Historia clinica creada exitosamente');
        await page.screenshot({ path: 'e2e/screenshots/historias-03-creada-exitosamente.png' });
        await esperarQueAlertaSeLimpie(page);

        // Igual que en Citas: cargarHistorias() vuelve a fallar (BUG-01) y la tabla
        // queda vacia. Consecuencia curiosa: como no hay boton "Ver" (no hay filas),
        // NO es posible disparar HistoriasModule.verHistoria() desde la UI real para
        // demostrar la ejecucion del XSS ahi -- BUG-01 termina "blindando"
        // accidentalmente al usuario de la UI contra el XSS de HistoriasModule,
        // aunque el payload SI quedo almacenado tal cual en la base de datos (esto ya
        // se confirmo directamente contra la API en el Paso 3, sin pasar por la UI).
        await expect(page.locator('#historias-table tbody tr')).toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/historias-04-tabla-sigue-vacia-tras-crear-bug01.png' });

        const xssEjecutado = await page.evaluate(() => window.__xssEjecutado === true);
        expect(xssEjecutado).toBe(false);
    });

    test('crear una historia sin doctor (opcional) tambien funciona', async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="historias"]');
        await expect(page.locator('#section-historias')).toHaveClass(/active/);
        await esperarModuloListo(page);

        await page.click('#btn-nueva-historia');
        await expect(page.locator('#modal-historia')).toHaveClass(/show/);

        await page.selectOption('#historia-paciente', { label: 'Maria Garcia' });
        // No se selecciona doctor: queda en la opcion vacia ("opcional").
        await page.fill('#historia-diagnostico', 'Control de rutina sin doctor asignado (E2E)');
        await page.click('#historia-form button[type="submit"]');

        await expect(page.locator('#alert-container')).toContainText('Historia clinica creada exitosamente');
        await page.screenshot({ path: 'e2e/screenshots/historias-05-creada-sin-doctor.png' });
    });
});
