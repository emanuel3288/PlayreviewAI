# Operación y escalabilidad

## Métricas importantes

- Invocaciones, duración y errores de Vercel.
- Créditos consumidos por plan y día.
- Trabajos `queued`, `running`, `completed` y `failed`.
- Tamaño de `reviews` y `analysis_runs` en Supabase.
- Transferencia de Supabase.
- Errores de Google Play y AdMob.

## Límites funcionales

- Free: 3 créditos diarios y análisis de Argentina.
- Premium: 30 créditos diarios.
- 50 reseñas/idioma: 1 crédito.
- 100 reseñas/idioma: 2 créditos.
- 500 reseñas o varios idiomas: 5 créditos.
- Historial: 3 análisis visibles para Free y 12 para Premium.

## Retención

El historial visible está limitado, pero la retención física debe controlarse. La siguiente evolución recomendada es una tarea programada que elimine `analysis_runs` antiguos y reseñas no referenciadas según una política aprobada.

## Incidentes

1. Revisá `job_id` y el estado del trabajo.
2. Consultá logs de Vercel y el error real de Supabase/Google Play.
3. No devuelvas mensajes internos ni secretos.
4. Si falta una migración, no despliegues código que llame a una función inexistente.
5. Reintentá solo errores transitorios; no dupliques el descuento de créditos.
