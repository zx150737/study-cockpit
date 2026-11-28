<template>
  <button
    type="button"
    class="theme-toggle"
    :class="{ floating }"
    :title="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
    :aria-label="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
    @click="theme.toggle()"
  >
    <!-- 当前是深色 → 显示太阳（点了变浅）；当前是浅色 → 显示月亮 -->
    <svg v-if="theme.isDark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="4.2" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M12 3.2v2M12 18.8v2M3.2 12h2M18.8 12h2M5.9 5.9l1.4 1.4M16.7 16.7l1.4 1.4M18.1 5.9l-1.4 1.4M7.3 16.7l-1.4 1.4"
        stroke="currentColor" stroke-width="1.6" stroke-linecap="round"
      />
    </svg>
    <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M20 14.2A8 8 0 0 1 9.8 4a8.2 8.2 0 1 0 10.2 10.2Z"
        stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"
      />
    </svg>
  </button>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme';

withDefaults(defineProps<{ floating?: boolean }>(), { floating: false });

const theme = useThemeStore();
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border-radius: var(--r-pill);
  border: 1px solid var(--hairline);
  background: var(--surface);
  color: var(--t3);
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease, background-color 120ms ease;
}
.theme-toggle:hover {
  color: var(--accent);
  border-color: var(--accent-line);
  background: var(--accent-soft);
}
.theme-toggle svg { width: 17px; height: 17px; display: block; }

/* 登录/注册页：固定在右上角 */
.theme-toggle.floating {
  position: fixed;
  top: 18px;
  right: 20px;
  z-index: 20;
  box-shadow: var(--sh-card);
}
</style>
