# PlayReview AI

Aplicación Android para buscar aplicaciones de Google Play, analizar reseñas y mostrar tendencias, problemas y recomendaciones.

## Arquitectura actual

```text
Android / Jetpack Compose
        │ Firebase ID token + HTTPS
        ▼
Vercel Functions (vercel-backend/)
        ├─ búsqueda y reseñas públicas de Google Play
        ├─ cola de análisis, créditos y planes
        └─ cuenta, historial y privacidad
        │ Supabase REST/RPC
        ▼
Supabase PostgreSQL

Firebase Authentication ── identidad y verificación de email
AdMob ── anuncios únicamente para cuentas gratuitas
```

La implementación que usa la app Android es `vercel-backend/`. El directorio `backend/` es un servicio FastAPI alternativo/legacy para scraping público y no forma parte del flujo de producción Android.

## Inicio rápido Android

1. Abrí el proyecto en Android Studio.
2. Colocá `google-services.json` en `app/`.
3. Verificá `AppConfig.kt` y `app/src/main/res/values/strings.xml`.
4. Ejecutá `./gradlew testDebugUnitTest assembleDebug`.

Para instalar en un dispositivo conectado: `./gradlew installDebug`.

## Backend Vercel

Variables requeridas: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY` y `FIREBASE_PROJECT_ID`.

```bash
cd vercel-backend
npm install
node --test tests/*.test.mjs
npx vercel --prod
```

## Base de datos

Para una instalación nueva ejecutá `supabase/schema.sql`. Para una existente aplicá las migraciones en el orden documentado en [supabase/README.md](supabase/README.md). Las migraciones aplicadas no se editan: cada cambio posterior agrega una migración nueva.

## Documentación

- [Arquitectura y responsabilidades](docs/ARCHITECTURE.md)
- [Desarrollo y flujo de cambios](docs/DEVELOPMENT.md)
- [Operación, límites y retención](docs/OPERATIONS.md)
- [Migraciones Supabase](supabase/README.md)
- [Backend Vercel](vercel-backend/README.md)
- [Publicación en Google Play](docs/google-play-release.md)

## Seguridad

Las claves privadas de Supabase y Firebase nunca se incluyen en el APK ni se guardan en Git. El cliente solo contiene URLs públicas y IDs de anuncios; autorización, créditos y plan se validan en Vercel.
