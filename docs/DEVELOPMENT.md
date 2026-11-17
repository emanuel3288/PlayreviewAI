# Desarrollo y cambios

## Orden recomendado

1. Cambiar la regla de negocio y sus tests.
2. Actualizar backend y contratos.
3. Actualizar Android y textos.
4. Ejecutar verificaciones.
5. Aplicar una migración Supabase si cambia el esquema.
6. Desplegar Vercel.
7. Instalar un APK Debug y probar el flujo real.

## Verificaciones

```bash
cd vercel-backend
node --check lib/supabase.mjs
node --test tests/*.test.mjs

cd ..
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

## Configuración Android

`app/src/main/java/com/appinsight/ai/AppConfig.kt` contiene URLs públicas y el ID de prueba de AdMob. Antes de producción, reemplazá los IDs de prueba por los reales. Nunca coloques claves privadas allí.

## Configuración backend

Las variables se administran en Vercel: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY` y `FIREBASE_PROJECT_ID`. No copies secretos a Git ni a logs.

## Pruebas manuales mínimas

- Registro con email y verificación.
- Google Sign-In.
- Perfil y créditos.
- Análisis de 7, 14 y 30 días.
- Free: 3 créditos y 3 análisis visibles.
- Premium: 30 créditos y 12 análisis visibles.
- Eliminar cuenta con contraseña y Google.
- Borrar y registrar nuevamente: no recuperar créditos gratuitos del mismo día.
- Free ve anuncios; Premium no.

## Regla de base de datos

Cada cambio de esquema debe tener un archivo `supabase/*_migration.sql` idempotente cuando sea posible, con comentarios y permisos explícitos. Primero se prueba en desarrollo, luego en producción y finalmente se despliega el backend que depende de él.
