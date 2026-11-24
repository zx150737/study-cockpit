/**
 * UTC+8 时间工具。
 * 需求 4：所有时间统一 UTC+8，周一起始、周日结算。
 * 前端不使用浏览器本地时区计算「今天/周一」，避免用户在其它时区时周次错乱。
 */

/** 以 Asia/Shanghai 取今天的 yyyy-MM-dd */
export function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const pick = (type: string) => parts.find((p) => p.type === type)?.value || '';
  return `${pick('year')}-${pick('month')}-${pick('day')}`;
}

/** 用 UTC 日历运算，避免本地时区参与 */
function utcDate(date: string): Date {
  return new Date(date + 'T00:00:00Z');
}

/** 所在周的周一（周一为首日） */
export function mondayOf(date: string): string {
  const d = utcDate(date);
  const dow = d.getUTCDay(); // 0=周日
  const diff = dow === 0 ? -6 : 1 - dow;
  d.setUTCDate(d.getUTCDate() + diff);
  return d.toISOString().slice(0, 10);
}

export function sundayOf(date: string): string {
  return addDays(mondayOf(date), 6);
}

export function addDays(date: string, days: number): string {
  const d = utcDate(date);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** 生成本周 7 天标签（用于图表 X 轴） */
export function weekLabels(monday: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i).slice(5));
}

/** 把后端 yyyy-MM-dd HH:mm:ss 转成 el-date-picker 需要的 yyyy-MM-ddTHH:mm:ss */
export function toPickerValue(value?: string): string {
  if (!value) return '';
  return value.replace(' ', 'T').slice(0, 19);
}

/** 把 el-date-picker 的值转成后端格式 */
export function fromPickerValue(value?: string): string | undefined {
  if (!value) return undefined;
  return value.replace('T', ' ').slice(0, 19);
}

export function rateText(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value.toFixed(2)}%`;
}
