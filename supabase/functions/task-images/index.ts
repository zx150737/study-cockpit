import { createClient } from 'npm:@supabase/supabase-js@2.119.2';

const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info', 'Access-Control-Allow-Methods': 'POST, OPTIONS' };
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, 'Content-Type': 'application/json' } });

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
  if (req.method !== 'POST') return json({ error: '请求方法不支持' }, 405);
  const url = Deno.env.get('SUPABASE_URL')!;
  const authorization = req.headers.get('Authorization') ?? '';
  const client = createClient(url, Deno.env.get('SUPABASE_ANON_KEY')!, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
  const { data: { user }, error: authError } = await client.auth.getUser();
  if (authError || !user) return json({ error: '请先登录' }, 401);
  const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });
  try {
    if (req.headers.get('Content-Type')?.includes('application/json')) {
      const body = await req.json();
      if (body.operation !== 'delete' || !Number.isSafeInteger(body.id)) return json({ error: '参数无效' }, 400);
      const { data: row, error } = await client.from('sc_images').select('id,path').eq('id', body.id).single();
      if (error || !row) return json({ error: '图片不存在' }, 404);
      const removed = await admin.storage.from('study-images').remove([row.path]);
      if (removed.error) throw removed.error;
      const deleted = await admin.from('sc_images').delete().eq('id', row.id).eq('user_id', user.id);
      if (deleted.error) throw deleted.error;
      return json({ ok: true });
    }
    if (Number(req.headers.get('Content-Length') || 0) > 6 * 1024 * 1024) return json({ error: '文件超过 5MB' }, 400);
    const form = await req.formData();
    const file = form.get('file');
    const taskId = Number(form.get('taskId'));
    if (!(file instanceof File) || file.size < 1 || file.size > 5 * 1024 * 1024 || !Number.isSafeInteger(taskId)) return json({ error: '请选择不超过 5MB 的图片' }, 400);
    const task = await client.from('sc_tasks').select('id').eq('id', taskId).eq('deleted', false).single();
    if (task.error || !task.data) return json({ error: '任务不存在' }, 404);
    const bytes = new Uint8Array(await file.arrayBuffer());
    const signature = Array.from(bytes.slice(0, 12));
    let ext = '', mime = '';
    if (signature[0] === 255 && signature[1] === 216 && signature[2] === 255) { ext = 'jpg'; mime = 'image/jpeg'; }
    else if ([137,80,78,71,13,10,26,10].every((b,i) => signature[i] === b)) { ext = 'png'; mime = 'image/png'; }
    else if (String.fromCharCode(...signature.slice(0,4)) === 'RIFF' && String.fromCharCode(...signature.slice(8,12)) === 'WEBP') { ext = 'webp'; mime = 'image/webp'; }
    if (!ext || !/\.(jpg|jpeg|png|webp)$/i.test(file.name)) return json({ error: '仅支持真实的 jpg/png/webp 图片' }, 400);
    const path = `${user.id}/${taskId}/${crypto.randomUUID()}.${ext}`;
    const metadata = await admin.from('sc_images').insert({ user_id: user.id, task_id: taskId, path, original_name: file.name.slice(0,255), mime_type: mime, file_size: file.size }).select('id').single();
    if (metadata.error) throw metadata.error;
    const uploaded = await admin.storage.from('study-images').upload(path, bytes, { contentType: mime, upsert: false });
    if (uploaded.error) {
      await admin.from('sc_images').delete().eq('id', metadata.data.id).eq('user_id', user.id);
      throw uploaded.error;
    }
    return json({ id: metadata.data.id });
  } catch {
    return json({ error: '图片操作失败，请稍后重试' }, 500);
  }
});
