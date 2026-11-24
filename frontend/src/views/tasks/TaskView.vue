<template>
  <div>
    <!-- 筛选栏 -->
    <div class="panel filter-bar">
      <el-date-picker v-model="query.taskDate" type="date" value-format="YYYY-MM-DD"
                      placeholder="任务日期" style="width:150px" clearable @change="reload" />
      <el-select v-model="query.goalId" placeholder="全部目标" clearable style="width:160px" @change="reload">
        <el-option v-for="g in goals" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <el-select v-model="query.finishStatus" placeholder="全部状态" clearable style="width:130px" @change="reload">
        <el-option label="未完成" :value="0" />
        <el-option label="已完成" :value="1" />
      </el-select>
      <el-checkbox v-model="query.overdue" @change="reload">仅看积压</el-checkbox>
      <div class="spacer"></div>
      <el-button type="primary" @click="openDialog()">新增任务</el-button>
    </div>

    <!-- 任务表格 -->
    <div class="panel" style="margin-top:12px">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无任务"
                :row-class-name="rowClass">
        <el-table-column prop="taskDate" label="日期" width="105" />
        <el-table-column prop="title" label="任务内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="goalName" label="目标" width="120" show-overflow-tooltip />
        <el-table-column prop="subjectName" label="科目" width="110" show-overflow-tooltip />
        <el-table-column prop="weight" label="权重" width="70" />
        <el-table-column prop="deadline" label="截止时间" width="150" show-overflow-tooltip />
        <el-table-column label="掌握度" width="90">
          <template #default="{ row }">{{ row.mastery ? row.mastery + '/5' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="row.finishStatus === 1 ? 'success' : (row.overdue ? 'danger' : 'info')" size="small" effect="dark">
              {{ row.finishStatus === 1 ? '已完成' : (row.overdue ? '已逾期' : '未完成') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="70">
          <template #default="{ row }">{{ row.imageCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="toggleFinish(row)">
              {{ row.finishStatus === 1 ? '撤销' : '完成' }}
            </el-button>
            <el-button link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link size="small" @click="openImages(row)">图片</el-button>
            <el-button link size="small" type="danger" @click="removeTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="page" v-model:page-size="size"
                     :page-sizes="[20, 50, 100]" @current-change="load" @size-change="reload" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog" :title="editing ? '编辑任务' : '新增任务'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="备考目标">
          <el-select v-model="form.goalId" style="width:100%" @change="onGoalChange">
            <el-option v-for="g in goals" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="form.subjectId" style="width:100%" :disabled="!form.goalId">
            <el-option v-for="s in formSubjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务内容"><el-input v-model="form.title" maxlength="255" /></el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="0.01" :max="999.99" :precision="2" :controls="false" />
          <span class="text-muted" style="margin-left:8px;font-size:11px">留空则取科目默认权重</span>
        </el-form-item>
        <el-form-item label="任务日期">
          <el-date-picker v-model="form.taskDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width:100%" clearable />
        </el-form-item>
        <el-form-item label="掌握度">
          <el-rate v-model="form.mastery" :max="5" allow-clear />
          <span class="text-muted" style="margin-left:8px;font-size:11px">1–5 分</span>
        </el-form-item>
        <el-form-item label="复盘备注"><el-input v-model="form.reviewNote" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 图片上传预览 -->
    <el-dialog v-model="imageDialog" title="配套图片" width="520px">
      <ImageUploader v-if="imageTaskId" :task-id="imageTaskId" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { taskApi, goalApi, subjectApi } from '@/api';
import { today } from '@/utils/date';
import ImageUploader from '@/components/ImageUploader.vue';
import type { GoalVO, SubjectVO, TaskVO } from '@/types';

const loading = ref(false);
const saving = ref(false);
const rows = ref<TaskVO[]>([]);
const goals = ref<GoalVO[]>([]);
const allSubjects = ref<SubjectVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const dialog = ref(false);
const editing = ref<TaskVO | null>(null);
const imageDialog = ref(false);
const imageTaskId = ref<number | null>(null);

const query = reactive({
  taskDate: today(),
  goalId: undefined as number | undefined,
  finishStatus: undefined as number | undefined,
  overdue: false,
});

const form = reactive({
  goalId: undefined as number | undefined,
  subjectId: undefined as number | undefined,
  title: '',
  weight: null as number | null,
  taskDate: today(),
  deadline: '' as string,
  mastery: 0,
  reviewNote: '',
});

const formSubjects = computed(() =>
  allSubjects.value.filter((s) => s.goalId === form.goalId));

async function loadBase(): Promise<void> {
  goals.value = await goalApi.list();
  // 拉取每个目标下的科目，供表单联动
  const lists = await Promise.all(goals.value.map((g) => subjectApi.list(g.id)));
  allSubjects.value = lists.flat();
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await taskApi.page({
      taskDate: query.taskDate || undefined,
      goalId: query.goalId,
      finishStatus: query.finishStatus,
      overdue: query.overdue || undefined,
      page: page.value,
      size: size.value,
    });
    rows.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function reload(): void {
  page.value = 1;
  load();
}

/** 未完成且已过期/逾期的任务标红 */
function rowClass({ row }: { row: TaskVO }): string {
  return row.overdue ? 'row-overdue' : '';
}

function onGoalChange(): void {
  form.subjectId = undefined;
}

function openDialog(row?: TaskVO): void {
  editing.value = row || null;
  form.goalId = row?.goalId ?? goals.value[0]?.id;
  form.subjectId = row?.subjectId;
  form.title = row?.title || '';
  form.weight = row?.weight ?? null;
  form.taskDate = row?.taskDate || today();
  form.deadline = row?.deadline || '';
  form.mastery = row?.mastery || 0;
  form.reviewNote = row?.reviewNote || '';
  dialog.value = true;
}

function buildPayload() {
  return {
    goalId: form.goalId as number,
    subjectId: form.subjectId as number,
    title: form.title.trim(),
    weight: form.weight ?? undefined,
    taskDate: form.taskDate,
    deadline: form.deadline || undefined,
    mastery: form.mastery || undefined,
    reviewNote: form.reviewNote || undefined,
  };
}

async function saveTask(): Promise<void> {
  if (!form.goalId || !form.subjectId || !form.title.trim() || !form.taskDate) {
    ElMessage.warning('请填写目标、科目、任务内容与日期');
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await taskApi.update(editing.value.id, buildPayload());
    } else {
      await taskApi.create(buildPayload());
    }
    dialog.value = false;
    await load();
    ElMessage.success('已保存');
  } finally {
    saving.value = false;
  }
}

async function toggleFinish(row: TaskVO): Promise<void> {
  await taskApi.finish(row.id, row.finishStatus === 1 ? 0 : 1);
  await load();
}

async function removeTask(row: TaskVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除任务「${row.title}」？`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await taskApi.remove(row.id);
  await load();
}

function openImages(row: TaskVO): void {
  imageTaskId.value = row.id;
  imageDialog.value = true;
}

onMounted(async () => {
  await loadBase();
  await load();
});
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.spacer { flex: 1; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
