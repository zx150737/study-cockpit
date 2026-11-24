<template>
  <div v-loading="loading" class="dash">
    <template v-if="overview">
      <!-- 关键指标 -->
      <div class="grid grid-4">
        <div class="panel">
          <div class="metric-label">当日整体完成率</div>
          <div class="metric-value">{{ overview.hasTask ? rateText(overview.todayRate) : '暂无任务' }}</div>
        </div>
        <div class="panel">
          <div class="metric-label">连续打卡天数</div>
          <div class="metric-value">{{ overview.streakDays }} <span style="font-size:13px">天</span></div>
        </div>
        <div class="panel">
          <div class="metric-label">本周平均完成率</div>
          <div class="metric-value">{{ rateText(overview.weeklyAvgRate) }}</div>
        </div>
        <div class="panel">
          <div class="metric-label">积压任务</div>
          <div class="metric-value" :class="{ 'text-warn': overview.backlog.total > 0 }">
            {{ overview.backlog.total }} <span style="font-size:13px">条</span>
          </div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="grid grid-2" style="margin-top:12px">
        <div class="panel">
          <div class="panel-title">当日完成率</div>
          <EChartWrap :option="donutOption" :height="240" />
        </div>
        <div class="panel">
          <div class="panel-title">本周完成率趋势（周一 ~ 周日）</div>
          <EChartWrap :option="trendOption" :height="240" />
        </div>
      </div>

      <div class="grid grid-2" style="margin-top:12px">
        <!-- 目标进度卡片 -->
        <div class="panel">
          <div class="panel-title">各备考目标进度</div>
          <div v-if="!overview.goalCards.length" class="text-muted">暂无目标，请先在「目标 & 科目」中创建</div>
          <div v-for="card in overview.goalCards" :key="card.goalId" class="goal-row">
            <div class="goal-head">
              <span>{{ card.name }}</span>
              <span class="text-muted">{{ card.taskTotal }} 项 · 本周 {{ rateText(card.weekRate) }}</span>
            </div>
            <el-progress :percentage="Number(card.completionRate)" :stroke-width="8" :show-text="false" color="#39c5cf" />
            <div class="goal-rate">累计 {{ rateText(card.completionRate) }}</div>
          </div>
        </div>

        <!-- 学习基线对比 -->
        <div class="panel">
          <div class="panel-title">学习基线对比</div>
          <template v-if="overview.baseline.available">
            <div class="baseline-row"><span>基线（近 {{ overview.baseline.sampleWeeks }} 周）</span><b>{{ rateText(overview.baseline.baselineRate) }}</b></div>
            <div class="baseline-row"><span>本周当前</span><b>{{ rateText(overview.baseline.currentRate) }}</b></div>
            <div class="baseline-row">
              <span>差值</span>
              <b :class="(overview.baseline.diff ?? 0) >= 0 ? 'text-ok' : 'text-warn'">
                {{ (overview.baseline.diff ?? 0) >= 0 ? '+' : '' }}{{ rateText(overview.baseline.diff) }}
              </b>
            </div>
            <div class="baseline-row"><span>相对提升</span><b>{{ rateText(overview.baseline.improvementPct) }}</b></div>
          </template>
          <div v-else class="text-muted">暂无基线数据：运行满一个结算周后自动生成</div>
        </div>
      </div>

      <!-- 积压预警面板 -->
      <div class="panel" style="margin-top:12px">
        <div class="panel-title">积压任务预警 <span class="text-warn">({{ overview.backlog.total }})</span></div>
        <el-table :data="overview.backlog.items" size="small" empty-text="暂无积压任务" row-class-name="row-overdue">
          <el-table-column prop="title" label="任务内容" min-width="180" show-overflow-tooltip />
          <el-table-column prop="goalName" label="目标" width="130" />
          <el-table-column prop="subjectName" label="科目" width="130" />
          <el-table-column prop="taskDate" label="任务日期" width="110" />
          <el-table-column prop="overdueDays" label="逾期天数" width="90">
            <template #default="{ row }"><span class="text-warn">{{ row.overdueDays }}</span></template>
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
import type { OverviewVO } from '@/types';

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

const donutOption = computed<EChartsOption>(() => {
  const rate = overview.value?.todayRate ?? 0;
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    title: {
      text: `${rate.toFixed(2)}%`,
      left: 'center',
      top: '40%',
      textStyle: { color: '#39c5cf', fontSize: 22, fontWeight: 700 },
      subtext: '当日完成率',
      subtextStyle: { color: '#8b949e', fontSize: 11 },
      itemGap: 6,
    },
    series: [{
      type: 'pie',
      radius: ['64%', '84%'],
      center: ['50%', '52%'],
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: '#161b22', borderWidth: 2 },
      data: [
        { value: Number(rate.toFixed(2)), name: '已完成', itemStyle: { color: '#39c5cf' } },
        { value: Number((100 - rate).toFixed(2)), name: '未完成', itemStyle: { color: '#30363d' } },
      ],
    }],
  };
});

const trendOption = computed<EChartsOption>(() => {
  const points = (overview.value?.weeklyTrend ?? []).map((p) => p.rate);
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 16, top: 20, bottom: 28 },
    xAxis: {
      type: 'category',
      data: weekLabels(mondayOf(today())),
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
      data: points,
      symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#39c5cf', width: 2 },
      itemStyle: { color: '#39c5cf' },
      areaStyle: { color: 'rgba(57,197,207,0.10)' },
      connectNulls: false,
    }],
  };
});
</script>

<style scoped>
.goal-row { margin-bottom: 14px; }
.goal-head { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px; }
.goal-rate { font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.baseline-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--border); font-size: 13px; }
.baseline-row:last-child { border-bottom: none; }
</style>
