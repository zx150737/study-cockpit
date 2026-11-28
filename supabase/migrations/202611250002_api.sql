begin;
create function public.sc_read(action text, params jsonb default '{}') returns jsonb
language plpgsql set search_path = '' as $$
declare
  result jsonb; w date; g integer := (params->>'goalId')::integer;
  p integer := coalesce((params->>'page')::integer,1);
  n integer := coalesce((params->>'size')::integer,20);
  td date := cockpit_private.sc_today(); wk jsonb; base numeric; samples integer; streak integer;
begin
  if auth.uid() is null then raise exception '请先登录' using errcode='42501'; end if;
  if p<1 or p>1000000 or n<1 or n>100 then raise exception '分页参数无效'; end if;
  w := cockpit_private.sc_monday(coalesce((params->>'weekStart')::date,td));
  case action
  when 'goals' then
    select coalesce(jsonb_agg(x.value order by x.id),'[]') into result from (
      select a.id,jsonb_build_object('id',a.id,'name',a.name,'createdAt',a.created_at,
        'subjectCount',(select count(*) from public.sc_subjects where goal_id=a.id and not deleted),
        'completionRate',(select cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight))
          from public.sc_tasks where goal_id=a.id and not deleted and task_date<=td)) value
      from public.sc_goals a where not a.deleted
    ) x;
  when 'subjects' then
    if not exists(select 1 from public.sc_goals where id=g and not deleted) then raise exception '目标不存在'; end if;
    select coalesce(jsonb_agg(x.value order by x.id),'[]') into result from (
      select s.id,jsonb_build_object('id',s.id,'goalId',s.goal_id,'name',s.name,'defaultWeight',s.default_weight,
        'completionRate',(select cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight))
          from public.sc_tasks where subject_id=s.id and not deleted and task_date between w and w+6)) value
      from public.sc_subjects s where s.goal_id=g and not s.deleted
    ) x;
  when 'tasks' then
    with filtered as (
      select * from cockpit_private.sc_task_view t
      where (not(params ? 'taskDate') or task_date=(params->>'taskDate')::date)
        and (not(params ? 'weekStart') or task_date between w and w+6)
        and (g is null or goal_id=g)
        and (not(params ? 'subjectId') or subject_id=(params->>'subjectId')::integer)
        and (not(params ? 'finishStatus') or finish_status=(params->>'finishStatus')::integer)
        and (coalesce((params->>'overdue')::boolean,false)=false or (value->>'overdue')::boolean)
    ) select jsonb_build_object('list',coalesce((select jsonb_agg(value order by task_date desc,id desc)
      from (select * from filtered order by task_date desc,id desc limit n offset (p-1)*n) q),'[]'),
      'total',(select count(*) from filtered),'page',p,'size',n) into result;
  when 'weekly' then result := cockpit_private.sc_week(w,g);
  when 'overdue' then
    select jsonb_build_object('total',(select count(*) from cockpit_private.sc_task_view where (value->>'overdue')::boolean),
      'items',coalesce((select jsonb_agg(jsonb_build_object('taskId',id,'title',title,
        'goalName',value->>'goalName','subjectName',value->>'subjectName','taskDate',task_date,
        'deadline',value->>'deadline','overdueDays',greatest(0,td-task_date)) order by task_date,id)
        from (select * from cockpit_private.sc_task_view where (value->>'overdue')::boolean order by task_date,id limit 10) q),'[]')) into result;
  when 'weeks' then
    perform public.sc_settle();
    select jsonb_build_object('list',coalesce((select jsonb_agg(summary || jsonb_build_object('id',id) order by week_start desc)
      from (select * from public.sc_weeks order by week_start desc limit n offset (p-1)*n) q),'[]'),
      'total',(select count(*) from public.sc_weeks),'page',p,'size',n) into result;
  when 'weekDetail' then
    perform public.sc_settle();
    select jsonb_build_object('summary',(select summary || jsonb_build_object('id',id) from public.sc_weeks where week_start=w),
      'tasks',coalesce((select jsonb_agg(value order by task_date,id) from cockpit_private.sc_task_view where task_date between w and w+6),'[]')) into result;
  when 'overview' then
    perform public.sc_settle();
    wk := cockpit_private.sc_week(w);
    select round(avg((summary->>'completionRate')::numeric),2),count(*) into base,samples
      from (select summary from public.sc_weeks where week_start<w order by week_start desc limit 4) q;
    select coalesce(min(i),366) into streak from generate_series(0,365) i where not exists(
      select 1 from public.sc_tasks where not deleted and finish_status=1
      and task_date=(case when exists(select 1 from public.sc_tasks where not deleted and task_date=td and finish_status=1)
        then td else td-1 end)-i);
    select jsonb_build_object(
      'todayRate',cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight)),
      'hasTask',count(*)>0,'streakDays',streak,'backlog',public.sc_read('overdue'),
      'weeklyTrend',(select jsonb_agg(case when (v->>'date')::date>td then v || '{"rate":null,"hasTask":false}'::jsonb else v end order by v->>'date') from jsonb_array_elements(wk->'dailyTrend') v),
      'weeklyAvgRate',(select coalesce(round(avg((v->>'rate')::numeric),2),0) from jsonb_array_elements(wk->'dailyTrend') v where (v->>'date')::date<=td and (v->>'hasTask')::boolean),
      'baseline',jsonb_build_object('available',samples>0,'baselineRate',base,'currentRate',wk->'completionRate',
        'diff',round((wk->>'completionRate')::numeric-base,2),'sampleWeeks',samples,
        'improvementPct',case when samples=0 then null when base=0 then 100 else round(((wk->>'completionRate')::numeric-base)/base*100,2) end),
      'goalCards',(select coalesce(jsonb_agg(jsonb_build_object('goalId',a.id,'name',a.name,
        'completionRate',(select cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight)) from public.sc_tasks where not deleted and goal_id=a.id and task_date<=td),
        'weekRate',(select cockpit_private.sc_rate(sum(weight) filter(where finish_status=1),sum(weight)) from public.sc_tasks where not deleted and goal_id=a.id and task_date between w and w+6),
        'taskTotal',(select count(*) from public.sc_tasks where not deleted and goal_id=a.id and task_date<=td)) order by a.id),'[]') from public.sc_goals a where not a.deleted)
    ) into result from public.sc_tasks where not deleted and task_date=td;
  else raise exception '未知查询';
  end case;
  return result;
end $$;

create function public.sc_write(action text, payload jsonb) returns jsonb
language plpgsql set search_path = '' as $$
declare
  k integer := (payload->>'id')::integer;
  g integer := (payload->>'goalId')::integer;
  s integer := (payload->>'subjectId')::integer;
  task_weight numeric; result jsonb;
begin
  if auth.uid() is null then raise exception '请先登录' using errcode='42501'; end if;
  if action in ('createSubject','updateSubject','createTask','updateTask') then
    if not exists(select 1 from public.sc_goals where id=g and not deleted) then raise exception '目标不存在'; end if;
  end if;
  if action in ('createTask','updateTask') then
    select default_weight into task_weight from public.sc_subjects where id=s and goal_id=g and not deleted;
    if not found then raise exception '科目不属于所选目标或已删除'; end if;
    task_weight := coalesce((payload->>'weight')::numeric,task_weight);
  end if;
  case action
  when 'createGoal' then
    insert into public.sc_goals(name) values(btrim(payload->>'name')) returning id into k;
    return jsonb_build_object('id',k,'name',btrim(payload->>'name'),'completionRate',0,'subjectCount',0);
  when 'updateGoal' then update public.sc_goals set name=btrim(payload->>'name') where id=k and not deleted;
  when 'deleteGoal' then update public.sc_goals set deleted=true where id=k and not deleted;
  when 'createSubject' then
    insert into public.sc_subjects(goal_id,name,default_weight) values(g,btrim(payload->>'name'),(payload->>'defaultWeight')::numeric) returning id into k;
    return jsonb_build_object('id',k,'goalId',g,'name',btrim(payload->>'name'),'defaultWeight',payload->'defaultWeight','completionRate',0);
  when 'updateSubject' then update public.sc_subjects set goal_id=g,name=btrim(payload->>'name'),default_weight=(payload->>'defaultWeight')::numeric where id=k and not deleted;
  when 'deleteSubject' then update public.sc_subjects set deleted=true where id=k and not deleted;
  when 'createTask' then
    insert into public.sc_tasks(goal_id,subject_id,title,weight,task_date,deadline,mastery,review_note)
    values(g,s,btrim(payload->>'title'),task_weight,(payload->>'taskDate')::date,nullif(payload->>'deadline','')::timestamp,(payload->>'mastery')::smallint,payload->>'reviewNote') returning id into k;
    select value into result from cockpit_private.sc_task_view where id=k; return result;
  when 'updateTask' then
    update public.sc_tasks set goal_id=g,subject_id=s,title=btrim(payload->>'title'),weight=task_weight,
      task_date=(payload->>'taskDate')::date,deadline=nullif(payload->>'deadline','')::timestamp,
      mastery=(payload->>'mastery')::smallint,review_note=payload->>'reviewNote' where id=k and not deleted;
  when 'deleteTask' then update public.sc_tasks set deleted=true where id=k and not deleted;
  when 'finishTask' then update public.sc_tasks set finish_status=(payload->>'finishStatus')::smallint where id=k and not deleted;
  else raise exception '未知操作';
  end case;
  if not found then raise exception '记录不存在或已删除'; end if;
  return null;
end $$;
revoke all on function public.sc_read(text,jsonb), public.sc_write(text,jsonb) from public, anon;
grant execute on function public.sc_read(text,jsonb), public.sc_write(text,jsonb) to authenticated;
commit;
