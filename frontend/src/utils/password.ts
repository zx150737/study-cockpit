/**
 * 客户端弱密码前置拦截。
 *
 * 背景：Supabase 的 leaked password protection（对接 HaveIBeenPwned）是 Pro 套餐专属，
 * 免费版无法开启。这里做一层等价的低成本替代：拦截最常见的弱密码与可预测模式，
 * 让「12345678」「abcd1234」这类即使满足长度与字符要求也不会被接受。
 *
 * 注意：这只是前置提示，真正的兜底仍是 Supabase 服务端的长度 + 字符类型强制。
 */

/** 常见弱密码（含中文用户高频密码与键盘/序列模式） */
const COMMON = new Set([
  '12345678', '123456789', '1234567890', '12345678910', '87654321',
  '11111111', '00000000', '88888888', '66666666', '12341234', '11223344',
  'password', 'password1', 'password123', 'passw0rd', 'p@ssw0rd',
  'qwerty123', 'qwertyuiop', 'qwerty12345', 'asdfghjk', 'asdfghjkl',
  'zxcvbnm1', '1qaz2wsx', '1q2w3e4r', 'qazwsxedc', 'abcd1234', 'abc12345',
  'a1234567', 'a123456789', 'admin123', 'admin1234', 'administrator',
  'letmein1', 'welcome1', 'iloveyou', 'monkey123', 'dragon123', 'sunshine1',
  'woaini1314', 'woaini520', '5201314', '5201314520', '1314520a',
]);

/** 是否为单调递增或递减序列，如 12345678 / abcdefgh / 87654321 */
function isSequential(s: string): boolean {
  if (s.length < 4) return false;
  let up = 0;
  let down = 0;
  for (let i = 1; i < s.length; i++) {
    const d = s.charCodeAt(i) - s.charCodeAt(i - 1);
    if (d === 1) up++;
    else if (d === -1) down++;
  }
  return up === s.length - 1 || down === s.length - 1;
}

/**
 * @returns 不合格原因；合格返回 null
 */
export function weakPasswordReason(password: string, email?: string): string | null {
  const p = password ?? '';
  const lower = p.toLowerCase();

  if (COMMON.has(lower)) return '这个密码太常见，容易被猜到，请换一个';
  if (/^(.)\1+$/.test(p)) return '密码不能是同一个字符重复';
  if (isSequential(lower)) return '密码不能是连续的数字或字母';
  if (/^\d+$/.test(p)) return '密码不能是纯数字';

  const local = (email ?? '').split('@')[0]?.toLowerCase() ?? '';
  if (local.length >= 4 && lower.includes(local)) return '密码不能包含你的邮箱账号';

  return null;
}

/** 0-3 的强度评分，仅用于可视化提示 */
export function passwordStrength(p: string): number {
  if (!p) return 0;
  let s = 0;
  if (p.length >= 8) s++;
  if (/[A-Za-z]/.test(p) && /\d/.test(p)) s++;
  if (p.length >= 10 && /[^A-Za-z0-9]/.test(p)) s++;
  return s;
}
