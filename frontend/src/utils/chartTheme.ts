/**
 * 图表取色：从 CSS 变量读取当前主题的颜色。
 *
 * ECharts 的配置是 JS 对象，拿不到 CSS 变量，所以必须显式取值。
 * 用法：在 computed 里调用 chartPalette()，并让 computed 依赖 theme.mode，
 * 这样切换深浅色时图表会自动重算。
 */

export function cssVar(name: string, fallback = ''): string {
  if (typeof document === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

/** 需要透明度时，把 hex / rgb 转成 rgba */
export function withAlpha(color: string, alpha: number): string {
  const c = color.trim();
  if (c.startsWith('#')) {
    const hex = c.length === 4
      ? c.slice(1).split('').map((x) => x + x).join('')
      : c.slice(1);
    const n = parseInt(hex, 16);
    if (Number.isNaN(n)) return c;
    const r = (n >> 16) & 255;
    const g = (n >> 8) & 255;
    const b = n & 255;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }
  const m = c.match(/rgba?\(([^)]+)\)/);
  if (m) {
    const parts = m[1].split(',').map((s) => s.trim());
    return `rgba(${parts[0]}, ${parts[1]}, ${parts[2]}, ${alpha})`;
  }
  return c;
}

export interface ChartPalette {
  accent: string;
  accentArea: string;
  t1: string;
  t2: string;
  t3: string;
  hairline: string;
  track: string;
  surface: string;
  raised: string;
  ok: string;
  danger: string;
}

export function chartPalette(): ChartPalette {
  const accent = cssVar('--accent', '#E08B8B');
  const t1 = cssVar('--t1', '#3A3330');
  const t2 = cssVar('--t2', '#7A6E68');
  const t3 = cssVar('--t3', '#A79A93');
  return {
    accent,
    accentArea: withAlpha(accent, 0.12),
    t1,
    t2,
    t3,
    hairline: cssVar('--hairline', 'rgba(58,51,48,.10)'),
    track: cssVar('--track', 'rgba(58,51,48,.09)'),
    surface: cssVar('--surface', '#FFFFFF'),
    raised: cssVar('--raised', '#FFF8F2'),
    ok: cssVar('--ok', '#6F9E6F'),
    danger: cssVar('--danger', '#D07C6B'),
  };
}
