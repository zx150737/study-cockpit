-- Run once on a new Supabase project. Existing Java/MySQL data is not modified.
begin;
create schema if not exists cockpit_private;
revoke all on schema cockpit_private from public, anon;
grant usage on schema cockpit_private to authenticated, service_role;

create table public.sc_goals (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 128),
  deleted boolean not null default false,
  created_at timestamptz not null default now(),
  unique(id, user_id)
);
create table public.sc_subjects (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  goal_id integer not null,
  name text not null check (length(btrim(name)) between 1 and 128),
  default_weight numeric(8,2) not null default 1 check (default_weight > 0),
  deleted boolean not null default false,
  created_at timestamptz not null default now(),
  unique(id, user_id),
  foreign key(goal_id, user_id) references public.sc_goals(id, user_id)
);
create table public.sc_tasks (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  goal_id integer not null,
  subject_id integer not null,
  title text not null check (length(btrim(title)) between 1 and 255),
  weight numeric(8,2) not null check (weight > 0),
  task_date date not null check (task_date >= date '1970-01-01'),
  deadline timestamp,
  mastery smallint check (mastery between 1 and 5),
  review_note text check (length(review_note) <= 1000),
  finish_status smallint not null default 0 check (finish_status in (0,1)),
  finished_at timestamptz,
  deleted boolean not null default false,
  created_at timestamptz not null default now(),
  unique(id, user_id),
  foreign key(goal_id, user_id) references public.sc_goals(id, user_id),
  foreign key(subject_id, user_id) references public.sc_subjects(id, user_id)
);
create table public.sc_images (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  task_id integer not null,
  original_name text not null check (length(original_name) between 1 and 255),
  path text not null unique,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  file_size integer not null check (file_size between 1 and 5242880),
  upload_time timestamptz not null default now(),
  expire_time timestamptz not null default (now() + interval '15 days'),
  foreign key(task_id, user_id) references public.sc_tasks(id, user_id)
);
create table public.sc_weeks (
  id integer generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  week_start date not null,
  summary jsonb not null,
  unique(user_id, week_start)
);
create index sc_goals_owner on public.sc_goals(user_id, id);
create index sc_subjects_owner_goal on public.sc_subjects(user_id, goal_id);
create index sc_tasks_owner_date on public.sc_tasks(user_id, task_date desc, id desc) where not deleted;
create index sc_tasks_owner_subject on public.sc_tasks(user_id, subject_id) where not deleted;
create index sc_images_owner_task on public.sc_images(user_id, task_id);
create index sc_images_expiry on public.sc_images(expire_time);

alter table public.sc_goals enable row level security;
alter table public.sc_subjects enable row level security;
alter table public.sc_tasks enable row level security;
alter table public.sc_images enable row level security;
alter table public.sc_weeks enable row level security;
create policy own_goals on public.sc_goals to authenticated using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));
create policy own_subjects on public.sc_subjects to authenticated using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));
create policy own_tasks on public.sc_tasks to authenticated using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));
create policy own_images on public.sc_images to authenticated using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));
create policy own_weeks on public.sc_weeks for select to authenticated using(user_id = (select auth.uid()));
revoke all on public.sc_goals, public.sc_subjects, public.sc_tasks, public.sc_images, public.sc_weeks from anon, authenticated;
grant select, insert, update on public.sc_goals, public.sc_subjects, public.sc_tasks to authenticated;
grant select, insert, delete on public.sc_images to authenticated;
grant select on public.sc_weeks to authenticated;
grant all on public.sc_goals, public.sc_subjects, public.sc_tasks, public.sc_images, public.sc_weeks to service_role;
grant usage on sequence public.sc_goals_id_seq, public.sc_subjects_id_seq, public.sc_tasks_id_seq, public.sc_images_id_seq to authenticated;
grant usage on sequence public.sc_goals_id_seq, public.sc_subjects_id_seq, public.sc_tasks_id_seq, public.sc_images_id_seq, public.sc_weeks_id_seq to service_role;

-- Enforce invariants even if a client bypasses the UI or RPC functions.
create function cockpit_private.sc_guard() returns trigger language plpgsql set search_path = '' as $$
begin
  if tg_op = 'UPDATE' then
    if new.id <> old.id or new.user_id <> old.user_id then raise exception '不能修改数据归属'; end if;
    new.created_at := old.created_at;
    if old.deleted and not new.deleted then raise exception '不能恢复已删除记录'; end if;
  else
    if tg_table_name <> 'sc_images' then new.created_at := now(); end if;
  end if;
  if tg_table_name = 'sc_subjects' then
    if tg_op = 'INSERT' or new.goal_id <> old.goal_id then
      if not exists(select 1 from public.sc_goals where id = new.goal_id and user_id = new.user_id and not deleted)
      then raise exception '目标不存在'; end if;
    end if;
  elsif tg_table_name = 'sc_tasks' then
    if tg_op = 'INSERT' or (new.goal_id, new.subject_id) is distinct from (old.goal_id, old.subject_id) then
      if not exists(select 1 from public.sc_subjects s join public.sc_goals g on g.id=s.goal_id
        where s.id=new.subject_id and s.goal_id=new.goal_id and s.user_id=new.user_id
        and g.user_id=new.user_id and not s.deleted and not g.deleted)
      then raise exception '科目不属于所选目标或已删除'; end if;
    end if;
    if tg_op = 'INSERT' then new.finish_status := 0; new.finished_at := null;
    elsif new.finish_status is distinct from old.finish_status then
      new.finished_at := case when new.finish_status=1 then now() else null end;
    else new.finished_at := old.finished_at;
    end if;
  elsif tg_table_name = 'sc_images' then
    if not exists(select 1 from public.sc_tasks where id=new.task_id and user_id=new.user_id and not deleted)
    then raise exception '任务不存在'; end if;
    if new.path !~ ('^' || new.user_id::text || '/' || new.task_id::text || '/[0-9a-f-]{36}\.(jpg|png|webp)$')
    then raise exception '图片路径无效'; end if;
    new.upload_time := now(); new.expire_time := now() + interval '15 days';
  end if;
  return new;
end $$;
create trigger sc_goals_guard before insert or update on public.sc_goals for each row execute function cockpit_private.sc_guard();
create trigger sc_subjects_guard before insert or update on public.sc_subjects for each row execute function cockpit_private.sc_guard();
create trigger sc_tasks_guard before insert or update on public.sc_tasks for each row execute function cockpit_private.sc_guard();
create trigger sc_images_guard before insert on public.sc_images for each row execute function cockpit_private.sc_guard();

create function cockpit_private.sc_today() returns date language sql stable set search_path = '' as $$
  select (now() at time zone 'Asia/Shanghai')::date;
$$;
create function cockpit_private.sc_monday(d date) returns date language sql immutable set search_path = '' as $$
  select d - (extract(isodow from d)::integer - 1);
$$;
create function cockpit_private.sc_rate(done numeric, total numeric) returns numeric language sql immutable set search_path = '' as $$
  select coalesce(round(done / nullif(total,0) * 100,2),0);
$$;

create view cockpit_private.sc_task_view with (security_invoker = true) as
select t.*, jsonb_build_object(
  'id',t.id,'goalId',t.goal_id,'subjectId',t.subject_id,'title',t.title,'weight',t.weight,
  'goalName',case when g.deleted then '已删除目标' else g.name end,
  'subjectName',case when s.deleted then '已删除科目' else s.name end,
  'taskDate',t.task_date,'deadline',to_char(t.deadline,'YYYY-MM-DD HH24:MI:SS'),
  'mastery',t.mastery,'reviewNote',t.review_note,'finishStatus',t.finish_status,
  'finishedAt',to_char(t.finished_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI:SS'),
  'createdAt',to_char(t.created_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI:SS'),
  'overdue',t.finish_status=0 and (t.task_date<cockpit_private.sc_today() or coalesce(t.deadline<now() at time zone 'Asia/Shanghai',false)),
  'imageCount',(select count(*) from public.sc_images i where i.task_id=t.id and i.expire_time>now())
) as value
from public.sc_tasks t join public.sc_goals g on g.id=t.goal_id join public.sc_subjects s on s.id=t.subject_id
where not t.deleted;
grant select on cockpit_private.sc_task_view to authenticated;

create function cockpit_private.sc_week(w date, g integer default null) returns jsonb
language sql stable set search_path = '' as $$
with days as (
  select d::date as day, count(t.id) as n,
    cockpit_private.sc_rate(sum(t.weight) filter(where t.finish_status=1),sum(t.weight)) as rate
  from generate_series(w::timestamp,(w+6)::timestamp,interval '1 day') d
  left join public.sc_tasks t on t.task_date=d::date and not t.deleted and (g is null or t.goal_id=g)
  group by d
), totals as (
  select count(*) n, cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight)) rate
  from public.sc_tasks where not deleted and task_date between w and w+6 and (g is null or goal_id=g)
), subjects as (
  select s.id,s.name,cockpit_private.sc_rate(sum(t.weight) filter(where t.finish_status=1),sum(t.weight)) rate
  from public.sc_subjects s left join public.sc_tasks t on t.subject_id=s.id and not t.deleted and t.task_date between w and w+6
  where not s.deleted and (g is null or s.goal_id=g) group by s.id,s.name
)
select jsonb_build_object('weekStart',w,'weekEnd',w+6,'completionRate',totals.rate,'taskTotal',totals.n,
  'avgDailyRate',(select coalesce(round(avg(rate) filter(where n>0),2),0) from days),
  'dailyTrend',(select jsonb_agg(jsonb_build_object('date',day,'rate',rate,'hasTask',n>0) order by day) from days),
  'subjectRates',(select coalesce(jsonb_agg(jsonb_build_object('subjectId',id,'name',name,'completionRate',rate) order by id),'[]') from subjects))
from totals;
$$;

-- Private snapshot writer: no client can choose another user's identity.
create function cockpit_private.sc_settle_for(u uuid) returns void
language sql security definer set search_path = '' as $$
with weeks as (
  select d::date w from generate_series(
    (select cockpit_private.sc_monday(min(task_date))::timestamp from public.sc_tasks where user_id=u),
    (cockpit_private.sc_monday(cockpit_private.sc_today())-7)::timestamp,interval '7 days') d
), daily as (
  select task_date,cockpit_private.sc_monday(task_date) w,count(*) n,
    count(*) filter(where finish_status=1) done,
    sum(weight) total_weight,coalesce(sum(weight) filter(where finish_status=1),0) done_weight
  from public.sc_tasks where user_id=u and not deleted and task_date<cockpit_private.sc_monday(cockpit_private.sc_today())
  group by task_date
)
insert into public.sc_weeks(user_id,week_start,summary)
select u,w.w,jsonb_build_object('weekStart',w.w,'weekEnd',w.w+6,
  'completionRate',cockpit_private.sc_rate(sum(done_weight),sum(total_weight)),
  'avgDailyRate',coalesce(round(avg(cockpit_private.sc_rate(done_weight,total_weight)) filter(where n>0),2),0),
  'checkinDays',count(*) filter(where done>0),'taskTotal',coalesce(sum(n),0),'taskFinished',coalesce(sum(done),0),
  'snapshotAt',to_char(now() at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI:SS'))
from weeks w left join daily d on d.w=w.w group by w.w
on conflict(user_id,week_start) do nothing;
$$;
create function public.sc_settle() returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception '请先登录' using errcode='42501'; end if;
  perform cockpit_private.sc_settle_for(auth.uid());
end $$;
create function cockpit_private.sc_settle_all() returns void language plpgsql security definer set search_path = '' as $$
declare u uuid;
begin
  for u in select distinct user_id from public.sc_tasks loop perform cockpit_private.sc_settle_for(u); end loop;
end $$;
revoke all on all functions in schema cockpit_private from public, anon, authenticated;
grant execute on function cockpit_private.sc_today(),cockpit_private.sc_monday(date),cockpit_private.sc_rate(numeric,numeric),cockpit_private.sc_week(date,integer) to authenticated;
revoke all on function public.sc_settle() from public, anon;
grant execute on function public.sc_settle() to authenticated;
commit;
