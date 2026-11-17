-- Ejecutar una vez en Supabase SQL Editor antes de desplegar la cola y caché.
-- Limita a tres consultas activas a Google Play y comparte resultados recientes.

create table if not exists public.google_play_cache (
  cache_key text primary key,
  max_reviews smallint not null check (max_reviews between 50 and 500),
  reviews jsonb not null,
  fetched_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists google_play_cache_expires_at_idx on public.google_play_cache (expires_at);

create table if not exists public.analysis_queue_guard (
  id boolean primary key default true check (id),
  created_at timestamptz not null default now()
);
insert into public.analysis_queue_guard (id) values (true) on conflict do nothing;

create table if not exists public.analysis_jobs (
  id uuid primary key default gen_random_uuid(),
  user_id text not null references public.profiles(id) on delete cascade,
  payload jsonb not null,
  status text not null default 'queued' check (status in ('queued', 'running', 'completed', 'failed')),
  attempts smallint not null default 0 check (attempts between 0 and 3),
  result jsonb,
  error text,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz
);
create index if not exists analysis_jobs_status_created_idx on public.analysis_jobs (status, created_at);
create index if not exists analysis_jobs_user_id_idx on public.analysis_jobs (user_id, created_at desc);

alter table public.google_play_cache enable row level security;
alter table public.analysis_jobs enable row level security;

create or replace function public.claim_next_analysis_job()
returns setof public.analysis_jobs
language plpgsql
security definer set search_path = public
as $$
declare claimed_id uuid;
begin
  perform 1 from public.analysis_queue_guard where id = true for update;
  update public.analysis_jobs set status = 'queued', started_at = null, attempts = attempts + 1
  where status = 'running' and started_at < now() - interval '4 minutes' and attempts < 3;
  update public.analysis_jobs set status = 'failed', error = 'El análisis venció en la cola. Intentá nuevamente.', completed_at = now()
  where (status = 'running' and started_at < now() - interval '4 minutes' and attempts >= 3)
     or (status = 'queued' and created_at < now() - interval '15 minutes');
  if (select count(*) from public.analysis_jobs where status = 'running') >= 3 then return; end if;
  select id into claimed_id from public.analysis_jobs where status = 'queued' order by created_at for update skip locked limit 1;
  if claimed_id is null then return; end if;
  update public.analysis_jobs set status = 'running', started_at = now(), attempts = attempts + 1 where id = claimed_id;
  return query select * from public.analysis_jobs where id = claimed_id;
end;
$$;

revoke all on table public.google_play_cache, public.analysis_queue_guard, public.analysis_jobs from anon, authenticated;
revoke execute on function public.claim_next_analysis_job() from public;
grant execute on function public.claim_next_analysis_job() to service_role;
