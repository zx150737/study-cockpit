<template>
  <div>
    <div class="grid grid-2">
      <!-- 目标 -->
      <div class="panel">
        <div class="panel-title row-between">
          <span>备考目标</span>
          <el-button size="small" type="primary" @click="openGoalDialog()">新增目标</el-button>
        </div>
        <el-table :data="goals" size="small" highlight-current-row empty-text="暂无目标" @current-change="onSelectGoal">
          <el-table-column prop="name" label="目标名称" min-width="130" show-overflow-tooltip />
          <el-table-column label="累计完成率" width="150">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.completionRate)" :stroke-width="6" color="#39c5cf" />
            </template>
          </el-table-column>
          <el-table-column prop="subjectCount" label="科目" width="60" />
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link size="small" @click="openGoalDialog(row)">编辑</el-button>
              <el-button link size="small" type="danger" @click="removeGoal(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 科目 -->
      <div class="panel">
        <div class="panel-title row-between">
          <span>科目 · {{ currentGoal?.name || '请先选择目标' }}</span>
          <el-button size="small" type="primary" :disabled="!currentGoal" @click="openSubjectDialog()">新增科目</el-button>
        </div>
        <el-table :data="subjects" size="small" empty-text="暂无科目">
          <el-table-column prop="name" label="科目名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="defaultWeight" label="默认权重" width="100" />
          <el-table-column label="本周完成率" width="140">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.completionRate)" :stroke-width="6" color="#39c5cf" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link size="small" @click="openSubjectDialog(row)">编辑</el-button>
              <el-button link size="small" type="danger" @click="removeSubject(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 目标弹窗 -->
    <el-dialog v-model="goalDialog" :title="editingGoal ? '编辑目标' : '新增目标'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="目标名称"><el-input v-model="goalForm.name" maxlength="128" placeholder="如 2027 考研 / 教师编" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="goalDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGoal">保存</el-button>
      </template>
    </el-dialog>

    <!-- 科目弹窗 -->
    <el-dialog v-model="subjectDialog" :title="editingSubject ? '编辑科目' : '新增科目'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="科目名称"><el-input v-model="subjectForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="默认权重">
          <el-input-number v-model="subjectForm.defaultWeight" :min="0.01" :max="999.99" :precision="2" />
          <span class="text-muted" style="margin-left:8px;font-size:11px">创建任务时自动带入</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subjectDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSubject">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { goalApi, subjectApi } from '@/api';
import type { GoalVO, SubjectVO } from '@/types';

const goals = ref<GoalVO[]>([]);
const subjects = ref<SubjectVO[]>([]);
const currentGoal = ref<GoalVO | null>(null);
const saving = ref(false);

const goalDialog = ref(false);
const editingGoal = ref<GoalVO | null>(null);
const goalForm = reactive({ name: '' });

const subjectDialog = ref(false);
const editingSubject = ref<SubjectVO | null>(null);
const subjectForm = reactive({ name: '', defaultWeight: 1 });

async function loadGoals(): Promise<void> {
  goals.value = await goalApi.list();
  if (currentGoal.value) {
    const found = goals.value.find((g) => g.id === currentGoal.value?.id);
    currentGoal.value = found || null;
  }
}

async function loadSubjects(): Promise<void> {
  subjects.value = currentGoal.value ? await subjectApi.list(currentGoal.value.id) : [];
}

function onSelectGoal(row: GoalVO | null): void {
  currentGoal.value = row;
  loadSubjects();
}

function openGoalDialog(row?: GoalVO): void {
  editingGoal.value = row || null;
  goalForm.name = row?.name || '';
  goalDialog.value = true;
}

async function saveGoal(): Promise<void> {
  if (!goalForm.name.trim()) {
    ElMessage.warning('请输入目标名称');
    return;
  }
  saving.value = true;
  try {
    if (editingGoal.value) {
      await goalApi.update(editingGoal.value.id, { name: goalForm.name.trim() });
    } else {
      await goalApi.create({ name: goalForm.name.trim() });
    }
    goalDialog.value = false;
    await loadGoals();
    ElMessage.success('已保存');
  } finally {
    saving.value = false;
  }
}

async function removeGoal(row: GoalVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除目标「${row.name}」？历史任务会保留供归档。`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await goalApi.remove(row.id);
  if (currentGoal.value?.id === row.id) {
    currentGoal.value = null;
    subjects.value = [];
  }
  await loadGoals();
  ElMessage.success('已删除');
}

function openSubjectDialog(row?: SubjectVO): void {
  if (!currentGoal.value) return;
  editingSubject.value = row || null;
  subjectForm.name = row?.name || '';
  subjectForm.defaultWeight = row?.defaultWeight || 1;
  subjectDialog.value = true;
}

async function saveSubject(): Promise<void> {
  if (!currentGoal.value || !subjectForm.name.trim()) {
    ElMessage.warning('请输入科目名称');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      goalId: currentGoal.value.id,
      name: subjectForm.name.trim(),
      defaultWeight: subjectForm.defaultWeight,
    };
    if (editingSubject.value) {
      await subjectApi.update(editingSubject.value.id, payload);
    } else {
      await subjectApi.create(payload);
    }
    subjectDialog.value = false;
    await Promise.all([loadSubjects(), loadGoals()]);
    ElMessage.success('已保存');
  } finally {
    saving.value = false;
  }
}

async function removeSubject(row: SubjectVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除科目「${row.name}」？`, '提示', { type: 'warning' });
  } catch {
    return;
  }
  await subjectApi.remove(row.id);
  await Promise.all([loadSubjects(), loadGoals()]);
  ElMessage.success('已删除');
}

onMounted(loadGoals);
</script>

<style scoped>
.row-between { display: flex; align-items: center; justify-content: space-between; }
</style>
