import { defineStore } from 'pinia';

export type ThemeMode = 'light' | 'dark';

const KEY = 'sc-theme';

/** 与 index.css 里的 --canvas 保持一致，用于移动端地址栏配色 */
const THEME_COLOR: Record<ThemeMode, string> = {
  light: '#FBF7F2',
  dark: '#1B1A22',
};

export const useThemeStore = defineStore('theme', {
  state: () => ({
    /** 默认浅色：用户反馈"太黑不好看"，首次进入不再给深色 */
    mode: 'light' as ThemeMode,
    ready: false,
  }),

  getters: {
    isDark: (s) => s.mode === 'dark',
  },

  actions: {
    init(): void {
      let saved: ThemeMode | null = null;
      try {
        const v = localStorage.getItem(KEY);
        if (v === 'light' || v === 'dark') saved = v;
      } catch {
        // 隐私模式下读不到就用默认值
      }
      this.set(saved ?? 'light');
      this.ready = true;
    },

    set(mode: ThemeMode): void {
      this.mode = mode;
      const root = document.documentElement;
      root.classList.toggle('dark', mode === 'dark');
      root.dataset.theme = mode;
      const meta = document.querySelector('meta[name="theme-color"]');
      if (meta) meta.setAttribute('content', THEME_COLOR[mode]);
      try {
        localStorage.setItem(KEY, mode);
      } catch {
        // 记忆失败不影响本次切换
      }
    },

    toggle(): void {
      this.set(this.mode === 'light' ? 'dark' : 'light');
    },
  },
});
