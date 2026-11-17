# Preparar PlayReview AI para Google Play

## Firma de publicación

Creá una única clave de carga y guardala fuera del repositorio, con sus contraseñas en un gestor de secretos:

```bash
mkdir -p keystores
keytool -genkeypair -v \
  -keystore keystores/playreview-upload.jks \
  -alias playreview-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Copiá `signing.properties.example` como `signing.properties` y completá las cuatro variables. Después generá el bundle:

```bash
./gradlew :app:bundleRelease
```

Subí `app/build/outputs/bundle/release/app-release.aab` a Play Console y activá Play App Signing. Registrá en Firebase Authentication las huellas SHA-1 y SHA-256 de la clave de carga y de la clave de firma de la app que Play Console muestra después del primer alta. Así Google Sign-In funcionará tanto en pruebas como en producción.

## Antes de enviar a revisión

Completá en Play Console la URL pública de la política de privacidad, la sección Data safety, el acceso para el revisor, la clasificación de contenido y el público objetivo. La política debe describir Firebase Authentication, el backend de Vercel, Supabase, la caché local y el procesamiento de las reseñas de Google Play.
