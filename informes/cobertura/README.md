# Reportes de cobertura

- **Backend (JaCoCo):** abrir [`backend-jacoco/index.html`](backend-jacoco/index.html)
- **Frontend (Jest/Istanbul):** abrir [`frontend-jest/index.html`](frontend-jest/index.html)

Generados con:

```bash
# Backend
cd backend && mvn test jacoco:report

# Frontend
cd frontend && npx jest --coverage
```

Resumen (ver detalle completo en `informes/hallazgos/02-paso2-unitarias-backend.md` y `04-paso4-unitarias-frontend.md`):

| Módulo | Cobertura |
|---|---|
| `PacienteService` | 99.4% instrucciones / 100% métodos |
| `DoctorService` | 90.6% instrucciones / 90.9% métodos |
| `CitaService` | 100% instrucciones / 100% métodos |
| `HistoriaClinicaService` | 100% instrucciones / 100% métodos |
| `frontend/js/utils.js` | 100% (statements/branches/funcs/lines) |
| `frontend/js/api.js` | 100% (statements/branches/funcs/lines) |
