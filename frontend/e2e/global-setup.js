/**
 * Global setup de Playwright: garantiza que la base de datos (PostgreSQL en
 * Docker) este arriba y saludable ANTES de que Playwright arranque los
 * webServer (backend/frontend) definidos en playwright.config.js.
 *
 * Playwright no puede administrar "docker compose" directamente como un
 * webServer (el comando `up -d` termina casi de inmediato en vez de quedar
 * corriendo en primer plano), asi que se maneja aqui, una sola vez.
 */
const { execSync } = require('child_process');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..', '..');

module.exports = async function globalSetup() {
    console.log('[global-setup] Verificando Docker / base de datos...');

    try {
        execSync('docker info', { stdio: 'ignore' });
    } catch (error) {
        throw new Error(
            'Docker no esta corriendo. Inicia Docker Desktop antes de ejecutar las pruebas E2E.'
        );
    }

    execSync('docker compose up -d', { cwd: REPO_ROOT, stdio: 'inherit' });

    const timeoutMs = 60_000;
    const intervalMs = 2_000;
    const start = Date.now();

    while (Date.now() - start < timeoutMs) {
        try {
            const status = execSync(
                'docker inspect --format="{{.State.Health.Status}}" hospital-db',
                { cwd: REPO_ROOT }
            ).toString().trim();

            if (status === 'healthy') {
                console.log('[global-setup] hospital-db esta healthy.');
                return;
            }
        } catch (error) {
            // El contenedor puede no existir todavia en el primer intento; se reintenta.
        }
        await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    throw new Error('hospital-db no alcanzo el estado "healthy" dentro del tiempo esperado (60s).');
};
