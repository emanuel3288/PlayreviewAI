# Arquitectura de PlayReview AI

## Componentes

| Componente | Responsabilidad | No debe hacer |
|---|---|---|
| `app/` | UI Compose, sesión Firebase, caché local y HTTP | Guardar secretos o decidir Premium por sí sola |
| `vercel-backend/api/` | Validar HTTP, autenticación y respuestas | Duplicar reglas de negocio |
| `vercel-backend/lib/` | Créditos, cola, Supabase, scraping y análisis | Exponer claves al cliente |
| Supabase | Perfiles, créditos, reseñas, historial y trabajos | Autenticar directamente la app Android |
| Firebase Auth | Registro, Google Sign-In y verificación | Guardar historial o créditos |
| AdMob | Anuncios para usuarios gratuitos | Decidir el plan |

## Flujo de un análisis

1. Android obtiene un Firebase ID token.
2. `POST /api/analyze` valida token y parámetros.
3. Vercel calcula el costo: 1, 2 o 5 créditos.
4. Supabase descuenta créditos con `consume_analysis_credits` de forma atómica.
5. La cola consulta Google Play y guarda el resultado.
6. Android consulta el trabajo hasta recibir `completed` o `failed`.

El backend es la autoridad para plan, créditos, historial y eliminación de cuenta. La app solo representa ese estado.

## Contratos principales

- `GET /api/profile`: plan y créditos restantes.
- `GET /api/apps/search`: búsqueda pública.
- `POST /api/analyze`: crea un análisis.
- `GET /api/analysis-jobs`: consulta estado.
- `POST /api/compare`: comparación Premium.
- `GET /api/portfolio`: Free ve 3 análisis por app; Premium ve 12.
- `DELETE /api/account`: elimina datos de cuenta y conserva solo el registro HMAC antifraude diario.

## Datos y retención

- `analysis_runs`: se conservan los últimos 12 por usuario y aplicación.
- El portafolio limita la respuesta según el plan.
- La caché Android conserva hasta 12 resultados y muestra 3 a Free.
- `reviews` se deduplica por aplicación y reseña pública.
- La retención física de reseñas debe evolucionar a una política automática antes de escalar.

## Reglas para escalar

- Agregá reglas de negocio en `lib/`, no en handlers HTTP.
- Agregá migraciones nuevas; no reescribas migraciones ya ejecutadas.
- Centralizá configuración en `AppConfig.kt` y variables de entorno.
- Reutilizá los contratos HTTP para futuras plataformas.
- Cuando crezca el volumen, separá el worker de scraping de las funciones HTTP y agregá métricas por `job_id`.
