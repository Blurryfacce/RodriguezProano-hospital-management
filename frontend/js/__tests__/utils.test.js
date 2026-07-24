/**
 * Pruebas unitarias de frontend/js/utils.js.
 *
 * utils.js es un script "vanilla" pensado para <script src="utils.js"> en el
 * navegador (declara funciones a nivel global, sin module.exports). Para
 * probarlo sin modificar el archivo fuente, se carga dentro de un sandbox vm
 * con helpers/loadScript.js y se exponen las funciones a probar.
 */
const { loadFrontendScript } = require('./helpers/loadScript');

describe('utils.js', () => {
    let formatDate, formatDateTime, escapeHTML, showAlert,
        validateEmail, validateTelefono, isFutureDate, localToISO;

    beforeEach(() => {
        document.body.innerHTML = '';
        const exposed = loadFrontendScript(
            'utils.js',
            ['formatDate', 'formatDateTime', 'escapeHTML', 'showAlert',
                'validateEmail', 'validateTelefono', 'isFutureDate', 'localToISO'],
            { document, setTimeout, clearTimeout }
        );
        ({ formatDate, formatDateTime, escapeHTML, showAlert,
            validateEmail, validateTelefono, isFutureDate, localToISO } = exposed);
    });

    describe('formatDate', () => {
        test('formatea una fecha valida a texto legible en espanol', () => {
            // Se usa mediodia UTC para evitar que el dia cambie por la zona
            // horaria local del entorno donde corre el test.
            const resultado = formatDate('2026-03-15T12:00:00.000Z');
            expect(resultado).toContain('2026');
            expect(resultado).toContain('marzo');
            expect(resultado).toContain('15');
        });

        test('con fecha undefined retorna el string "Invalid Date" en silencio (bug documentado)', () => {
            // BUG INTENCIONAL: no valida entrada, retorna un string sin sentido
            // en vez de lanzar un error o retornar null/mensaje claro.
            expect(formatDate(undefined)).toBe('Invalid Date');
        });

        test('con fecha null NO falla: new Date(null) se interpreta como epoch (bug documentado)', () => {
            // BUG INTENCIONAL: null no se valida explicitamente. Date(null) === Date(0),
            // asi que un valor "vacio" termina mostrando una fecha real (epoch, 1969 o 1970
            // segun la zona horaria) en vez de indicar que no hay fecha.
            const resultado = formatDate(null);
            expect(resultado).not.toBe('Invalid Date');
            expect(resultado).toMatch(/196[9]|1970/);
        });

        test('con string vacio retorna "Invalid Date" (boundary)', () => {
            expect(formatDate('')).toBe('Invalid Date');
        });
    });

    describe('formatDateTime', () => {
        test('formatea fecha y hora validas incluyendo la hora', () => {
            const resultado = formatDateTime('2026-03-15T12:30:00.000Z');
            expect(resultado).toContain('2026');
            expect(resultado).toMatch(/\d{1,2}:\d{2}/);
        });

        test('con fecha undefined retorna "Invalid Date" (bug documentado, igual que formatDate)', () => {
            expect(formatDateTime(undefined)).toBe('Invalid Date');
        });
    });

    describe('escapeHTML', () => {
        test('escapa &, <, > y " correctamente', () => {
            const resultado = escapeHTML('<div class="a">Tom & Jerry</div>');
            expect(resultado).toBe('&lt;div class=&quot;a&quot;&gt;Tom &amp; Jerry&lt;/div&gt;');
        });

        test('NO escapa comillas simples ni backticks (bug documentado, XSS parcial)', () => {
            // BUG INTENCIONAL: escape incompleto. Si el resultado se inserta dentro de un
            // atributo HTML delimitado con comillas simples, o en un template literal JS,
            // sigue siendo explotable.
            const resultado = escapeHTML(`it's a \`test\``);
            expect(resultado).toContain("'");
            expect(resultado).toContain('`');
        });

        test('con null/undefined/string vacio retorna string vacio (boundary)', () => {
            expect(escapeHTML(null)).toBe('');
            expect(escapeHTML(undefined)).toBe('');
            expect(escapeHTML('')).toBe('');
        });
    });

    describe('showAlert', () => {
        test('inserta el mensaje dentro de #alert-container con la clase del tipo indicado', () => {
            document.body.innerHTML = '<div id="alert-container"></div>';

            showAlert('Operacion exitosa', 'success');

            const container = document.getElementById('alert-container');
            expect(container.innerHTML).toContain('Operacion exitosa');
            expect(container.innerHTML).toContain('alert-success');
        });

        test('sin indicar el tipo usa "success" por defecto', () => {
            document.body.innerHTML = '<div id="alert-container"></div>';

            showAlert('mensaje sin tipo');

            const container = document.getElementById('alert-container');
            expect(container.innerHTML).toContain('alert-success');
        });

        test('sin #alert-container en el DOM no lanza error (boundary)', () => {
            document.body.innerHTML = '';
            expect(() => showAlert('mensaje', 'error')).not.toThrow();
        });

        test('usa innerHTML sin escapar el mensaje: XSS real via DOM (bug documentado)', () => {
            document.body.innerHTML = '<div id="alert-container"></div>';

            const payload = '<img src=x onerror="window.__xss = true">';
            showAlert(payload, 'error');

            const container = document.getElementById('alert-container');
            // BUG INTENCIONAL: el payload se inserta tal cual via innerHTML, sin pasar por
            // escapeHTML. jsdom normaliza la serializacion del atributo (agrega comillas),
            // pero el <img> con onerror queda igualmente insertado como elemento real del
            // DOM: en un navegador esto dispara el onerror (XSS-DOM).
            const img = container.querySelector('img');
            expect(img).not.toBeNull();
            expect(img.getAttribute('onerror')).toBe('window.__xss = true');
        });

        test('el mensaje se auto-oculta despues de 4 segundos', () => {
            // jest.useFakeTimers() debe activarse ANTES de cargar el script: showAlert
            // captura la referencia a setTimeout del contexto vm en el momento de la carga,
            // asi que si se activan los fake timers despues, seguiria usando el setTimeout real.
            jest.useFakeTimers();
            document.body.innerHTML = '<div id="alert-container"></div>';
            const { showAlert: showAlertConFakeTimers } = loadFrontendScript(
                'utils.js', ['showAlert'], { document, setTimeout, clearTimeout }
            );

            showAlertConFakeTimers('temporal', 'success');
            const container = document.getElementById('alert-container');
            expect(container.innerHTML).not.toBe('');

            jest.advanceTimersByTime(4000);
            expect(container.innerHTML).toBe('');

            jest.useRealTimers();
        });
    });

    describe('validateEmail', () => {
        test('acepta emails validos comunes', () => {
            expect(validateEmail('juan.perez@hospital.com')).toBe(true);
        });

        test('acepta emails con "+" en el nombre de usuario (el comentario del codigo dice que los rechaza, pero no es asi)', () => {
            // Hallazgo: el comentario "BUG INTENCIONAL... rechaza emails validos con +"
            // NO se corresponde con el comportamiento real de la regex: el caracter '+'
            // SI esta incluido en la clase [a-zA-Z0-9._%+-], por lo que se acepta.
            expect(validateEmail('user+tag@example.com')).toBe(true);
        });

        test('rechaza un email sin TLD (ej. "usuario@dominio")', () => {
            expect(validateEmail('usuario@dominio')).toBe(false);
        });

        test('acepta erroneamente un dominio con doble punto (bug real de la regex)', () => {
            // BUG real (no documentado en el comentario original): "domain..com" no es un
            // dominio valido, pero la regex lo acepta porque no prohibe puntos consecutivos.
            expect(validateEmail('user@domain..com')).toBe(true);
        });

        test('con null, undefined o string vacio retorna false (boundary)', () => {
            expect(validateEmail(null)).toBe(false);
            expect(validateEmail(undefined)).toBe(false);
            expect(validateEmail('')).toBe(false);
        });
    });

    describe('validateTelefono', () => {
        test('acepta un numero de 10 digitos', () => {
            expect(validateTelefono('0991234567')).toBe(true);
        });

        test('rechaza numeros con menos o mas de 10 digitos (boundary)', () => {
            expect(validateTelefono('099123456')).toBe(false);
            expect(validateTelefono('09912345678')).toBe(false);
        });

        test('acepta erroneamente prefijos que no son reales en Ecuador (bug documentado)', () => {
            // BUG INTENCIONAL: solo valida la cantidad de digitos, no el prefijo (09, 08, etc).
            expect(validateTelefono('1234567890')).toBe(true);
        });

        test('con null o undefined no lanza excepcion y retorna false', () => {
            expect(() => validateTelefono(null)).not.toThrow();
            expect(validateTelefono(null)).toBe(false);
            expect(validateTelefono(undefined)).toBe(false);
        });
    });

    describe('isFutureDate', () => {
        test('retorna true para una fecha claramente futura', () => {
            const futura = new Date(Date.now() + 1000 * 60 * 60 * 24 * 30).toISOString();
            expect(isFutureDate(futura)).toBe(true);
        });

        test('retorna false para una fecha claramente pasada', () => {
            const pasada = new Date(Date.now() - 1000 * 60 * 60 * 24 * 30).toISOString();
            expect(isFutureDate(pasada)).toBe(false);
        });

        test('con una fecha invalida retorna false en silencio, sin avisar del error (bug documentado)', () => {
            expect(isFutureDate('esto-no-es-una-fecha')).toBe(false);
        });
    });

    describe('localToISO', () => {
        test('convierte un valor de datetime-local interpretandolo como hora LOCAL del sistema (bug documentado)', () => {
            // BUG INTENCIONAL: no fuerza UTC. new Date(localDateTime) interpreta el valor
            // segun la zona horaria del sistema que ejecuta el codigo (no necesariamente la
            // del usuario final), lo que puede desalinear la hora mostrada vs la guardada.
            const input = '2026-03-15T08:30';
            const resultado = localToISO(input);

            const [datePart, timePart] = input.split('T');
            const [y, m, d] = datePart.split('-').map(Number);
            const [hh, mm] = timePart.split(':').map(Number);
            const esperadoInterpretandoComoLocal = new Date(y, m - 1, d, hh, mm, 0, 0).toISOString();

            expect(resultado).toBe(esperadoInterpretandoComoLocal);
        });

        test('retorna un string ISO 8601 valido terminado en Z', () => {
            const resultado = localToISO('2026-03-15T08:30');
            expect(resultado).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
        });
    });
});
