-- 202611250004_admin.sql
-- 后台管理能力：管理员白名单 + 只读管理查询 + 图片长期归档标记。
-- 原则：不修改任何既有 RLS 策略，普通用户的数据隔离保持不变；
--       管理查询全部走 SECURITY DEFINER 函数，并在函数内强制校验管理员身份。

begin;

-- ------------------------------------------------------------
-- 1. 管理员白名单
--    使用 user_id 而非 email：认证系统换邮箱不会导致权限漂移。
--    该表不创建任何 RLS 策略 = 前端角色读不到也写不到，只能由服务端维护。
-- ------------------------------------------------------------
create table public.sc_admins(
  user_id    uuid primary key references auth.users(id) on delete cascade,
  note       text check (length(note) <= 100),
  created_at timestamptz not null default now()
);
alter table public.sc_admins enable row level security;
revoke all on table public.sc_admins from anon, authenticated;

-- ------------------------------------------------------------
-- 2. 图片长期归档标记
-- ------------------------------------------------------------
alter table public.sc_images
  add column archived    boolean not null default false,
  add column archived_at timestamptz,
  add column archived_by uuid references auth.users(id) on delete set null;

create index sc_images_archived_idx on public.sc_images(id) where archived;

-- 归档后的图片不再参与 15 天到期清理
create index sc_images_expiry_active on public.sc_images(expire_time) where not archived;

-- ------------------------------------------------------------
-- 3. 管理员判定（SECURITY DEFINER：绕过 sc_admins 的 RLS）
-- ------------------------------------------------------------
create function public.sc_is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.sc_admins a where a.user_id = auth.uid());
$$;
revoke all on function public.sc_is_admin() from public, anon;
grant execute on function public.sc_is_admin() to authenticated;

-- ------------------------------------------------------------
-- 4. 长期归档私有桶
-- ------------------------------------------------------------
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('study-archive','study-archive',false,5242880,array['image/jpeg','image/png','image/webp']);

-- ------------------------------------------------------------
-- 5. 存储策略
--    管理员：可读两个桶的全部对象（用于后台查看与生成签名链接）
--    普通用户：可读自己已归档的图片（归档不应让用户丢失自己的记录）
--    注意：普通用户对 study-images 的原有策略保持不变。
-- ------------------------------------------------------------
create policy sc_objects_admin_read on storage.objects for select to authenticated
using (
  bucket_id in ('study-images','study-archive')
  and public.sc_is_admin()
);

create policy sc_archive_owner_read on storage.objects for select to authenticated
using (
  bucket_id = 'study-archive'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and exists (
    select 1
    from public.sc_images i
    join public.sc_tasks t on t.id = i.task_id
    where i.path = name and i.user_id = (select auth.uid()) and not t.deleted
  )
);

-- ------------------------------------------------------------
-- 6. 管理查询：用户总览
-- ------------------------------------------------------------
create function public.sc_admin_users() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  select coalesce(jsonb_agg(x.value order by x.created_at desc), '[]'::jsonb) into result
  from (
    select u.created_at,
      jsonb_build_object(
        'userId',   u.id,
        'email',    u.email,
        'nickname', coalesce(u.raw_user_meta_data->>'nickname',''),
        'createdAt', to_char(u.created_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI'),
        'lastSignIn', to_char(u.last_sign_in_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI'),
        'goalCount',    (select count(*) from public.sc_goals g
                          where g.user_id=u.id and not g.deleted),
        'taskTotal',    (select count(*) from public.sc_tasks t
                          where t.user_id=u.id and not t.deleted),
        'taskFinished', (select count(*) from public.sc_tasks t
                          where t.user_id=u.id and not t.deleted and t.finish_status=1),
        'overdueCount', (select count(*) from public.sc_tasks t
                          where t.user_id=u.id and not t.deleted and t.finish_status=0
                            and t.task_date < cockpit_private.sc_today()),
        'completionRate', (select cockpit_private.sc_rate(
                             sum(t.weight) filter (where t.finish_status=1), sum(t.weight))
                           from public.sc_tasks t
                           where t.user_id=u.id and not t.deleted),
        'imageCount',   (select count(*) from public.sc_images i
                          where i.user_id=u.id and not i.archived and i.expire_time > now()),
        'archivedCount',(select count(*) from public.sc_images i
                          where i.user_id=u.id and i.archived),
        'weekCount',    (select count(*) from public.sc_weeks w where w.user_id=u.id)
      ) as value
    from auth.users u
  ) x;
  return result;
end $$;
revoke all on function public.sc_admin_users() from public, anon;
grant execute on function public.sc_admin_users() to authenticated;

-- ------------------------------------------------------------
-- 7. 管理查询：指定用户的任务明细（分页）
-- ------------------------------------------------------------
create function public.sc_admin_tasks(p_user uuid, p_page integer default 1, p_size integer default 50)
returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare result jsonb; p integer := coalesce(p_page,1); n integer := coalesce(p_size,50);
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  if p < 1 or n < 1 or n > 200 then raise exception '分页参数无效'; end if;
  select jsonb_build_object(
    'total', (select count(*) from cockpit_private.sc_task_view v where v.user_id = p_user),
    'page', p, 'size', n,
    'list', coalesce((
      select jsonb_agg(q.value order by q.task_date desc, q.id desc)
      from (
        select v.task_date, v.id, v.value
        from cockpit_private.sc_task_view v
        where v.user_id = p_user
        order by v.task_date desc, v.id desc
        limit n offset (p-1)*n
      ) q), '[]'::jsonb)
  ) into result;
  return result;
end $$;
revoke all on function public.sc_admin_tasks(uuid,integer,integer) from public, anon;
grant execute on function public.sc_admin_tasks(uuid,integer,integer) to authenticated;

-- ------------------------------------------------------------
-- 8. 管理查询：指定用户的图片清单（含归档状态）
--    返回 path 与 archived，前端据此选择桶后签发临时链接。
-- ------------------------------------------------------------
create function public.sc_admin_images(p_user uuid, p_include_expired boolean default false)
returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', i.id, 'taskId', i.task_id, 'originalName', i.original_name,
      'path', i.path, 'mimeType', i.mime_type, 'fileSize', i.file_size,
      'uploadTime', to_char(i.upload_time at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI'),
      'expireTime', to_char(i.expire_time at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI'),
      'archived', i.archived,
      'archivedAt', to_char(i.archived_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI'),
      'taskTitle', t.title
    ) order by i.upload_time desc), '[]'::jsonb) into result
  from public.sc_images i
  left join public.sc_tasks t on t.id = i.task_id
  where i.user_id = p_user
    and (p_include_expired or i.archived or i.expire_time > now());
  return result;
end $$;
revoke all on function public.sc_admin_images(uuid,boolean) from public, anon;
grant execute on function public.sc_admin_images(uuid,boolean) to authenticated;

-- ------------------------------------------------------------
-- 9. 管理员自助管理白名单（只能增删，不能看别人的身份以外的数据）
-- ------------------------------------------------------------
create function public.sc_admin_add(p_email text, p_note text default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare target uuid;
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  select id into target from auth.users where lower(email) = lower(btrim(p_email));
  if target is null then raise exception '该邮箱尚未注册'; end if;
  insert into public.sc_admins(user_id, note) values(target, p_note)
    on conflict (user_id) do update set note = excluded.note;
  return jsonb_build_object('userId', target, 'email', btrim(p_email));
end $$;
revoke all on function public.sc_admin_add(text,text) from public, anon;
grant execute on function public.sc_admin_add(text,text) to authenticated;

create function public.sc_admin_list() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'userId', a.user_id, 'email', u.email, 'note', a.note,
      'createdAt', to_char(a.created_at at time zone 'Asia/Shanghai','YYYY-MM-DD HH24:MI')
    ) order by a.created_at), '[]'::jsonb) into result
  from public.sc_admins a join auth.users u on u.id = a.user_id;
  return result;
end $$;
revoke all on function public.sc_admin_list() from public, anon;
grant execute on function public.sc_admin_list() to authenticated;

create function public.sc_admin_remove(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.sc_is_admin() then raise exception '需要管理员权限' using errcode='42501'; end if;
  if p_user = auth.uid() and (select count(*) from public.sc_admins) <= 1 then
    raise exception '不能移除最后一个管理员';
  end if;
  delete from public.sc_admins where user_id = p_user;
end $$;
revoke all on function public.sc_admin_remove(uuid) from public, anon;
grant execute on function public.sc_admin_remove(uuid) to authenticated;

commit;
