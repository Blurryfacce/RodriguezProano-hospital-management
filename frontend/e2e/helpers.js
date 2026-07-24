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

module.exports = { datosUnicos, toDatetimeLocalValue, fechaFuturaDatetimeLocal };
