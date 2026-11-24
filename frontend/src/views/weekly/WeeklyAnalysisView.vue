<template>
  <div>
    <div class="panel filter-bar">
      <span class="text-muted">周次</span>
      <el-date-picker :model-value="weekStart" type="date" value-format="YYYY-MM-DD"
                      style="width:160px" @update:model-value="onWeekPick" />
      <span class="text-muted">{{ weekStart }} ~ {{ sundayOf(weekStart) }}</span>
      <el-select v-model="goalId" placeholder="全部目标" clearable style="width:170px" @change="load">
        <el-option v-for="g in goals" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <div class="spacer"></div>
      <el-button type="primary" :loading="exporting" @click="exportCsv">导出 CSV</el-button>
    </div>

    <div v-loading="loading" style="margin-top:12px">
      <div class="grid grid-4">
        <div class="panel"><div class="metric-label">本周完成率</div><div class="metric-value">{{ rateText(data?.completionRate) }}</div></div>
        <div class="panel"><div class="metric-label">本周平均完成率</div><div class="metric-value">{{ rateText(data?.avgDailyRate) }}</div></div>
        <div class="panel"><div class="metric-label">任务总数</div><div class="metric-value">{{ data?.taskTotal ?? 0 }}</div></div>
        <div class="panel"><div class="metric-label">周起止</div><div class="metric-value" style="font-size:15px">{{ weekStart }} ~ {{ sundayOf(weekStart) }}</div></div>
      </div>

      <div class="grid grid-2" style="margin-top:12px">
        <div class="panel">
          <div class="panel-title">每日完成率趋势</div>
          <EChartWrap :option="trendOption" :height="260" />
        </div>
        <div class="panel">
          <div class="panel-title">各科完成率</div>
          <EChartWrap :option="subjectOption" :height="260" />
        </div>
      </div>

      <div class="panel" style="margin-top:12px">
        <div class="panel-title">导出说明</div>
        <div class="text-muted" style="font-size:12px;line-height:1.8">
          导出按当前「周次 + 目标」筛选，后端流式生成 UTF-8 CSV，浏览器直接下载。<br />
          CSV 仅保留 <b>[IMAGE]</b> 标记与图片数量，不嵌入图片、不输出临时访问链接，避免文件体积膨胀；短时效链接写入归档也没有意义。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { EChartsOption } from 'echarts';
import { ElMessage } from 'element-plus';
import EChartWrap from '@/components/EChartWrap.vue';
import { reportApi, goalApi } from '@/api';
import { today, mondayOf, sundayOf, weekLabels, rateText } from '@/utils/date';
import type { GoalVO, WeeklyVO } from '@/types';

const loading = ref(false);
const exporting = ref(false);
const weekStart = ref(mondayOf(today()));
const goalId = ref<number | undefined>(undefined);
const goals = ref<GoalVO[]>([]);
const data = ref<WeeklyVO | null>(null);

function onWeekPick(value: string | null): void {
  // 统一归一到周一，避免周次计算错乱
  weekStart.value = value ? mondayOf(value) : mondayOf(today());
  load();
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    data.value = await reportApi.weekly({ weekStart: weekStart.value, goalId: goalId.value });
  } finally {
    loading.value = false;
  }
}

async function exportCsv(): Promise<void> {
  exporting.value = true;
  try {
    await reportApi.exportCsv({ weekStart: weekStart.value, goalId: goalId.value });
    ElMessage.success('CSV 已开始下载');
  } finally {
    exporting.value = false;
  }
}

const trendOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis' },
  grid: { left: 44, right: 16, top: 20, bottom: 28 },
  xAxis: {
    type: 'category', data: weekLabels(weekStart.value),
    axisLine: { lineStyle: { color: '#30363d' } },
    axisLabel: { color: '#8b949e', fontSize: 11 },
  },
  yAxis: {
    type: 'value', min: 0, max: 100,
    splitLine: { lineStyle: { color: '#21262d' } },
    axisLabel: { color: '#8b949e', formatter: '{value}%' },
  },
  series: [{
    type: 'line',
    data: (data.value?.dailyTrend ?? []).map((p) => p.rate),
    symbol: 'circle', symbolSize: 6,
    lineStyle: { color: '#39c5cf', width: 2 },
    itemStyle: { color: '#39c5cf' },
    areaStyle: { color: 'rgba(57,197,207,0.10)' },
  }],
}));

const subjectOption = computed<EChartsOption>(() => {
  const rates = data.value?.subjectRates ?? [];
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 24, top: 12, bottom: 24 },
    xAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: '#21262d' } }, axisLabel: { color: '#8b949e' } },
    yAxis: {
      type: 'category', data: rates.map((r) => r.name),
      axisLine: { lineStyle: { color: '#30363d' } },
      axisLabel: { color: '#8b949e', fontSize: 11 },
    },
    series: [{
      type: 'bar', data: rates.map((r) => Number(r.completionRate)),
      barWidth: 12,
      itemStyle: { color: '#39c5cf', borderRadius: [0, 2, 2, 0] },
    }],
  };
});

onMounted(async () => {
  goals.value = await goalApi.list();
  await load();
});
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.spacer { flex: 1; }
</style>
