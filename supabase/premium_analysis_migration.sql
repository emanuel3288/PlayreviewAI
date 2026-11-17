-- Ejecutar en Supabase SQL Editor para habilitar análisis Premium.
-- Conserva los perfiles, usos y análisis existentes.

alter table public.analysis_runs drop constraint if exists analysis_runs_review_count_check;
alter table public.analysis_runs add constraint analysis_runs_review_count_check
  check (review_count between 0 and 500);

alter table public.daily_analysis_usage drop constraint if exists daily_analysis_usage_used_check;
alter table public.daily_analysis_usage add constraint daily_analysis_usage_used_check
  check (used between 0 and 30);

-- Las reseñas se entregan únicamente a través del backend autenticado.
alter table public.reviews enable row level security;

create or replace function public.consume_analysis(
  p_user_id text, p_max_reviews integer, p_language_count integer
)
returns table (allowed boolean, remaining smallint, plan text)
language plpgsql
security definer set search_path = public
as $$
declare
  current_plan public.subscription_plan;
  current_usage smallint;
  daily_limit smallint;
begin
  insert into public.profiles (id) values (p_user_id) on conflict (id) do nothing;
  select profiles.plan into current_plan from public.profiles
    where id = p_user_id for update;
  daily_limit := case when current_plan = 'premium' then 30 else 3 end;

  if p_max_reviews not in (50, 100, 500)
      or p_language_count not between 1 and 3
      or (current_plan = 'free' and (p_max_reviews <> 50 or p_language_count <> 1)) then
    return query select false, 0::smallint, current_plan::text;
    return;
  end if;

  insert into public.daily_analysis_usage (user_id, usage_date, used)
    values (p_user_id, current_date, 0) on conflict (user_id, usage_date) do nothing;
  select used into current_usage from public.daily_analysis_usage
    where user_id = p_user_id and usage_date = current_date for update;
  if current_usage >= daily_limit then
    return query select false, 0::smallint, current_plan::text;
    return;
  end if;
  update public.daily_analysis_usage set used = used + 1
    where user_id = p_user_id and usage_date = current_date;
  return query select true, (daily_limit - current_usage - 1)::smallint, current_plan::text;
end;
$$;

revoke execute on function public.consume_analysis(text, integer, integer) from public;
grant execute on function public.consume_analysis(text, integer, integer) to service_role;
