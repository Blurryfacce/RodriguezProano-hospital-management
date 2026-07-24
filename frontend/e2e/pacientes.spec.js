const { test, expect } = require('@playwright/test');
const { datosUnicos } = require('./helpers');

test.describe('Flujo CRUD de Pacientes', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="pacientes"]');
        await expect(page.locator('#section-pacientes')).toHaveClass(/active/);
        await expect(page.locator('#pacientes-table tbody tr').first()).toBeVisible();
    });

    test('crear, editar y eliminar un paciente de punta a punta', async ({ page }) => {
        const { etiqueta } = datosUnicos('E2E');
        const nombre = etiqueta;
        const apellido = 'Paciente';
        const email = `${etiqueta}@e2e-test.com`;

        // ---- Crear ----
        await page.click('#btn-nuevo-paciente');
        await expect(page.locator('#modal-paciente')).toHaveClass(/show/);

        await page.fill('#paciente-nombre', nombre);
        await page.fill('#paciente-apellido', apellido);
        await page.fill('#paciente-email', email);
        await page.fill('#paciente-telefono', '0991234567');
        await page.fill('#paciente-direccion', 'Direccion de prueba E2E');
        await page.screenshot({ path: 'e2e/screenshots/pacientes-01-formulario-crear.png' });

        await page.click('#paciente-form button[type="submit"]');
        await expect(page.locator('#alert-container')).toContainText('Paciente creado exitosamente');

        const fila = page.locator('#pacientes-table tbody tr', { hasText: `${nombre} ${apellido}` });
        await expect(fila).toBeVisible();
        await expect(fila).toContainText(email);
        await page.screenshot({ path: 'e2e/screenshots/pacientes-02-creado-en-tabla.png' });

        // ---- Editar ----
        await fila.locator('.btn-edit').click();
        await expect(page.locator('#modal-paciente')).toHaveClass(/show/);
        await expect(page.locator('#paciente-nombre')).toHaveValue(nombre);

        const nuevoTelefono = '0987654321';
        await page.fill('#paciente-telefono', nuevoTelefono);
        await page.click('#paciente-form button[type="submit"]');

        await expect(page.locator('#alert-container')).toContainText('Paciente actualizado exitosamente');
        await expect(page.locator('#pacientes-table tbody tr', { hasText: `${nombre} ${apellido}` }))
            .toContainText(nuevoTelefono);
        await page.screenshot({ path: 'e2e/screenshots/pacientes-03-editado.png' });

        // ---- Eliminar ----
        // BUG INTENCIONAL (ver informes/hallazgos): el frontend NO pide confirmacion
        // antes de eliminar.
        let aparecioDialogoConfirmacion = false;
        page.on('dialog', (dialog) => {
            aparecioDialogoConfirmacion = true;
            dialog.dismiss();
        });

        await page.locator('#pacientes-table tbody tr', { hasText: `${nombre} ${apellido}` })
            .locator('.btn-delete').click();

        // BUG REAL descubierto en este flujo (documentado en el comentario de apiFetch,
        // "para DELETE intenta parsear JSON aunque el body este vacio"): el backend SI
        // elimina el registro (responde 200 con body vacio), pero apiFetch intenta hacer
        // response.json() sobre ese body vacio, lo cual lanza una excepcion. El frontend
        // termina mostrando un mensaje de ERROR enganoso para una operacion que en
        // realidad tuvo exito.
        await expect(page.locator('#alert-container')).toContainText('Error al eliminar paciente');
        expect(aparecioDialogoConfirmacion).toBe(false);
        await page.screenshot({ path: 'e2e/screenshots/pacientes-04-eliminar-mensaje-enganoso-bug.png' });

        // Se confirma que el borrado SI se ejecuto en el backend recargando la seccion
        // (fuerza un nuevo GET /api/pacientes independiente del que fallo al parsear).
        await page.click('.nav-btn[data-section="dashboard"]');
        await page.click('.nav-btn[data-section="pacientes"]');
        await expect(page.locator('#pacientes-table tbody tr', { hasText: `${nombre} ${apellido}` }))
            .toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/pacientes-05-eliminacion-confirmada-tras-recargar.png' });
    });

    test('la busqueda en tiempo real filtra la tabla por nombre (y expone un bug de case-sensitivity)', async ({ page }) => {
        await page.fill('#search-pacientes', 'Juan');
        await expect(page.locator('#pacientes-table tbody tr')).toHaveCount(1);
        await expect(page.locator('#pacientes-table tbody')).toContainText('Juan');

        // BUG documentado (Paso 3, backend LIKE case-sensitive): en minusculas no encuentra
        // nada. renderTabla() siempre inserta al menos una <tr> (la de "sin resultados"),
        // por eso se verifica el TEXTO del placeholder en vez del conteo de filas.
        await page.fill('#search-pacientes', 'juan');
        await expect(page.locator('#pacientes-table tbody')).toContainText('No hay pacientes registrados');
        await page.screenshot({ path: 'e2e/screenshots/pacientes-06-busqueda-case-sensitive-bug.png' });

        await page.fill('#search-pacientes', '');
        await expect(page.locator('#pacientes-table tbody tr').first()).toBeVisible();
    });
});
