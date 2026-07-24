/**
 * Utilidades compartidas para las pruebas E2E.
 *
 * IMPORTANTE: estas pruebas corren contra el backend REAL (Postgres en
 * Docker), no una base de datos aislada por test. Por eso los datos que se
 * crean usan sufijos unicos (timestamp) para no chocar entre corridas.
 */

function datosUnicos(prefijo) {
    const sufijo = Date.now();
    return { sufijo, etiqueta: `${prefijo}${sufijo}` };
}

/** Formatea un Date a 'YYYY-MM-DDTHH:mm' (formato de <input type="datetime-local">) en hora LOCAL. */
function toDatetimeLocalValue(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fechaFuturaDatetimeLocal(diasEnElFuturo = 7) {
    const fecha = new Date(Date.now() + diasEnElFuturo * 24 * 60 * 60 * 1000);
    fecha.setSeconds(0, 0);
    return toDatetimeLocalValue(fecha);
}

/**
 * Espera a que el #alert-container quede vacio (la alerta actual ya se auto-oculto).
 *
 * BUG documentado (ver informes/hallazgos): showAlert() programa un
 * setTimeout(4000ms) que limpia el contenedor SIN cancelar el timeout de una
 * alerta anterior. Si dos alertas se muestran en menos de 4s una de otra, el
 * timer de la primera puede borrar el mensaje de la segunda antes de tiempo.
 * Se espera a que cada ciclo de alerta termine antes de la siguiente accion
 * para que las pruebas no queden a merced de esa condicion de carrera.
 */
async function esperarQueAlertaSeLimpie(page) {
    await page.waitForFunction(
        () => document.getElementById('alert-container')?.innerHTML === '',
        null,
        { timeout: 6000 }
    );
}

module.exports = { datosUnicos, toDatetimeLocalValue, fechaFuturaDatetimeLocal, esperarQueAlertaSeLimpie };
