<template>
  <div ref="chartEl" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';

/** ECharts 统一封装：关闭花哨动画、随容器尺寸自适应（PC/移动端） */
const props = withDefaults(defineProps<{ option: EChartsOption; height?: number }>(), {
  height: 260,
});

const chartEl = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let observer: ResizeObserver | null = null;

function render(): void {
  if (!chartEl.value) return;
  if (!chart) chart = echarts.init(chartEl.value);
  chart.setOption({ ...props.option, animation: false }, true);
}

onMounted(() => {
  render();
  observer = new ResizeObserver(() => chart?.resize());
  if (chartEl.value) observer.observe(chartEl.value);
});

watch(() => props.option, () => nextTick(render), { deep: true });

onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
  chart = null;
});
</script>
