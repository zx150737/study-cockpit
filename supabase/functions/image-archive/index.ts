// 图片长期归档管理（管理员专用）
// - archive  ：把图片从 15 天临时桶 study-images 复制到长期桶 study-archive，并标记 archived
// - unarchive：放回 study-images，重新开始 15 天倒计时
// - delete   ：彻底删除对象与记录
// 鉴权：调用者必须登录且存在于 public.sc_admins 白名单。
import { createClient } from 'npm:@supabase/supabase-js@2.119.2';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, 'Content-Type': 'application/json' } });

const HOT_BUCKET = 'study-images';
const COLD_BUCKET = 'study-archive';

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
  if (req.method !== 'POST') return json({ error: '请求方法不支持' }, 405);

  const url = Deno.env.get('SUPABASE_URL')!;
  const authorization = req.headers.get('Authorization') ?? '';
  const client = createClient(url, Deno.env.get('SUPABASE_ANON_KEY')!, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });

  const {
    data: { user },
    error: authError,
  } = await client.auth.getUser();
  if (authError || !user) return json({ error: '请先登录' }, 401);

  const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
    auth: { persistSession: false },
  });

  // 管理员白名单校验：用服务端身份读表，避免依赖 RLS。
  const member = await admin.from('sc_admins').select('user_id').eq('user_id', user.id).maybeSingle();
  if (member.error) return json({ error: '权限校验失败' }, 500);
  if (!member.data) return json({ error: '需要管理员权限' }, 403);

  let body: { operation?: string; id?: number };
  try {
    body = await req.json();
  } catch {
    return json({ error: '参数无效' }, 400);
  }
  const operation = body.operation;
  const id = body.id;
  if (!Number.isSafeInteger(id)) return json({ error: '参数无效' }, 400);

  try {
    const { data: row, error: findError } = await admin
      .from('sc_images')
      .select('id,path,user_id,archived,expire_time')
      .eq('id', id as number)
      .maybeSingle();
    if (findError) throw findError;
    if (!row) return json({ error: '图片不存在' }, 404);

    if (operation === 'archive') {
      if (row.archived) return json({ ok: true, already: true });

      const downloaded = await admin.storage.from(HOT_BUCKET).download(row.path);
      if (downloaded.error || !downloaded.data) return json({ error: '读取原图失败' }, 500);
      const bytes = new Uint8Array(await downloaded.data.arrayBuffer());

      const uploaded = await admin.storage
        .from(COLD_BUCKET)
        .upload(row.path, bytes, { contentType: downloaded.data.type || 'application/octet-stream', upsert: true });
      if (uploaded.error) return json({ error: '写入归档桶失败' }, 500);

      // 先确认归档副本存在，再删原图，避免中途失败导致图片丢失。
      const cold = await admin.storage.from(COLD_BUCKET).list(row.path.split('/').slice(0, -1).join('/'), {
        search: row.path.split('/').pop()!,
      });
      if (cold.error || !cold.data?.length) return json({ error: '归档校验失败，未删除原图' }, 500);

      const removed = await admin.storage.from(HOT_BUCKET).remove([row.path]);
      if (removed.error) return json({ error: '清理原图失败' }, 500);

      const updated = await admin
        .from('sc_images')
        .update({ archived: true, archived_at: new Date().toISOString(), archived_by: user.id })
        .eq('id', row.id);
      if (updated.error) throw updated.error;
      return json({ ok: true, archived: true });
    }

    if (operation === 'unarchive') {
      if (!row.archived) return json({ ok: true, already: true });

      const downloaded = await admin.storage.from(COLD_BUCKET).download(row.path);
      if (downloaded.error || !downloaded.data) return json({ error: '读取归档图片失败' }, 500);
      const bytes = new Uint8Array(await downloaded.data.arrayBuffer());

      const uploaded = await admin.storage
        .from(HOT_BUCKET)
        .upload(row.path, bytes, { contentType: downloaded.data.type || 'application/octet-stream', upsert: true });
      if (uploaded.error || !uploaded.data) return json({ error: '写回临时桶失败' }, 500);

      const removed = await admin.storage.from(COLD_BUCKET).remove([row.path]);
      if (removed.error) return json({ error: '清理归档副本失败' }, 500);

      const expire = new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString();
      const updated = await admin
        .from('sc_images')
        .update({ archived: false, archived_at: null, archived_by: null, expire_time: expire })
        .eq('id', row.id);
      if (updated.error) throw updated.error;
      return json({ ok: true, archived: false, expireTime: expire });
    }

    if (operation === 'delete') {
      const bucket = row.archived ? COLD_BUCKET : HOT_BUCKET;
      const removed = await admin.storage.from(bucket).remove([row.path]);
      if (removed.error) return json({ error: '删除文件失败' }, 500);
      const deleted = await admin.from('sc_images').delete().eq('id', row.id);
      if (deleted.error) throw deleted.error;
      return json({ ok: true });
    }

    return json({ error: '未知操作' }, 400);
  } catch {
    return json({ error: '图片管理操作失败，请稍后重试' }, 500);
  }
});
