# Supabase para PlayReview AI

1. Abrí **SQL Editor** en tu proyecto Supabase.
2. Copiá y ejecutá el contenido de `schema.sql` completo.
3. Las cuentas y la confirmación de email se manejan con **Firebase Authentication**. No hace falta habilitar proveedores de login en Supabase.
4. En **Project Settings > API Keys**, conservá para Vercel: la URL del proyecto, la clave **Publishable** y la clave **Secret**.

Las claves se cargan como variables de entorno de Vercel. Nunca se agregan al APK ni al repositorio:

```text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
SUPABASE_SECRET_KEY
FIREBASE_PROJECT_ID
```

`SUPABASE_SECRET_KEY` sólo la usará el backend de Vercel para guardar análisis y reseñas. La clave Publishable se usa para acceder a la API de datos y `FIREBASE_PROJECT_ID` permite validar los ID tokens de Firebase. Nunca agregues la clave Secret al APK ni al repositorio.

Si ya ejecutaste el esquema anterior basado en `auth.users`, corré una vez
`firebase_auth_migration.sql` antes de desplegar el backend actualizado.

## Migraciones de producción

Si la base ya existe, no vuelvas a ejecutar `schema.sql` completo. Aplicá una vez, en este orden según las funciones que usa la versión actual:

1. `firebase_auth_migration.sql` si la base anterior usaba `auth.users`.
2. `queue_cache_migration.sql` para cola y caché de reseñas.
3. `credits_migration.sql` para créditos, límite de historial y protección antifraude.
4. `review_origin_unknown_migration.sql` para corregir el origen de reseñas antiguas, si corresponde.

Cada archivo debe ejecutarse en el SQL Editor y verificarse antes de desplegar el backend que lo necesita. No edites una migración ya aplicada: agregá otra con un nombre nuevo.
