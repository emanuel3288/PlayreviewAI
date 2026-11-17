-- Google Play no proporciona el país de origen de cada autor en estas consultas.
-- Quita la atribución anterior, calculada a partir de la tienda solicitada.
update public.reviews set region = 'und' where region <> 'und';
alter table public.reviews alter column region set default 'und';
