import { loadEnv, build } from 'vite';

const env = { ...loadEnv('production', process.cwd(), 'VITE_'), ...process.env };
const url = env.VITE_SUPABASE_URL || '';
const key = env.VITE_SUPABASE_PUBLISHABLE_KEY || '';
if (!/^https:\/\/[a-z0-9-]+\.supabase\.co\/?$/.test(url)) {
  throw new Error('请填写真实的 VITE_SUPABASE_URL（https://项目编号.supabase.co）后再构建。');
}
if (!/^sb_publishable_[A-Za-z0-9_-]+$/.test(key)) {
  throw new Error('请填写 VITE_SUPABASE_PUBLISHABLE_KEY；前端仅接受 sb_publishable_ 公钥，禁止使用 Secret/service_role 密钥。');
}
process.env.VITE_BACKEND = 'supabase';
process.env.VITE_SUPABASE_URL = url.replace(/\/$/, '');
process.env.VITE_SUPABASE_PUBLISHABLE_KEY = key;
await build();
console.log('免费版构建完成：frontend/dist，可上传到 Cloudflare Pages。');
