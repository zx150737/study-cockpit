<template>
  <div>
    <div class="grid grid-4">
      <div class="panel">
        <div class="metric-label">今日待复习</div>
        <div class="metric-value" :class="{ 'is-accent': (stats?.dueTotal ?? 0) > 0 }">{{ stats?.dueTotal ?? 0 }}</div>
      </div>
      <div class="panel">
        <div class="metric-label">错题卡总数</div>
        <div class="metric-value">{{ stats?.cardTotal ?? 0 }}</div>
      </div>
      <div class="panel">
        <div class="metric-label">今日已复习</div>
        <div class="metric-value">{{ stats?.reviewedToday ?? 0 }}</div>
      </div>
      <div class="panel">
        <div class="metric-label">累计正确率</div>
        <div class="metric-value">{{ stats?.accuracy ?? 0 }}<span class="metric-unit">%</span></div>
      </div>
    </div>

    <el-tabs v-model="tab" class="panel tabs-panel" style="margin-top: 12px">
      <!-- 复习队列 -->
      <el-tab-pane label="开始复习" name="review">
        <div v-if="loading" v-loading="true" style="height: 200px"></div>

        <div v-else-if="currentCard" class="review-stage">
          <div class="review-progress">
            <span class="t-label">第 {{ index + 1 }} / {{ queue.length }} 张</span>
            <el-progress :percentage="Math.round((index / queue.length) * 100)" :show-text="false" style="flex: 1" />
          </div>

          <div class="flash-card">
            <span class="t-label">题面</span>
            <pre class="flash-front">{{ currentCard.front }}</pre>

            <template v-if="showingAnswer">
              <div class="flash-divider"></div>
              <span class="t-label">答案与解析</span>
              <pre class="flash-back">{{ currentCard.back }}</pre>
            </template>
          </div>

          <div v-if="!showingAnswer" class="review-actions">
            <el-button type="primary" @click="showingAnswer = true">显示答案</el-button>
          </div>
          <div v-else class="review-actions">
            <span class="t-dim">刚才想得怎么样？</span>
            <el-button type="danger" plain :loading="grading" @click="grade(0)">完全忘了</el-button>
            <el-button :loading="grading" @click="grade(1)">有点犹豫</el-button>
            <el-button type="primary" :loading="grading" @click="grade(2)">很轻松</el-button>
          </div>
        </div>

        <el-empty v-else-if="!queue.length" :description="(stats?.cardTotal ?? 0) > 0 ? '今天没有到期卡片，明天再来' : '还没有错题卡，先去 AI 学习助手提问并加入错题本'" :image-size="90">
          <el-button type="primary" :icon="MagicStick" @click="goTutor">去提问</el-button>
        </el-empty>

        <div v-else class="done-stage">
          <div class="t-hero" style="color: var(--accent)">今天复习完成 🎉</div>
          <p class="t-muted">本轮过完 {{ queue.length }} 张卡片，按遗忘曲线排好了下一次时间。</p>
          <el-button type="primary" @click="startReview">再查一遍到期卡片</el-button>
        </div>
      </el-tab-pane>

      <!-- 卡片管理 -->
      <el-tab-pane label="卡片管理" name="manage">
        <div class="manage-bar">
          <el-input v-model="keyword" placeholder="搜索题面或答案" clearable style="width: 240px" />
          <div class="spacer"></div>
          <el-button :icon="Refresh" @click="loadCards">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">手动建卡</el-button>
        </div>

        <el-table v-loading="cardLoading" :data="filtered" size="small" style="margin-top: 12px">
          <el-table-column prop="front" label="题面" min-width="240">
            <template #default="{ row }">
              <span class="cell-clamp">{{ row.front }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="subjectName" label="科目" width="110">
            <template #default="{ row }">{{ row.subjectName || '—' }}</template>
          </el-table-column>
          <el-table-column label="熟练度" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.box >= 4 ? 'success' : row.box >= 2 ? 'primary' : 'info'">
                第 {{ row.box }} 级
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下次复习" width="130">
            <template #default="{ row }">
              <span :class="row.overdue ? 'text-warn' : ''">{{ row.dueDate }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reviewCount" label="复习次数" width="100" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button text type="danger" size="small" @click="removeCard(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <span class="t-dim">还没有卡片</span>
          </template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="createVisible" title="手动建一张错题卡" width="min(520px, 92vw)">
      <el-form label-width="72px">
        <el-form-item label="题面">
          <el-input v-model="form.front" type="textarea" :rows="3" maxlength="2000" placeholder="这道题问的是什么" />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="form.back" type="textarea" :rows="4" maxlength="4000" placeholder="正确答案 + 关键解析" />
        </el-form-item>
        <el-form-item label="科目">
          <el-input v-model="form.subjectName" maxlength="64" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, MagicStick } from '@element-plus/icons-vue';
import { tutorApi } from '@/api';
import type { TutorCardVO, TutorStatsVO } from '@/types';

const router = useRouter();
const tab = ref('review');
const stats = ref<TutorStatsVO | null>(null);

const loading = ref(false);
const cardLoading = ref(false);
const grading = ref(false);
const queue = ref<TutorCardVO[]>([]);
const index = ref(0);
const showingAnswer = ref(false);
const cards = ref<TutorCardVO[]>([]);
const keyword = ref('');

const currentCard = computed(() => queue.value[index.value] ?? null);
const filtered = computed(() => {
  const key = keyword.value.trim();
  if (!key) return cards.value;
  return cards.value.filter((c) => c.front.includes(key) || c.back.includes(key));
});

async function loadStats(): Promise<void> {
  if (!tutorApi) return;
  try { stats.value = await tutorApi.stats(); } catch { /* 忽略 */ }
}

async function loadCards(): Promise<void> {
  if (!tutorApi) return;
  cardLoading.value = true;
  try { cards.value = (await tutorApi.cards({ dueOnly: false, page: 1, size: 100 })).list; }
  catch { /* 已提示 */ }
  finally { cardLoading.value = false; }
}

async function startReview(): Promise<void> {
  if (!tutorApi) return;
  loading.value = true;
  showingAnswer.value = false;
  index.value = 0;
  try {
    queue.value = (await tutorApi.cards({ dueOnly: true, page: 1, size: 100 })).list;
    await loadStats();
  } catch {
    queue.value = [];
  } finally {
    loading.value = false;
  }
}

async function grade(value: 0 | 1 | 2): Promise<void> {
  const card = currentCard.value;
  if (!tutorApi || !card || grading.value) return;
  grading.value = true;
  try {
    await tutorApi.reviewCard(card.id, value);
    // 评为「忘了」的卡片重新排到本轮末尾，当场再练一次。
    if (value === 0) queue.value.push({ ...card });
    index.value += 1;
    showingAnswer.value = false;
    await loadStats();
  } catch {
    // 已提示
  } finally {
    grading.value = false;
  }
}

async function removeCard(row: TutorCardVO): Promise<void> {
  if (!tutorApi) return;
  try {
    await ElMessageBox.confirm('删除这张错题卡？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await tutorApi.deleteCard(row.id);
    ElMessage.success('已删除');
    await loadCards();
    await loadStats();
  } catch {
    // 已提示
  }
}

const createVisible = ref(false);
const creating = ref(false);
const form = ref({ front: '', back: '', subjectName: '' });

function openCreate(): void {
  form.value = { front: '', back: '', subjectName: '' };
  createVisible.value = true;
}

async function submitCreate(): Promise<void> {
  if (!tutorApi) return;
  if (!form.value.front.trim() || !form.value.back.trim()) {
    ElMessage.warning('题面和答案都要填');
    return;
  }
  creating.value = true;
  try {
    await tutorApi.createCard({ ...form.value });
    ElMessage.success('已保存');
    createVisible.value = false;
    await loadCards();
    await loadStats();
  } catch {
    // 已提示
  } finally {
    creating.value = false;
  }
}

function goTutor(): void {
  router.push('/tutor');
}

onMounted(async () => {
  await Promise.all([loadStats(), loadCards()]);
  await startReview();
});
</script>

<style scoped>
.tabs-panel { padding: 4px 16px 16px; }
.review-stage { display: flex; flex-direction: column; gap: 14px; padding: 8px 0; }
.review-progress { display: flex; align-items: center; gap: 12px; }
.flash-card { padding: 20px; border-radius: var(--r-panel); border: 1px solid var(--hairline); background: var(--overlay); display: flex; flex-direction: column; gap: 8px; min-height: 180px; }
.flash-front { margin: 0; font-family: inherit; font-size: 16px; line-height: 1.8; color: var(--t1); white-space: pre-wrap; word-break: break-word; }
.flash-back { margin: 0; font-family: inherit; font-size: 14px; line-height: 1.8; color: var(--t2); white-space: pre-wrap; word-break: break-word; }
.flash-divider { height: 1px; background: var(--hairline); margin: 6px 0; }
.review-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.done-stage { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px 0; text-align: center; }
.manage-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.spacer { flex: 1; }
.cell-clamp { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
</style>
