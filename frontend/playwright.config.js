// @ts-check
const path = require('path');
const { defineConfig, devices } = require('@playwright/test');

const isWindows = process.platform === 'win32';

module.exports = defineConfig({
    testDir: './e2e',
    fullyParallel: false, // los flujos comparten la misma base de datos real (no hay aislamiento por test)
    workers: 1,
    retries: 0,
    reporter: [
        ['html', { outputFolder: 'e2e-report', open: 'never' }],
        ['list'],
    ],
    use: {
        baseURL: 'http://localhost:3000',
        trace: 'retain-on-failure',
        screenshot: 'on', // evidencia visual de cada paso relevante, por rubrica del proyecto
        video: 'retain-on-failure',
    },
    projects: [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    ],

    globalSetup: require.resolve('./e2e/global-setup.js'),

    // Levanta automaticamente frontend y backend si no estan corriendo ya.
    // Si ya estan arriba (reuseExistingServer), Playwright los reutiliza tal cual.
    webServer: [
        {
            command: 'npx http-server . -p 3000 -s -c-1',
            cwd: __dirname,
            url: 'http://localhost:3000/index.html',
            reuseExistingServer: true,
            timeout: 30_000,
        },
        {
            command: isWindows ? 'mvn.cmd spring-boot:run' : 'mvn spring-boot:run',
            cwd: path.resolve(__dirname, '..', 'backend'),
            url: 'http://localhost:8080/api/pacientes',
            reuseExistingServer: true,
            timeout: 180_000,
        },
    ],
});
