# PlayReview AI en Vercel

Backend serverless: busca apps en Google Play por país y ofrece 3 créditos gratuitos diarios. Una consulta de 50 reseñas consume 1 crédito; las consultas Premium de 100 reseñas consumen 2 y las de 500 reseñas o varios idiomas consumen 5. Premium permite 30 créditos diarios y guarda el historial en Supabase.

En Vercel configurá estas variables de entorno:

```
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
SUPABASE_SECRET_KEY
FIREBASE_PROJECT_ID
```

El backend calcula un HMAC del email verificado usando `SUPABASE_SECRET_KEY` para impedir que borrar y crear una cuenta reinicie los créditos gratuitos del mismo día. El email no se guarda en el registro antifraude.

El límite vuelve a cero cada día. Para habilitar una cuenta Premium manualmente, asigná `plan = 'premium'` a su fila en `public.profiles` con su Firebase UID. La app consulta ese plan al abrirse o volver del segundo plano. Todavía no hay cobro ni altas automáticas por pago.

## Cola y caché de análisis

Antes de desplegar la versión con control de carga, ejecutá `supabase/queue_cache_migration.sql` una vez en el SQL Editor de Supabase. La migración permite hasta tres consultas activas a Google Play, mantiene las solicitudes adicionales en cola y conserva durante diez minutos las muestras recientes por aplicación, país e idioma. La app consulta el estado del trabajo automáticamente hasta recibir el resultado.

El idioma de consulta de Google Play no garantiza el idioma real de cada comentario. Se detecta el idioma aproximado a partir del texto; los comentarios demasiado breves o ambiguos aparecen como `und` (sin identificar). Una reseña publicada en varios idiomas de consulta se cuenta una sola vez.

El país seleccionado configura la tienda consultada (`gl`); Google Play no proporciona el país del autor en las reseñas obtenidas. El backend guarda su origen como `und`. En comparaciones Premium, cada tienda devuelve su calificación pública y consulta hasta 50 reseñas de los últimos 30 días. Si Google Play muestra una misma reseña en más de una tienda, se conserva en cada muestra, tal como fue devuelta por esa consulta. Para corregir filas antiguas, ejecutá `supabase/review_origin_unknown_migration.sql`.

## Publicación

```bash
npx vercel --prod
```

La API publicada expone `GET /api/apps/search?q=...`, `GET /api/profile`, `POST /api/analyze`, `POST /api/compare`, `GET /api/analysis-jobs` y `GET /api/portfolio`. La comparación requiere Premium; el portafolio devuelve 3 análisis recientes por app para Free y 12 para Premium.

## Autenticación Firebase

La aplicación Android usa Firebase Authentication. En Vercel agregá `FIREBASE_PROJECT_ID`
con el **Project ID** (no el número de proyecto) de Firebase. La API valida la firma y la
audiencia de cada ID token con las claves públicas de Firebase; no necesita una clave privada
ni una cuenta de servicio. Sólo acepta cuentas cuyo email ya fue confirmado.

Antes de publicar los cambios, instalá la nueva dependencia:

```bash
npm install
```
