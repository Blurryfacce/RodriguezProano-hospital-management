const { test, expect } = require('@playwright/test');
const { datosUnicos, esperarQueAlertaSeLimpie } = require('./helpers');

test.describe('Flujo CRUD de Doctores', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.click('.nav-btn[data-section="doctores"]');
        await expect(page.locator('#section-doctores')).toHaveClass(/active/);
        await expect(page.locator('#doctores-table tbody tr').first()).toBeVisible();
    });

    test('crear y editar un doctor; eliminarlo muestra un error enganoso pese a tener exito', async ({ page }) => {
        const { etiqueta } = datosUnicos('E2EDoc');
        const nombre = etiqueta;
        const apellido = 'Doctor';

        // ---- Crear ----
        await page.click('#btn-nuevo-doctor');
        await expect(page.locator('#modal-doctor')).toHaveClass(/show/);

        await page.fill('#doctor-nombre', nombre);
        await page.fill('#doctor-apellido', apellido);
        await page.fill('#doctor-especialidad', 'Medicina General');
        await page.fill('#doctor-email', `${etiqueta}@e2e-test.com`);
        await page.fill('#doctor-consultorio', 'CONS-E2E');
        await page.screenshot({ path: 'e2e/screenshots/doctores-01-formulario-crear.png' });

        await page.click('#doctor-form button[type="submit"]');
        await expect(page.locator('#alert-container')).toContainText('Doctor creado exitosamente');
        await esperarQueAlertaSeLimpie(page);

        const fila = page.locator('#doctores-table tbody tr', { hasText: `${nombre} ${apellido}` });
        await expect(fila).toBeVisible();
        await page.screenshot({ path: 'e2e/screenshots/doctores-02-creado-en-tabla.png' });

        // ---- Editar ----
        await fila.locator('.btn-edit').click();
        await expect(page.locator('#modal-doctor')).toHaveClass(/show/);
        const nuevaEspecialidad = 'Medicina Interna';
        await page.fill('#doctor-especialidad', nuevaEspecialidad);
        await page.click('#doctor-form button[type="submit"]');

        await expect(page.locator('#alert-container')).toContainText('Doctor actualizado exitosamente');
        await expect(page.locator('#doctores-table tbody tr', { hasText: `${nombre} ${apellido}` }))
            .toContainText(nuevaEspecialidad);
        await page.screenshot({ path: 'e2e/screenshots/doctores-03-editado.png' });
        // BUG documentado (helpers.js): showAlert no cancela el setTimeout de la alerta
        // anterior, asi que dos alertas seguidas pueden pisarse. Se espera a que el ciclo
        // de esta alerta termine antes de disparar la siguiente accion.
        await esperarQueAlertaSeLimpie(page);

        // ---- Eliminar (sin citas asociadas: el borrado SI se ejecuta en el backend) ----
        // Mismo bug que en Pacientes (Paso 5): apiFetch intenta parsear el body vacio
        // de la respuesta DELETE (200 sin contenido) y lanza una excepcion, mostrando
        // un mensaje de ERROR enganoso pese a que el borrado si tuvo exito.
        await page.locator('#doctores-table tbody tr', { hasText: `${nombre} ${apellido}` })
            .locator('.btn-delete').click();
        await expect(page.locator('#alert-container')).toContainText('Error al eliminar doctor');
        await page.screenshot({ path: 'e2e/screenshots/doctores-04-eliminar-mensaje-enganoso-bug.png' });

        await page.click('.nav-btn[data-section="dashboard"]');
        await page.click('.nav-btn[data-section="doctores"]');
        await expect(page.locator('#doctores-table tbody tr', { hasText: `${nombre} ${apellido}` }))
            .toHaveCount(0);
        await page.screenshot({ path: 'e2e/screenshots/doctores-05-eliminacion-confirmada-tras-recargar.png' });
    });

    test('eliminar un doctor CON citas asociadas muestra un exito falso pese a fallar en el backend (bug critico)', async ({ page }) => {
        // Elena Rodriguez (id=1, seed data) tiene citas asociadas (fk_citas_doctor).
        // Verificado por API (curl) antes de escribir este test:
        //   DELETE /api/doctores/1 -> HTTP 500 (viola la FK al hacer commit) y el
        //   doctor NO se elimina. Pero como el body del 500 SI trae contenido JSON
        //   (a diferencia del DELETE exitoso, que responde vacio), apiFetch logra
        //   parsearlo sin lanzar excepcion y lo retorna como si fuera data valida.
        //   DoctoresModule.eliminarDoctor nunca revisa response.ok, asi que muestra
        //   "Doctor eliminado exitosamente" para una operacion que en realidad FALLO.
        const filaElena = page.locator('#doctores-table tbody tr', { hasText: 'Elena Rodriguez' });
        await expect(filaElena).toBeVisible();
        await page.screenshot({ path: 'e2e/screenshots/doctores-06-antes-de-eliminar-con-citas.png' });

        await filaElena.locator('.btn-delete').click();

        // BUG CRITICO: mensaje de EXITO pese a que el backend respondio 500 y no elimino nada.
        await expect(page.locator('#alert-container')).toContainText('Doctor eliminado exitosamente');
        await page.screenshot({ path: 'e2e/screenshots/doctores-07-exito-falso-bug-critico.png' });

        // Se confirma que Elena Rodriguez SIGUE en el sistema (el backend rechazo el borrado).
        await page.click('.nav-btn[data-section="dashboard"]');
        await page.click('.nav-btn[data-section="doctores"]');
        await expect(page.locator('#doctores-table tbody tr', { hasText: 'Elena Rodriguez' })).toBeVisible();
        await page.screenshot({ path: 'e2e/screenshots/doctores-08-elena-sigue-existiendo.png' });
    });

    test('la busqueda por especialidad usa el endpoint vulnerable a SQL Injection del backend', async ({ page }) => {
        await page.fill('#search-doctores', 'Cardiologia');
        await expect(page.locator('#doctores-table tbody tr')).toHaveCount(1);
        await expect(page.locator('#doctores-table tbody')).toContainText('Elena');

        // PoC de inyeccion SQL (ver Paso 3, DoctorService.buscarPorEspecialidadInsegura):
        // el "-- " comenta el resto de la query nativa (el "%'" que cierra el LIKE original),
        // dejando "especialidad ILIKE '%' OR '1'='1'" -> siempre verdadero -> bypasea el filtro
        // y retorna TODOS los doctores, incluido el que tiene especialidad NULL.
        await page.fill('#search-doctores', "' OR '1'='1' -- ");
        await expect(page.locator('#doctores-table tbody tr')).toHaveCount(4);
        await page.screenshot({ path: 'e2e/screenshots/doctores-09-sql-injection-bypass-desde-ui.png' });

        await page.fill('#search-doctores', '');
        await expect(page.locator('#doctores-table tbody tr').first()).toBeVisible();
    });
});
