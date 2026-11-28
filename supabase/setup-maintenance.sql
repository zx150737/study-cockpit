-- 第 4 步：完成迁移及 Edge Function 部署后，在 Supabase SQL Editor 执行。
-- 先在 Integrations 启用 Cron、pg_net、Vault。
-- CLEANUP_SECRET 与 Edge Functions Secrets 中的同名值必须一致。
-- 不要将填入真实密钥的脚本提交到 GitHub 或分享。
do $$
declare
  project_url text := 'https://替换为项目编号.supabase.co';
  cleanup_secret text := '替换为至少32字符的随机字符串';
begin
  if project_url like '%替换%' or length(cleanup_secret)<32 or cleanup_secret like '%替换%' then
    raise exception '请先填写 Project URL 和 CLEANUP_SECRET';
  end if;
  if exists(select 1 from vault.secrets where name in ('cockpit_project_url','cockpit_cleanup_secret')) then
    raise exception '定时任务密钥已存在，请在 Vault 更新，不要重复初始化';
  end if;
  perform vault.create_secret(project_url,'cockpit_project_url');
  perform vault.create_secret(cleanup_secret,'cockpit_cleanup_secret');
end $$;

-- UTC Sunday 16:05 = Shanghai Monday 00:05; missed historical weeks are caught up.
select cron.schedule('cockpit-weekly-settlement','5 16 * * 0',
  'select cockpit_private.sc_settle_all()');

-- Metadata is removed only after the Storage API confirms physical deletion.
select cron.schedule('cockpit-image-cleanup','17 * * * *', $job$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name='cockpit_project_url') || '/functions/v1/cleanup-images',
    headers := jsonb_build_object('Content-Type','application/json','x-cleanup-secret',
      (select decrypted_secret from vault.decrypted_secrets where name='cockpit_cleanup_secret')),
    body := '{}'::jsonb
  );
$job$);
