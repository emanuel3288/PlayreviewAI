# Servicio de búsqueda y scraping público

Este servicio usa `google-play-scraper` únicamente desde el backend. Busca catálogos públicos de Google Play y extrae reseñas públicas por páginas, respetando pausas configurables y sin intentar evadir CAPTCHA, límites o controles de acceso.

## Ejecutar en Ubuntu con Docker

1. Instalá Docker Engine y el complemento Docker Compose siguiendo la documentación de Docker para Ubuntu.
2. Desde la raíz del proyecto: `cp .env.example .env`.
3. Cambiá `POSTGRES_PASSWORD` por una contraseña larga y única.
4. Ejecutá `docker compose up --build`.
5. Abrí `http://localhost:8000/docs` para la documentación OpenAPI.

## Probar una búsqueda real

```bash
curl 'http://localhost:8000/api/apps/search?q=whatsapp&country=ar&lang=es'
```

El resultado proviene de Google Play en el momento de la consulta. Si Play no responde, la API responde `502`; no genera sustitutos ficticios.

## Lanzar un trabajo real

```bash
curl -X POST http://localhost:8000/api/scraping/jobs \
  -H 'content-type: application/json' \
  -d '{"app_id":"com.whatsapp","period_days":30}'
```

Consultá `GET /api/scraping/jobs/{job_id}` para el estado y `GET /api/apps/com.whatsapp/insights` al finalizar. `period_days` admite `10` (últimos 10 días) o `30` (último mes). Por defecto se consultan Argentina, España, México, Estados Unidos, Reino Unido, Brasil, Francia y Alemania en sus idiomas principales. El contador de reseñas y las regiones completadas se actualizan con datos reales. Si la cantidad total accesible es desconocida, el cliente debe mostrar progreso indeterminado, no un porcentaje inventado.

## Límites importantes

- El trabajo continúa hasta que Google Play termine la paginación accesible, sin un límite propio por región. Google puede limitar, variar o impedir resultados por país, idioma o momento; por ello no es posible prometer el 100 % de reseñas históricas. El scraper persiste cobertura parcial y errores reales.
- El servicio almacena contenido de reseñas y debe tener una política de retención y controles de acceso antes de manejar datos de terceros en producción.
- El análisis incluido es una base local y explicable sobre reseñas recolectadas. No afirma causas técnicas. Se debe conectar un proveedor de IA solo con datos y controles aprobados.
