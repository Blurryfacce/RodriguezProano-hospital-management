/**
 * Carga un archivo JS "vanilla" (sin module.exports, con function/const a nivel
 * global pensado para <script> en el navegador) dentro de un sandbox vm de Node,
 * y expone los nombres indicados como propiedades del contexto retornado.
 *
 * Esto permite probar frontend/js/utils.js y frontend/js/api.js con Jest SIN
 * modificar esos archivos fuente (el proyecto no debe tocarse salvo lo pedido).
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

/**
 * @param {string} relativePath - ruta relativa a frontend/js/, ej. "utils.js"
 * @param {string[]} exposedNames - nombres declarados en el script (function/const
 *   de nivel superior) que se quieren exponer como propiedades del contexto devuelto
 * @param {object} extraContext - globals adicionales a inyectar en el sandbox
 *   (p. ej. { document, fetch, setTimeout })
 * @returns {object} contexto vm con los nombres expuestos como propiedades
 */
function loadFrontendScript(relativePath, exposedNames, extraContext = {}) {
    const filePath = path.resolve(__dirname, '..', '..', relativePath);
    const code = fs.readFileSync(filePath, 'utf-8');

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

    new vm.Script(`${code}\n${exposeStatements}`, { filename: filePath }).runInContext(context);

    const exposed = {};
    for (const name of exposedNames) {
        exposed[name] = context[`__exposed_${name}`];
    }
    return exposed;
}

module.exports = { loadFrontendScript };
