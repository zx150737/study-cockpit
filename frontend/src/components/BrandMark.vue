<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    class="brand-mark-svg"
    role="img"
    aria-label="Study Cockpit"
  >
    <rect x="1" y="1" width="30" height="30" rx="7" :fill="filled ? 'currentColor' : 'none'" :fill-opacity="filled ? 0.12 : 0" />
    <rect x="1" y="1" width="30" height="30" rx="7" fill="none" class="bm-ring" stroke-width="1.4" />
    <path
      d="M8 21 L13 16 L18 19 L24 10"
      fill="none"
      stroke="currentColor"
      stroke-width="2.6"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="24" cy="10" r="2" fill="currentColor" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: number; filled?: boolean }>(), {
  size: 20,
  filled: false,
});
</script>

<style scoped>
/* 标记颜色跟随主题强调色，深浅色切换时自动变化 */
.brand-mark-svg {
  display: block;
  flex: none;
  color: var(--accent);
}
.bm-ring { stroke: var(--hairline-strong); }
</style>
