/**
 * Carga un archivo JS "vanilla" (sin module.exports, con function/const a nivel
 * global pensado para <script> en el navegador) dentro de un sandbox vm de Node,
 * y expone los nombres indicados como propiedades del contexto retornado.
 *
 * Esto permite probar frontend/js/utils.js y frontend/js/api.js con Jest SIN
 * modificar esos archivos fuente (el proyecto no debe tocarse salvo lo pedido).
 *
 * Ademas, el codigo se instrumenta en memoria con babel-plugin-istanbul (usando
 * la ruta ABSOLUTA del archivo real como filename) para que Jest --coverage
 * pueda reportar cobertura real de utils.js/api.js, pese a ejecutarse dentro
 * de un vm.createContext aislado (que Jest normalmente no puede observar).
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const babel = require('@babel/core');

function instrumentar(code, absoluteFilePath) {
    const resultado = babel.transformSync(code, {
        filename: absoluteFilePath,
        babelrc: false,
        configFile: false,
        plugins: [['babel-plugin-istanbul', { cwd: path.resolve(__dirname, '..', '..', '..') }]],
    });
    return resultado.code;
}

function fusionarCobertura(coberturaDelSandbox) {
    if (!coberturaDelSandbox) return;
    global.__coverage__ = global.__coverage__ || {};

    for (const [archivo, datos] of Object.entries(coberturaDelSandbox)) {
        if (!global.__coverage__[archivo]) {
            global.__coverage__[archivo] = datos;
            continue;
        }
        const acumulado = global.__coverage__[archivo];
        for (const tipo of ['s', 'f', 'b']) {
            for (const key of Object.keys(datos[tipo])) {
                if (Array.isArray(acumulado[tipo][key])) {
                    acumulado[tipo][key] = acumulado[tipo][key].map((v, i) => v + datos[tipo][key][i]);
                } else {
                    acumulado[tipo][key] = (acumulado[tipo][key] || 0) + datos[tipo][key];
                }
            }
        }
    }
}

// IMPORTANTE: no se puede fusionar la cobertura justo despues de cargar el script,
// porque en ese momento las funciones todavia no fueron LLAMADAS por el test (solo
// definidas). Cada contexto vm mantiene su propio objeto de cobertura, que se sigue
// mutando en memoria a medida que el test invoca las funciones. Por eso se registran
// todos los contextos y la fusion final se hace una sola vez en afterAll, cuando ya
// se ejecutaron todas las llamadas de todos los tests del archivo.
const contextosRegistrados = [];

if (typeof afterAll === 'function') {
    afterAll(() => {
        for (const contexto of contextosRegistrados) {
            fusionarCobertura(contexto.__coverage__);
        }
    });
}

/**
 * @param {string} relativePath - ruta relativa a frontend/js/, ej. "utils.js"
 * @param {string[]} exposedNames - nombres declarados en el script (function/const
 *   de nivel superior) que se quieren exponer como propiedades del contexto devuelto
 * @param {object} extraContext - globals adicionales a inyectar en el sandbox
 *   (p. ej. { document, fetch, setTimeout })
 * @returns {object} objeto con los nombres expuestos como propiedades
 */
function loadFrontendScript(relativePath, exposedNames, extraContext = {}) {
    const filePath = path.resolve(__dirname, '..', '..', relativePath);
    const code = fs.readFileSync(filePath, 'utf-8');
    const codigoInstrumentado = instrumentar(code, filePath);

    const context = {
        console,
        setTimeout,
        clearTimeout,
        ...extraContext,
    };
    vm.createContext(context);

    const exposeStatements = exposedNames
        .map((name) => `globalThis.__exposed_${name} = typeof ${name} !== 'undefined' ? ${name} : undefined;`)
        .join('\n');

    new vm.Script(`${codigoInstrumentado}\n${exposeStatements}`, { filename: filePath }).runInContext(context);

    contextosRegistrados.push(context);

    const exposed = {};
    for (const name of exposedNames) {
        exposed[name] = context[`__exposed_${name}`];
    }
    return exposed;
}

module.exports = { loadFrontendScript };
