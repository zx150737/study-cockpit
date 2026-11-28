import { createClient } from 'npm:@supabase/supabase-js@2.119.2';

Deno.serve(async (req: Request) => {
  const secret = Deno.env.get('CLEANUP_SECRET');
  if (!secret || req.method !== 'POST' || req.headers.get('x-cleanup-secret') !== secret) return new Response('Unauthorized', { status: 401 });
  const admin = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });
  let count = 0;
  // Bounded execution; subsequent hourly runs resume remaining work.
  for (let batch = 0; batch < 5; batch++) {
    // 只清理未归档的到期图片；归档图片长期保留，由后台手动管理。
    const { data, error } = await admin.from('sc_images').select('id,path').eq('archived', false).lte('expire_time', new Date().toISOString()).order('id').limit(100);
    if (error) return new Response('Database read failed', { status: 500 });
    if (!data?.length) break;
    const removed = await admin.storage.from('study-images').remove(data.map(row => row.path));
    if (removed.error) return new Response('Storage cleanup failed; will retry', { status: 500 });
    const deleted = await admin.from('sc_images').delete().in('id', data.map(row => row.id));
    if (deleted.error) return new Response('Metadata cleanup failed; will retry', { status: 500 });
    count += data.length;
  }
  return Response.json({ removed: count });
});
