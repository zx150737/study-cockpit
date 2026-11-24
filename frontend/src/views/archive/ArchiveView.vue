<template>
  <div>
    <div class="grid grid-2">
      <!-- 历史周快照 -->
      <div class="panel">
        <div class="panel-title">历史周结算记录</div>
        <el-table v-loading="loadingWeeks" :data="weeks" size="small" highlight-current-row
                  empty-text="暂无结算记录（每周日 23:59 生成）" @current-change="onSelectWeek">
          <el-table-column prop="weekStart" label="周起始" width="115" />
          <el-table-column prop="weekEnd" label="周结束" width="115" />
          <el-table-column label="完成率" width="95">
            <template #default="{ row }">{{ rateText(row.completionRate) }}</template>
          </el-table-column>
          <el-table-column prop="taskTotal" label="任务" width="65" />
          <el-table-column prop="checkinDays" label="打卡" width="65" />
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next"
                       :total="weekTotal" v-model:current-page="weekPage" :page-size="weekSize"
                       @current-change="loadWeeks" />
      </div>

      <!-- 周详情 -->
      <div class="panel">
        <div class="panel-title">周明细 · {{ selectedWeek || '请选择周次' }}</div>
        <template v-if="detail">
          <div class="detail-line"><span>加权完成率</span><b>{{ rateText(detail.summary?.completionRate) }}</b></div>
          <div class="detail-line"><span>平均每日完成率</span><b>{{ rateText(detail.summary?.avgDailyRate) }}</b></div>
          <div class="detail-line"><span>任务 / 已完成</span><b>{{ detail.summary?.taskTotal }} / {{ detail.summary?.taskFinished }}</b></div>
          <div class="detail-line"><span>打卡天数</span><b>{{ detail.summary?.checkinDays }}</b></div>
          <el-table :data="detail.tasks" size="small" max-height="260" style="margin-top:12px" empty-text="该周无任务">
            <el-table-column prop="taskDate" label="日期" width="105" />
            <el-table-column prop="title" label="任务" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.finishStatus === 1 ? 'success' : 'info'" size="small" effect="dark">
                  {{ row.finishStatus === 1 ? '已完成' : '未完成' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="weight" label="权重" width="65" />
          </el-table>
        </template>
        <div v-else class="text-muted">点击左侧周次查看明细</div>
      </div>
    </div>

    <!-- 历史任务查询 -->
    <div class="panel" style="margin-top:12px">
      <div class="panel-title row-between">
        <span>历史任务归档</span>
        <div class="filter-bar">
          <el-date-picker v-model="taskWeek" type="date" value-format="YYYY-MM-DD"
                          placeholder="按周筛选" style="width:160px" clearable @change="reloadTasks" />
          <el-select v-model="taskGoalId" placeholder="全部目标" clearable style="width:160px" @change="reloadTasks">
            <el-option v-for="g in goals" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </div>
      </div>
      <el-table v-loading="loadingTasks" :data="taskRows" size="small" empty-text="暂无历史任务">
        <el-table-column prop="taskDate" label="日期" width="105" />
        <el-table-column prop="title" label="任务内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="goalName" label="目标" width="120" />
        <el-table-column prop="subjectName" label="科目" width="110" />
        <el-table-column prop="weight" label="权重" width="70" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="row.finishStatus === 1 ? 'success' : 'info'" size="small" effect="dark">
              {{ row.finishStatus === 1 ? '已完成' : '未完成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mastery" label="掌握度" width="80" />
        <el-table-column prop="reviewNote" label="复盘备注" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="taskTotal" v-model:current-page="taskPage" v-model:page-size="taskSize"
                     :page-sizes="[20, 50, 100]" @current-change="loadTasks" @size-change="reloadTasks" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { archiveApi, goalApi } from '@/api';
import { mondayOf, rateText } from '@/utils/date';
import type { GoalVO, TaskVO, WeekVO, WeekDetailVO } from '@/types';

const goals = ref<GoalVO[]>([]);

const weeks = ref<WeekVO[]>([]);
const weekTotal = ref(0);
const weekPage = ref(1);
const weekSize = ref(20);
const loadingWeeks = ref(false);
const selectedWeek = ref('');
const detail = ref<WeekDetailVO | null>(null);

const taskRows = ref<TaskVO[]>([]);
const taskTotal = ref(0);
const taskPage = ref(1);
const taskSize = ref(20);
const taskWeek = ref('');
const taskGoalId = ref<number | undefined>(undefined);
const loadingTasks = ref(false);

async function loadWeeks(): Promise<void> {
  loadingWeeks.value = true;
  try {
    const data = await archiveApi.weeks({ page: weekPage.value, size: weekSize.value });
    weeks.value = data.list;
    weekTotal.value = data.total;
  } finally {
    loadingWeeks.value = false;
  }
}

async function onSelectWeek(row: WeekVO | null): Promise<void> {
  if (!row) return;
  selectedWeek.value = row.weekStart;
  detail.value = await archiveApi.weekDetail(row.weekStart);
}

async function loadTasks(): Promise<void> {
  loadingTasks.value = true;
  try {
    const data = await archiveApi.tasks({
      weekStart: taskWeek.value ? mondayOf(taskWeek.value) : undefined,
      goalId: taskGoalId.value,
      page: taskPage.value,
      size: taskSize.value,
    });
    taskRows.value = data.list;
    taskTotal.value = data.total;
  } finally {
    loadingTasks.value = false;
  }
}

function reloadTasks(): void {
  taskPage.value = 1;
  loadTasks();
}

onMounted(async () => {
  goals.value = await goalApi.list();
  await Promise.all([loadWeeks(), loadTasks()]);
});
</script>

<style scoped>
.row-between { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.detail-line { display: flex; justify-content: space-between; padding: 7px 0; border-bottom: 1px solid var(--border); font-size: 13px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
