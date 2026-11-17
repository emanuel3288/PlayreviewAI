const policy = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Política de privacidad | PlayReview AI</title>
  <style>
    body { margin: 0; background: #f8fafc; color: #172033; font-family: system-ui, sans-serif; line-height: 1.6; }
    main { max-width: 760px; margin: 0 auto; padding: 40px 24px 64px; }
    h1, h2 { line-height: 1.2; color: #172554; } h1 { margin-bottom: 0; }
    .date { color: #526079; margin-top: 4px; } a { color: #1d4ed8; }
  </style>
</head>
<body><main>
  <h1>Política de privacidad de PlayReview AI</h1>
  <p class="date">Última actualización: 14 de noviembre de 2026</p>

  <p>PlayReview AI permite analizar reseñas públicas de aplicaciones de Google Play. Esta política explica qué información se procesa al usar la aplicación y con qué finalidad.</p>

  <h2>Información que procesamos</h2>
  <ul>
    <li><strong>Cuenta:</strong> dirección de email, identificador de usuario y estado de verificación. Si elegís iniciar sesión con Google, Firebase puede procesar los identificadores y datos básicos de perfil que Google proporcione para ese acceso.</li>
    <li><strong>Uso del servicio:</strong> las búsquedas, aplicaciones analizadas, plan de la cuenta, cuota diaria e historial de análisis.</li>
    <li><strong>Reseñas analizadas:</strong> contenido, calificación, fecha e identificador público de reseñas obtenidas de Google Play, junto con la aplicación a la que pertenecen.</li>
    <li><strong>Datos locales:</strong> una copia de análisis recientes puede guardarse en el dispositivo para permitir su consulta posterior.</li>
    <li><strong>Publicidad:</strong> las cuentas gratuitas pueden recibir anuncios mediante Google Mobile Ads (AdMob), que puede procesar identificadores del dispositivo, información técnica y datos de publicidad conforme a sus políticas y controles disponibles.</li>
    <li><strong>Prevención de abuso:</strong> conservamos un identificador HMAC irreversible derivado del email verificado, junto con el consumo diario de créditos gratuitos. No guardamos el email en este registro y lo usamos únicamente para impedir que borrar y crear cuentas repetidamente reinicie la cuota del mismo día.</li>
  </ul>

  <h2>Cómo usamos la información</h2>
  <p>Usamos estos datos para autenticar tu cuenta, aplicar límites de uso, obtener y analizar reseñas, mostrar resultados e historial, mantener la seguridad y funcionamiento del servicio, y mostrar publicidad a las cuentas gratuitas. No vendemos datos personales.</p>

  <h2>Servicios que intervienen</h2>
  <p>La autenticación se realiza con Firebase Authentication de Google. El backend opera en Vercel y los perfiles e historiales se almacenan en Supabase. Las reseñas se consultan desde Google Play. Estos proveedores pueden procesar datos conforme a sus propias políticas de privacidad.</p>

  <h2>Conservación y eliminación</h2>
  <p>Conservamos la información mientras la cuenta y el servicio estén activos, salvo que sea necesario conservar un registro técnico mínimo por razones de seguridad y prevención de abuso. Al eliminar la cuenta borramos sus datos, pero el registro HMAC de consumo de créditos gratuitos puede conservarse para evitar el reinicio de la cuota durante el día en curso. Podés eliminar tu cuenta desde la aplicación o solicitarlo desde <a href="/api/account-deletion">esta página de eliminación de cuenta</a>.</p>

  <h2>Seguridad</h2>
  <p>Usamos conexiones HTTPS y controles de acceso en los servicios que almacenan información. Ningún método de transmisión o almacenamiento es completamente infalible.</p>

  <h2>Menores de edad</h2>
  <p>PlayReview AI no está dirigida a menores de 13 años. Si considerás que un menor nos proporcionó información personal, contactanos para revisarlo.</p>

  <h2>Cambios y contacto</h2>
  <p>Podemos actualizar esta política para reflejar cambios en el servicio o requisitos aplicables. Publicaremos la versión vigente en esta misma URL.</p>
  <p>Para consultas o solicitudes de privacidad: <a href="mailto:ema25met@gmail.com">ema25met@gmail.com</a>.</p>
</main></body></html>`;

export default function handler(request, response) {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    response.setHeader('Allow', 'GET, HEAD');
    return response.status(405).send('Método no permitido');
  }
  response.setHeader('Content-Type', 'text/html; charset=utf-8');
  response.setHeader('Cache-Control', 'public, max-age=3600');
  return response.status(200).send(request.method === 'HEAD' ? '' : policy);
}
