<template>
  <div v-loading="loading" class="dash">
    <template v-if="overview">
      <!-- 首屏：1 个主指标 + 3 个次级指标，层级拉开 -->
      <section class="hero panel">
        <div class="hero-primary">
          <div class="metric-label">今日整体完成率</div>
          <div class="metric-value is-accent">
            {{ overview.hasTask ? num(overview.todayRate) : '—' }}<span v-if="overview.hasTask" class="metric-unit">%</span>
          </div>
          <div v-if="!overview.hasTask" class="hero-hint">今天还没有任务，去「任务管理」安排一个</div>
        </div>
        <div class="hero-secondary">
          <div class="hs">
            <div class="metric-label">连续打卡</div>
            <div class="metric-sub-value num">{{ overview.streakDays }}<span class="metric-unit"> 天</span></div>
          </div>
          <div class="hs">
            <div class="metric-label">本周平均</div>
            <div class="metric-sub-value num">{{ num(overview.weeklyAvgRate) }}<span class="metric-unit">%</span></div>
          </div>
          <div class="hs">
            <div class="metric-label">积压任务</div>
            <div class="metric-sub-value num" :class="{ 'is-danger': overview.backlog.total > 0 }">
              {{ overview.backlog.total }}<span class="metric-unit"> 条</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 图表区 -->
      <div class="grid grid-2">
        <div class="panel">
          <div class="panel-title">今日完成率</div>
          <EChartWrap :option="donutOption" :height="240" />
        </div>
        <div class="panel">
          <div class="panel-title">本周趋势 · 周一至周日</div>
          <EChartWrap :option="trendOption" :height="240" />
        </div>
      </div>

      <div class="grid grid-2">
        <!-- 目标进度 -->
        <div class="panel">
          <div class="panel-title">各目标进度</div>
          <div v-if="!overview.goalCards.length" class="empty-line">暂无目标，先在「目标 & 科目」中创建</div>
          <div v-for="card in overview.goalCards" :key="card.goalId" class="goal-row">
            <div class="goal-head">
              <span class="goal-name">{{ card.name }}</span>
              <span class="num goal-pct">{{ num(card.completionRate) }}%</span>
            </div>
            <div class="hairline-bar"><i :style="{ width: clamp(card.completionRate) + '%' }"></i></div>
            <div class="goal-sub">{{ card.taskTotal }} 项 · 本周 {{ rateText(card.weekRate) }}</div>
          </div>
        </div>

        <!-- 学习基线 -->
        <div class="panel">
          <div class="panel-title">学习基线对比</div>
          <template v-if="overview.baseline.available">
            <div class="baseline-row"><span>基线 · 近 {{ overview.baseline.sampleWeeks }} 周</span><b class="num">{{ rateText(overview.baseline.baselineRate) }}</b></div>
            <div class="baseline-row"><span>本周当前</span><b class="num">{{ rateText(overview.baseline.currentRate) }}</b></div>
            <div class="baseline-row">
              <span>差值</span>
              <b class="num" :class="(overview.baseline.diff ?? 0) >= 0 ? 'text-ok' : 'text-warn'">
                {{ (overview.baseline.diff ?? 0) >= 0 ? '+' : '' }}{{ rateText(overview.baseline.diff) }}
              </b>
            </div>
            <div class="baseline-row"><span>相对提升</span><b class="num">{{ rateText(overview.baseline.improvementPct) }}</b></div>
          </template>
          <div v-else class="empty-line">暂无基线数据 · 运行满一个结算周后自动生成</div>
        </div>
      </div>

      <!-- 积压预警 -->
      <div class="panel">
        <div class="panel-title">
          积压任务预警
          <span v-if="overview.backlog.total > 0" class="dot is-overdue" style="margin-left:8px"></span>
          <span v-if="overview.backlog.total > 0" class="num" style="color:var(--danger)">{{ overview.backlog.total }}</span>
        </div>
        <el-table :data="overview.backlog.items" empty-text="暂无积压任务" row-class-name="row-overdue">
          <el-table-column prop="title" label="任务内容" min-width="180" show-overflow-tooltip />
          <el-table-column prop="goalName" label="目标" width="130" show-overflow-tooltip />
          <el-table-column prop="subjectName" label="科目" width="130" show-overflow-tooltip />
          <el-table-column prop="taskDate" label="任务日期" width="110" />
          <el-table-column prop="overdueDays" label="逾期天数" width="90" align="right">
            <template #default="{ row }"><span class="num text-warn">{{ row.overdueDays }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { EChartsOption } from 'echarts';
import EChartWrap from '@/components/EChartWrap.vue';
import { dashboardApi } from '@/api';
import { rateText, mondayOf, today, weekLabels } from '@/utils/date';
import { chartPalette } from '@/utils/chartTheme';
import { useThemeStore } from '@/stores/theme';
import type { OverviewVO } from '@/types';

const theme = useThemeStore();
const loading = ref(false);
const overview = ref<OverviewVO | null>(null);

onMounted(async () => {
  loading.value = true;
  try {
    overview.value = await dashboardApi.overview();
  } finally {
    loading.value = false;
  }
});

function clamp(n: number | undefined | null): number {
  return Math.max(0, Math.min(100, Number(n ?? 0)));
}
function num(v: number | undefined | null): string {
  return v === null || v === undefined ? '—' : v.toFixed(2);
}

const donutOption = computed<EChartsOption>(() => {
  const p = chartPalette();
  void theme.mode;
  const rate = overview.value?.todayRate ?? 0;
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', textStyle: { color: p.t1 }, backgroundColor: p.raised, borderColor: p.hairline },
    title: {
      text: `${rate.toFixed(2)}%`,
      left: 'center',
      top: '40%',
      textStyle: { color: p.accent, fontSize: 24, fontWeight: 500, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' },
      subtext: '今日完成率',
      subtextStyle: { color: p.t3, fontSize: 11 },
      itemGap: 6,
    },
    series: [{
      type: 'pie',
      radius: ['66%', '84%'],
      center: ['50%', '52%'],
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: p.surface, borderWidth: 2 },
      data: [
        { value: Number(rate.toFixed(2)), name: '已完成', itemStyle: { color: p.accent } },
        { value: Number((100 - rate).toFixed(2)), name: '未完成', itemStyle: { color: p.track } },
      ],
    }],
  };
});

const trendOption = computed<EChartsOption>(() => {
  const p = chartPalette();
  void theme.mode;
  const points = (overview.value?.weeklyTrend ?? []).map((x) => x.rate);
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', textStyle: { color: p.t1 }, backgroundColor: p.raised, borderColor: p.hairline },
    grid: { left: 44, right: 16, top: 20, bottom: 28 },
    xAxis: {
      type: 'category',
      data: weekLabels(mondayOf(today())),
      axisLine: { lineStyle: { color: p.hairline } },
      axisTick: { show: false },
      axisLabel: { color: p.t3, fontSize: 11 },
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      splitLine: { show: false },
      axisLabel: { color: p.t3, formatter: '{value}%' },
    },
    series: [{
      type: 'line',
      data: points,
      symbol: 'circle', symbolSize: 5,
      lineStyle: { color: p.accent, width: 2 },
      itemStyle: { color: p.accent },
      areaStyle: { color: p.accentArea },
      connectNulls: false,
    }],
  };
});
</script>

<style scoped>
.dash { display: flex; flex-direction: column; gap: var(--sp-4); }

/* 首屏 hero */
.hero {
  display: grid;
  grid-template-columns: 1.4fr 2fr;
  gap: var(--sp-8);
  align-items: stretch;
  padding: var(--sp-6);
}
.hero-primary {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding-right: var(--sp-8);
  border-right: 1px solid var(--hairline);
}
.hero-hint {
  font-size: var(--fs-body);
  color: var(--t3);
}
.hero-secondary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-4);
}
.hs {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: var(--sp-4);
  background: var(--overlay);
  border: 1px solid var(--hairline);
  border-radius: var(--r-ctl);
}

/* 目标进度 */
.goal-row { padding: 10px 0; border-bottom: 1px solid var(--hairline); }
.goal-row:last-child { border-bottom: none; }
.goal-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }
.goal-name { font-size: var(--fs-body); color: var(--t1); }
.goal-pct { font-size: var(--fs-h2); font-weight: 500; color: var(--accent); }
.goal-sub { font-size: var(--fs-label); color: var(--t3); margin-top: 6px; }

/* 基线 */
.baseline-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 10px 0;
  border-bottom: 1px solid var(--hairline);
  font-size: var(--fs-body);
  color: var(--t2);
}
.baseline-row:last-child { border-bottom: none; }
.baseline-row b { font-weight: 500; color: var(--t1); }

.empty-line { font-size: var(--fs-body); color: var(--t3); padding: 8px 0; }

@media (max-width: 767px) {
  .hero { grid-template-columns: 1fr; }
  .hero-primary { border-right: none; padding-right: 0; border-bottom: 1px solid var(--hairline); padding-bottom: var(--sp-4); }
  .hero-secondary { grid-template-columns: 1fr; }
}
</style>
