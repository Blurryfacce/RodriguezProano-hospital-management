/**
 * Pruebas unitarias de frontend/js/api.js con fetch mockeado.
 *
 * api.js tambien es un script vanilla (const/function a nivel global, sin
 * module.exports), cargado en un sandbox vm igual que utils.js (ver
 * helpers/loadScript.js) para no modificar el archivo fuente.
 */
const { loadFrontendScript } = require('./helpers/loadScript');

function mockFetchResponse({ ok = true, status = 200, json = {} } = {}) {
    return jest.fn().mockResolvedValue({
        ok,
        status,
        json: jest.fn().mockResolvedValue(json),
    });
}

describe('api.js', () => {
    let apiFetch, PacientesAPI, DoctoresAPI, CitasAPI, HistoriasAPI, API_BASE;
    let fetchMock;

    function cargarApiConFetch(mock) {
        fetchMock = mock;
        const exposed = loadFrontendScript(
            'api.js',
            ['apiFetch', 'PacientesAPI', 'DoctoresAPI', 'CitasAPI', 'HistoriasAPI', 'API_BASE'],
            { fetch: mock }
        );
        ({ apiFetch, PacientesAPI, DoctoresAPI, CitasAPI, HistoriasAPI, API_BASE } = exposed);
    }

    beforeEach(() => {
        cargarApiConFetch(mockFetchResponse({ json: { id: 1 } }));
    });

    describe('apiFetch (funcion base)', () => {
        test('arma la URL completa con API_BASE y hace GET por defecto', async () => {
            await apiFetch('/pacientes');

            expect(fetchMock).toHaveBeenCalledWith(
                `${API_BASE}/pacientes`,
                expect.objectContaining({ headers: expect.objectContaining({ 'Content-Type': 'application/json' }) })
            );
        });

        test('retorna el JSON parseado de la respuesta en el caso feliz', async () => {
            cargarApiConFetch(mockFetchResponse({ json: { nombre: 'Juan' } }));

            const resultado = await apiFetch('/pacientes/1');

            expect(resultado).toEqual({ nombre: 'Juan' });
        });

        test('con response.ok = false NO lanza excepcion, retorna el body igual (bug documentado)', async () => {
            // BUG INTENCIONAL: apiFetch no revisa response.ok antes de retornar; un 404/500
            // del backend se trata igual que una respuesta exitosa desde el punto de vista
            // del llamador, quien debe inspeccionar manualmente el contenido.
            cargarApiConFetch(mockFetchResponse({ ok: false, status: 404, json: { status: 404, error: 'Recurso no encontrado' } }));

            const resultado = await apiFetch('/pacientes/9999');

            expect(resultado).toEqual({ status: 404, error: 'Recurso no encontrado' });
        });

        test('si fetch rechaza (error de red), la excepcion se propaga', async () => {
            cargarApiConFetch(jest.fn().mockRejectedValue(new Error('Network error')));

            await expect(apiFetch('/pacientes')).rejects.toThrow('Network error');
        });

        test('si response.json() rechaza (body vacio, comun en DELETE), la excepcion se propaga sin manejo especial (bug documentado)', async () => {
            // BUG INTENCIONAL: para DELETE el backend puede responder sin body; apiFetch
            // igual intenta parsear JSON, y si falla, no hay fallback ni mensaje claro.
            const mock = jest.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: jest.fn().mockRejectedValue(new SyntaxError('Unexpected end of JSON input')),
            });
            cargarApiConFetch(mock);

            await expect(apiFetch('/pacientes/1', { method: 'DELETE' })).rejects.toThrow('Unexpected end of JSON input');
        });

        test('pasar headers personalizados BORRA Content-Type en vez de combinarse (bug real no documentado en el codigo)', async () => {
            // Hallazgo nuevo: "config" hace `{ headers: {..., ...options.headers}, ...options }".
            // Como "options" tambien trae su propia clave "headers", el segundo spread
            // (...options) sobreescribe por completo el objeto headers ya fusionado.
            // Resultado: cualquier llamada que pase headers personalizados pierde
            // "Content-Type: application/json" en vez de combinarlo.
            await apiFetch('/pacientes', { headers: { Authorization: 'Bearer x' } });

            const [, config] = fetchMock.mock.calls[0];
            expect(config.headers).toEqual({ Authorization: 'Bearer x' });
            expect(config.headers['Content-Type']).toBeUndefined();
        });
    });

    describe('PacientesAPI', () => {
        test('listar hace GET a /pacientes', async () => {
            await PacientesAPI.listar();
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/pacientes`, expect.any(Object));
        });

        test('buscar hace GET a /pacientes/{id}', async () => {
            await PacientesAPI.buscar(5);
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/pacientes/5`, expect.any(Object));
        });

        test('crear hace POST con el paciente serializado como body', async () => {
            const paciente = { nombre: 'Ana', apellido: 'Ruiz' };
            await PacientesAPI.crear(paciente);

            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/pacientes`);
            expect(config.method).toBe('POST');
            expect(JSON.parse(config.body)).toEqual(paciente);
        });

        test('actualizar hace PUT a /pacientes/{id}', async () => {
            await PacientesAPI.actualizar(5, { nombre: 'Ana' });
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/pacientes/5`);
            expect(config.method).toBe('PUT');
        });

        test('eliminar hace DELETE a /pacientes/{id}', async () => {
            await PacientesAPI.eliminar(5);
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/pacientes/5`);
            expect(config.method).toBe('DELETE');
        });

        test('buscarPorNombre SI codifica el parametro con encodeURIComponent (el comentario dice que no sanitiza, pero si escapa la URL)', () => {
            // Hallazgo: el comentario "BUG INTENCIONAL: No sanitiza el parametro de busqueda
            // (XSS reflejado)" es impreciso en cuanto a ESTA funcion: encodeURIComponent SI
            // impide que caracteres como <, >, " lleguen crudos a la URL. Si existe riesgo de
            // XSS reflejado, esta en donde se RENDERIZA el resultado (pacientes.js), no aqui.
            PacientesAPI.buscarPorNombre('<script>alert(1)</script>');

            const [url] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/pacientes/buscar?nombre=%3Cscript%3Ealert(1)%3C%2Fscript%3E`);
            expect(url).not.toContain('<script>');
        });

        test('edadPromedio hace GET al endpoint de estadisticas', async () => {
            await PacientesAPI.edadPromedio();
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/pacientes/estadisticas/edad-promedio`, expect.any(Object));
        });
    });

    describe('DoctoresAPI', () => {
        test('listar hace GET a /doctores', async () => {
            await DoctoresAPI.listar();
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/doctores`, expect.any(Object));
        });

        test('buscar hace GET a /doctores/{id}', async () => {
            await DoctoresAPI.buscar(2);
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/doctores/2`, expect.any(Object));
        });

        test('crear hace POST con el doctor serializado', async () => {
            const doctor = { nombre: 'Sofia', especialidad: 'Dermatologia' };
            await DoctoresAPI.crear(doctor);
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/doctores`);
            expect(config.method).toBe('POST');
            expect(JSON.parse(config.body)).toEqual(doctor);
        });

        test('actualizar hace PUT a /doctores/{id}', async () => {
            await DoctoresAPI.actualizar(2, { nombre: 'Sofia' });
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/doctores/2`);
            expect(config.method).toBe('PUT');
        });

        test('eliminar hace DELETE a /doctores/{id}', async () => {
            await DoctoresAPI.eliminar(2);
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/doctores/2`);
            expect(config.method).toBe('DELETE');
        });

        test('buscarPorEspecialidad construye la URL hacia el endpoint vulnerable a SQL injection en backend', () => {
            // El propio comentario del codigo advierte: este endpoint es vulnerable en el
            // backend (ver DoctorService.buscarPorEspecialidadInsegura, Paso 7 - OWASP).
            // Aqui solo se verifica que el frontend construye la URL correctamente.
            DoctoresAPI.buscarPorEspecialidad("' OR '1'='1");
            const [url] = fetchMock.mock.calls[0];
            expect(url).toContain(`${API_BASE}/doctores/buscar-especialidad?q=`);
        });

        test('buscarPorNombre no valida parametros vacios antes de llamar a fetch (bug documentado)', () => {
            DoctoresAPI.buscarPorNombre('', '');
            expect(fetchMock).toHaveBeenCalledWith(
                `${API_BASE}/doctores/buscar-nombre?nombre=&apellido=`,
                expect.any(Object)
            );
        });
    });

    describe('CitasAPI', () => {
        test('listar hace GET a /citas', async () => {
            await CitasAPI.listar();
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/citas`, expect.any(Object));
        });

        test('buscar hace GET a /citas/{id}', async () => {
            await CitasAPI.buscar(7);
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/citas/7`, expect.any(Object));
        });

        test('actualizar hace PUT a /citas/{id}', async () => {
            await CitasAPI.actualizar(7, { motivo: 'Actualizado' });
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/citas/7`);
            expect(config.method).toBe('PUT');
        });

        test('eliminar hace DELETE a /citas/{id}', async () => {
            await CitasAPI.eliminar(7);
            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/citas/7`);
            expect(config.method).toBe('DELETE');
        });

        test('crear hace POST con la cita serializada', async () => {
            const cita = { pacienteId: 1, doctorId: 2, fechaHora: '2026-08-01T10:00:00' };
            await CitasAPI.crear(cita);

            const [url, config] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/citas`);
            expect(JSON.parse(config.body)).toEqual(cita);
        });

        test('porRangoFechas no valida que inicio sea anterior a fin (bug documentado)', () => {
            // BUG INTENCIONAL: el frontend envia el rango tal cual, sin validar el orden;
            // la validacion (si existiera) tendria que hacerse en el backend.
            CitasAPI.porRangoFechas('2026-12-31', '2026-01-01');
            const [url] = fetchMock.mock.calls[0];
            expect(url).toBe(`${API_BASE}/citas/rango-fechas?inicio=2026-12-31&fin=2026-01-01`);
        });

        test('porPaciente, porDoctor y porEstado construyen las URLs esperadas', async () => {
            await CitasAPI.porPaciente(3);
            expect(fetchMock).toHaveBeenLastCalledWith(`${API_BASE}/citas/paciente/3`, expect.any(Object));

            await CitasAPI.porDoctor(4);
            expect(fetchMock).toHaveBeenLastCalledWith(`${API_BASE}/citas/doctor/4`, expect.any(Object));

            await CitasAPI.porEstado('PROGRAMADA');
            expect(fetchMock).toHaveBeenLastCalledWith(`${API_BASE}/citas/estado/PROGRAMADA`, expect.any(Object));
        });
    });

    describe('HistoriasAPI', () => {
        test('listar hace GET a /historias-clinicas', async () => {
            await HistoriasAPI.listar();
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/historias-clinicas`, expect.any(Object));
        });

        test('buscar hace GET a /historias-clinicas/{id}', async () => {
            await HistoriasAPI.buscar(9);
            expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/historias-clinicas/9`, expect.any(Object));
        });

        test('crear envia el diagnostico tal cual, sin sanitizar (coherente con el bug de XSS del backend)', async () => {
            const historia = { pacienteId: 1, diagnostico: '<script>alert(1)</script>' };
            await HistoriasAPI.crear(historia);

            const [, config] = fetchMock.mock.calls[0];
            expect(JSON.parse(config.body).diagnostico).toBe('<script>alert(1)</script>');
        });

        test('porPaciente y porDoctor construyen las URLs esperadas', async () => {
            await HistoriasAPI.porPaciente(1);
            expect(fetchMock).toHaveBeenLastCalledWith(`${API_BASE}/historias-clinicas/paciente/1`, expect.any(Object));

            await HistoriasAPI.porDoctor(2);
            expect(fetchMock).toHaveBeenLastCalledWith(`${API_BASE}/historias-clinicas/doctor/2`, expect.any(Object));
        });
    });
});
