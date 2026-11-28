import { createClient, type SupabaseClient, type User } from '@supabase/supabase-js';
import type { UserVO } from '@/types';

export const isCloud = import.meta.env.VITE_BACKEND === 'supabase';
let client: SupabaseClient | undefined;
export function cloudClient(): SupabaseClient {
  if (!client) {
    const url = import.meta.env.VITE_SUPABASE_URL;
    const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;
    if (!url || !key) throw new Error('工作台服务尚未配置，请联系管理员');
    client = createClient(url, key);
  }
  return client;
}
export function userView(user: User): UserVO {
  return { id: user.id, username: user.email || '', nickname: user.user_metadata.nickname || undefined };
}

/** 丢弃当前客户端实例；下次访问会按当前（已清理的）存储重新创建 */
export function resetCloudClient(): void {
  client = undefined;
}

const NEED_PURGE = 'sc-need-purge';

/**
 * 页面启动最早期调用：若上一次加载标记了「会话需清理」，在创建任何客户端之前先把存储清干净。
 * 这样才能彻底避开上一页残留客户端在卸载前把脏会话写回存储的问题。
 */
export function earlyPurgeIfNeeded(): void {
  try {
    if (!sessionStorage.getItem(NEED_PURGE)) return;
    sessionStorage.removeItem(NEED_PURGE);
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (key && /^sb-.*-auth-token$/.test(key)) localStorage.removeItem(key);
    }
  } catch {
    // 存储不可用时忽略
  }
}

/**
 * 清掉浏览器里已经失效的会话。
 * 场景：账号被管理员删除、或服务端已吊销会话时，localStorage 会残留过期会话，
 * 每次加载都拿它去刷新并失败，导致用户"登录后被反复踢回登录页"。
 *
 * 关键点：只删 localStorage 不够 —— Supabase 客户端在内存里还持有旧会话，
 * 后续任何一次保存都会把脏会话写回存储。因此清理后必须重建客户端实例。
 */
async function purgeBrokenSession(sb: SupabaseClient): Promise<void> {
  // 先停掉自动刷新：否则旧客户端会周期性把内存里的脏会话写回存储
  try {
    sb.auth.stopAutoRefresh();
  } catch {
    // 忽略
  }
  try {
    await sb.auth.signOut({ scope: 'local' });
  } catch {
    // 服务端拒绝也继续清理本地
  }
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (key && /^sb-.*-auth-token$/.test(key)) localStorage.removeItem(key);
    }
  } catch {
    // 存储不可用时忽略
  }
  // 标记：下次页面启动时再清一次，防止本页残留客户端把脏会话写回
  try {
    sessionStorage.setItem(NEED_PURGE, '1');
  } catch {
    // 忽略
  }
  resetCloudClient();
}

export async function cloudCurrentUser(): Promise<UserVO | null> {
  const sb = cloudClient();
  let failed = false;
  try {
    const { data, error } = await sb.auth.getUser();
    if (!error && data.user) return userView(data.user);
    failed = true;
  } catch {
    failed = true;
  }
  if (!failed) return null;

  // 区分「正常未登录」与「有会话但服务端已失效」。
  // 后者常见于账号被删除：localStorage 里还留着会话，每次加载都会失败。
  let hasSession = false;
  try {
    hasSession = Boolean((await sb.auth.getSession()).data.session);
  } catch {
    hasSession = false;
  }
  if (!hasSession) return null;

  await purgeBrokenSession(sb);
  return null;
}
