-- AI 学习助手：提问会话、结构化答复、错题复习卡（间隔重复）。
-- 只存文本；模型调用发生在边缘函数 ai-tutor，密钥不进入数据库、不进入前端。
begin;

create table public.sc_ai_threads (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  subject_id integer,
  mode text not null default 'explain' check (mode in ('explain', 'practice')),
  title text not null check (length(btrim(title)) between 1 and 80),
  question text not null check (length(btrim(question)) between 1 and 4000),
  reply jsonb check (reply is null or jsonb_typeof(reply) = 'object'),
  total_tokens integer not null default 0 check (total_tokens >= 0),
  deleted boolean not null default false,
  created_at timestamptz not null default now(),
  replied_at timestamptz,
  unique(id, user_id),
  foreign key(subject_id, user_id) references public.sc_subjects(id, user_id)
);

create table public.sc_ai_cards (
  id integer generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  thread_id integer,
  front text not null check (length(btrim(front)) between 1 and 2000),
  back text not null check (length(btrim(back)) between 1 and 4000),
  subject_name text check (length(subject_name) <= 64),
  box smallint not null default 1 check (box between 1 and 5),
  due_date date not null default ((now() at time zone 'Asia/Shanghai')::date),
  review_count integer not null default 0 check (review_count >= 0),
  correct_count integer not null default 0 check (correct_count >= 0),
  last_reviewed_at timestamptz,
  deleted boolean not null default false,
  created_at timestamptz not null default now(),
  unique(id, user_id),
  foreign key(thread_id, user_id) references public.sc_ai_threads(id, user_id)
);

create index sc_ai_threads_owner on public.sc_ai_threads(user_id, id desc) where not deleted;
create index sc_ai_cards_owner_due on public.sc_ai_cards(user_id, due_date, id) where not deleted;
create unique index sc_ai_cards_thread_once on public.sc_ai_cards(user_id, thread_id)
  where not deleted and thread_id is not null;

alter table public.sc_ai_threads enable row level security;
alter table public.sc_ai_cards enable row level security;
create policy own_ai_threads on public.sc_ai_threads to authenticated
  using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));
create policy own_ai_cards on public.sc_ai_cards to authenticated
  using(user_id = (select auth.uid())) with check(user_id = (select auth.uid()));

revoke all on public.sc_ai_threads, public.sc_ai_cards from anon, authenticated;
grant select, insert, update on public.sc_ai_threads, public.sc_ai_cards to authenticated;
grant all on public.sc_ai_threads, public.sc_ai_cards to service_role;
grant usage on sequence public.sc_ai_threads_id_seq, public.sc_ai_cards_id_seq to authenticated, service_role;

-- 答复与 token 计数只能由边缘函数（service_role）写入，客户端伪造一律拒绝。
create function cockpit_private.sc_ai_guard() returns trigger language plpgsql set search_path = '' as $$
begin
  if tg_op = 'UPDATE' then
    if new.id <> old.id or new.user_id <> old.user_id then raise exception '不能修改数据归属'; end if;
    new.created_at := old.created_at;
    if old.deleted and not new.deleted then raise exception '不能恢复已删除记录'; end if;
    if tg_table_name = 'sc_ai_threads' then
      if new.reply is distinct from old.reply and current_user not in ('service_role', 'postgres')
      then raise exception '答复只能由服务端写入'; end if;
      if new.total_tokens < old.total_tokens then new.total_tokens := old.total_tokens; end if;
    end if;
  else
    new.created_at := now();
    if tg_table_name = 'sc_ai_threads' then
      if new.reply is not null and current_user not in ('service_role', 'postgres')
      then raise exception '答复只能由服务端写入'; end if;
    else
      if new.thread_id is not null and not exists(
        select 1 from public.sc_ai_threads t
        where t.id = new.thread_id and t.user_id = new.user_id and not t.deleted)
      then raise exception '提问不存在'; end if;
      new.review_count := 0; new.correct_count := 0; new.last_reviewed_at := null;
      new.due_date := (now() at time zone 'Asia/Shanghai')::date;
    end if;
  end if;
  return new;
end $$;
create trigger sc_ai_threads_guard before insert or update on public.sc_ai_threads for each row execute function cockpit_private.sc_ai_guard();
create trigger sc_ai_cards_guard before insert or update on public.sc_ai_cards for each row execute function cockpit_private.sc_ai_guard();

-- 把结构化答复压成错题卡背面的纯文本。
create function cockpit_private.sc_ai_card_back(reply jsonb) returns text
language plpgsql stable set search_path = '' as $$
declare steps text; pitfalls text; out text;
begin
  steps := (select string_agg(format('%s. %s：%s', o, x->>'title', x->>'detail'), E'\n' order by o)
            from jsonb_array_elements(coalesce(reply->'steps', '[]'::jsonb)) with ordinality as s(x, o));
  pitfalls := (select string_agg(v, '；') from jsonb_array_elements_text(coalesce(reply->'pitfalls', '[]'::jsonb)) as t(v));
  out := concat_ws(E'\n',
    nullif('答案：' || coalesce(reply->>'answer', ''), '答案：'),
    nullif(steps, ''),
    nullif('易错：' || coalesce(pitfalls, ''), '易错：'));
  return nullif(btrim(out), '');
end $$;

create function public.sc_ai_read(action text, params jsonb default '{}') returns jsonb
language plpgsql set search_path = '' as $$
declare
  uid uuid := auth.uid();
  p integer := coalesce((params->>'page')::integer, 1);
  n integer := coalesce((params->>'size')::integer, 20);
  k integer := (params->>'id')::integer;
  due_only boolean := coalesce((params->>'dueOnly')::boolean, false);
  td date := cockpit_private.sc_today();
  result jsonb;
begin
  if uid is null then raise exception '请先登录' using errcode = '42501'; end if;
  if p < 1 or p > 1000000 or n < 1 or n > 100 then raise exception '分页参数无效'; end if;
  case action
  when 'threads' then
    select jsonb_build_object(
      'list', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', q.id, 'title', q.title, 'mode', q.mode, 'subjectId', q.subject_id,
          'subjectName', case when s.deleted then '已删除科目' else s.name end,
          'questionPreview', left(q.question, 80), 'hasReply', q.reply is not null,
          'createdAt', to_char(q.created_at at time zone 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS')
        ) order by q.id desc)
        from (select * from public.sc_ai_threads where user_id = uid and not deleted
              order by id desc limit n offset (p - 1) * n) q
        left join public.sc_subjects s on s.id = q.subject_id), '[]'),
      'total', (select count(*) from public.sc_ai_threads where user_id = uid and not deleted),
      'page', p, 'size', n) into result;
  when 'thread' then
    select jsonb_build_object(
      'id', t.id, 'title', t.title, 'mode', t.mode, 'subjectId', t.subject_id,
      'subjectName', case when s.deleted then '已删除科目' else s.name end,
      'question', t.question, 'reply', t.reply,
      'createdAt', to_char(t.created_at at time zone 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS'),
      'repliedAt', to_char(t.replied_at at time zone 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS'))
    into result
    from public.sc_ai_threads t left join public.sc_subjects s on s.id = t.subject_id
    where t.id = k and t.user_id = uid and not t.deleted;
    if result is null then raise exception '提问不存在或已删除'; end if;
  when 'cards' then
    select jsonb_build_object(
      'list', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', q.id, 'threadId', q.thread_id, 'front', q.front, 'back', q.back,
          'subjectName', q.subject_name, 'box', q.box,
          'dueDate', to_char(q.due_date, 'YYYY-MM-DD'),
          'reviewCount', q.review_count, 'correctCount', q.correct_count,
          'lastReviewedAt', to_char(q.last_reviewed_at at time zone 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS'),
          'overdue', q.due_date < td)
        order by q.due_date, q.id)
        from (select * from public.sc_ai_cards
              where user_id = uid and not deleted and (not due_only or due_date <= td)
              order by due_date, id limit n offset (p - 1) * n) q), '[]'),
      'total', (select count(*) from public.sc_ai_cards
                where user_id = uid and not deleted and (not due_only or due_date <= td)),
      'dueTotal', (select count(*) from public.sc_ai_cards
                   where user_id = uid and not deleted and due_date <= td),
      'page', p, 'size', n) into result;
  when 'stats' then
    select jsonb_build_object(
      'threadTotal', (select count(*) from public.sc_ai_threads where user_id = uid and not deleted),
      'threadsToday', (select count(*) from public.sc_ai_threads
                       where user_id = uid and not deleted
                       and created_at >= (td::timestamp at time zone 'Asia/Shanghai')),
      'cardTotal', (select count(*) from public.sc_ai_cards where user_id = uid and not deleted),
      'dueTotal', (select count(*) from public.sc_ai_cards
                   where user_id = uid and not deleted and due_date <= td),
      'mastered', (select count(*) from public.sc_ai_cards
                   where user_id = uid and not deleted and box >= 5),
      'reviewedToday', (select count(*) from public.sc_ai_cards
                        where user_id = uid and not deleted
                        and last_reviewed_at >= (td::timestamp at time zone 'Asia/Shanghai')),
      'accuracy', (select coalesce(round(sum(correct_count)::numeric / nullif(sum(review_count), 0) * 100, 1), 0)
                   from public.sc_ai_cards where user_id = uid and not deleted)) into result;
  else raise exception '未知查询';
  end case;
  return result;
end $$;

create function public.sc_ai_write(action text, payload jsonb) returns jsonb
language plpgsql set search_path = '' as $$
declare
  uid uuid := auth.uid();
  k integer := (payload->>'id')::integer;
  tid integer := (payload->>'threadId')::integer;
  sid integer := nullif(payload->>'subjectId', '')::integer;
  mode_in text := coalesce(nullif(btrim(payload->>'mode'), ''), 'explain');
  g integer := (payload->>'grade')::integer;
  b smallint; nb smallint; iv integer; result jsonb;
  q_text text; r_reply jsonb; back_text text; s_name text;
begin
  if uid is null then raise exception '请先登录' using errcode = '42501'; end if;
  case action
  when 'createThread' then
    if length(btrim(coalesce(payload->>'question', ''))) < 1 then raise exception '请输入题目内容'; end if;
    if mode_in not in ('explain', 'practice') then raise exception '模式取值无效'; end if;
    if sid is not null and not exists(
      select 1 from public.sc_subjects where id = sid and user_id = uid and not deleted)
    then raise exception '科目不存在或已删除'; end if;
    insert into public.sc_ai_threads(user_id, subject_id, mode, title, question)
    values(uid, sid, mode_in,
      left(btrim(coalesce(nullif(btrim(payload->>'title'), ''), payload->>'question')), 80),
      btrim(payload->>'question'))
    returning id into k;
    select jsonb_build_object('id', t.id, 'title', t.title, 'mode', t.mode, 'subjectId', t.subject_id,
      'subjectName', s.name, 'question', t.question, 'reply', t.reply,
      'createdAt', to_char(t.created_at at time zone 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS'),
      'repliedAt', null)
    into result
    from public.sc_ai_threads t left join public.sc_subjects s on s.id = t.subject_id
    where t.id = k;
    return result;
  when 'deleteThread' then
    update public.sc_ai_threads set deleted = true where id = k and user_id = uid and not deleted;
    if not found then raise exception '提问不存在或已删除'; end if;
    update public.sc_ai_cards set deleted = true where thread_id = k and user_id = uid and not deleted;
    return null;
  when 'addCard' then
    select t.question, t.reply, t.subject_id into q_text, r_reply, sid
    from public.sc_ai_threads t where t.id = tid and t.user_id = uid and not t.deleted;
    if q_text is null then raise exception '提问不存在或已删除'; end if;
    if r_reply is null then raise exception '该提问还没有 AI 答复，不能加入错题本'; end if;
    if exists(select 1 from public.sc_ai_cards where user_id = uid and thread_id = tid and not deleted)
    then raise exception '这个问题已经在错题本里了'; end if;
    back_text := coalesce(cockpit_private.sc_ai_card_back(r_reply), left(r_reply::text, 4000));
    s_name := (select name from public.sc_subjects where id = sid and user_id = uid);
    insert into public.sc_ai_cards(user_id, thread_id, front, back, subject_name)
    values(uid, tid, left(q_text, 2000), left(back_text, 4000), left(s_name, 64))
    returning id into k;
    return jsonb_build_object('id', k);
  when 'createCard' then
    if length(btrim(coalesce(payload->>'front', ''))) < 1 then raise exception '请输入题面'; end if;
    if length(btrim(coalesce(payload->>'back', ''))) < 1 then raise exception '请输入答案'; end if;
    insert into public.sc_ai_cards(user_id, front, back, subject_name)
    values(uid, left(btrim(payload->>'front'), 2000), left(btrim(payload->>'back'), 4000),
      left(nullif(btrim(payload->>'subjectName'), ''), 64))
    returning id into k;
    return jsonb_build_object('id', k);
  when 'deleteCard' then
    update public.sc_ai_cards set deleted = true where id = k and user_id = uid and not deleted;
    if not found then raise exception '复习卡不存在或已删除'; end if;
    return null;
  when 'reviewCard' then
    select box into b from public.sc_ai_cards where id = k and user_id = uid and not deleted;
    if not found then raise exception '复习卡不存在或已删除'; end if;
    if g is null or g < 0 or g > 2 then raise exception '自评取值无效'; end if;
    nb := case when g = 0 then 1 when g = 1 then greatest(1, b) else least(5, b + 1) end;
    iv := (array[1, 2, 4, 8, 16])[nb];
    -- 评为「忘了」当天重排，当场再练一次；其余按盒子间隔推后。
    update public.sc_ai_cards
    set box = nb, due_date = case when g = 0 then cockpit_private.sc_today() else cockpit_private.sc_today() + iv end,
        review_count = review_count + 1,
        correct_count = correct_count + case when g >= 1 then 1 else 0 end,
        last_reviewed_at = now()
    where id = k and user_id = uid and not deleted;
    return jsonb_build_object('id', k, 'box', nb,
      'dueDate', to_char(case when g = 0 then cockpit_private.sc_today()
                              else cockpit_private.sc_today() + iv end, 'YYYY-MM-DD'));
  else raise exception '未知操作';
  end case;
end $$;

revoke all on function public.sc_ai_read(text, jsonb), public.sc_ai_write(text, jsonb) from public, anon;
grant execute on function public.sc_ai_read(text, jsonb), public.sc_ai_write(text, jsonb) to authenticated;
revoke all on function cockpit_private.sc_ai_card_back(jsonb) from public, anon;
grant execute on function cockpit_private.sc_ai_card_back(jsonb) to authenticated, service_role;
commit;
