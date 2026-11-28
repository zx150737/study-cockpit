<template>
  <div class="tutor-wrap">
    <!-- 左：提问记录 -->
    <aside class="panel thread-side">
      <div class="side-head">
        <span class="panel-title">提问记录</span>
        <el-button type="primary" size="small" :icon="Plus" @click="startNew">新提问</el-button>
      </div>
      <div class="thread-list">
        <button
          v-for="t in threads"
          :key="t.id"
          class="thread-item"
          :class="{ 'is-active': t.id === currentId }"
          @click="openThread(t.id)"
        >
          <span class="thread-title">{{ t.title }}</span>
          <span class="thread-meta">
            <el-tag size="small" :type="t.mode === 'practice' ? 'warning' : 'primary'">
              {{ t.mode === 'practice' ? '练习' : '讲解' }}
            </el-tag>
            <span class="t-dim">{{ (t.createdAt || '').slice(5, 16) }}</span>
          </span>
        </button>
        <div v-if="!threads.length && !listLoading" class="side-empty">还没有提问</div>
      </div>
      <div class="side-foot">
        今日已用 {{ stats?.threadsToday ?? 0 }} / 60 次
      </div>
    </aside>

    <!-- 右：提问区 / 答复区 -->
    <section class="tutor-main">
      <div v-if="!currentId" class="panel">
        <div class="ask-head">
          <h3 class="t-h2">把题目贴进来</h3>
          <span class="t-dim">只发送你输入的这段文字，不上传整页截图，也不读取任何课程账号信息</span>
        </div>
        <el-input
          v-model="question"
          type="textarea"
          :rows="8"
          maxlength="4000"
          show-word-limit
          resize="vertical"
          placeholder="例：已知函数 f(x)=x²-4x+3，求它在区间 [0,3] 上的最小值，并说明单调性。"
        />
        <div class="ask-actions">
          <el-radio-group v-model="mode">
            <el-radio-button value="explain">讲解这道题</el-radio-button>
            <el-radio-button value="practice">出同类练习</el-radio-button>
          </el-radio-group>
          <el-select v-model="subjectId" placeholder="关联科目（可选）" clearable style="width: 180px">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <div class="spacer" />
          <el-button type="primary" :loading="asking" @click="ask">开始讲解</el-button>
        </div>
        <div v-if="asking" class="ask-progress">
          <el-progress :percentage="100" :indeterminate="true" :duration="3" :show-text="false" />
          <span class="t-dim">AI 正在思考，已等待 {{ elapsed }} 秒…</span>
        </div>
        <div v-if="!asking && lastError" class="ask-error">{{ lastError }}</div>
      </div>

      <div v-else v-loading="threadLoading" class="panel">
        <header class="ans-head">
          <div class="ans-title">
            <h3 class="t-h2">{{ current?.title || '加载中…' }}</h3>
            <div class="t-dim">
              {{ current?.createdAt }}
              <template v-if="current?.mode"> · {{ current.mode === 'practice' ? '练习生成' : '题目讲解' }}</template>
              <template v-if="current?.subjectName"> · {{ current.subjectName }}</template>
            </div>
          </div>
          <div class="ans-actions">
            <el-button :icon="Refresh" :loading="asking" @click="regenerate">重新生成</el-button>
            <el-button type="primary" plain :icon="Star" :disabled="!current?.reply" @click="addToCards">
              加入错题本
            </el-button>
            <el-button text type="danger" @click="removeThread">删除</el-button>
          </div>
        </header>

        <div class="question-echo">
          <span class="t-label">题目</span>
          <pre class="echo-text">{{ current?.question }}</pre>
        </div>

        <el-alert v-if="lastError" :title="lastError" type="error" show-icon :closable="false" style="margin-bottom: 6px" />

        <template v-if="current?.reply">
          <div class="answer-box">
            <span class="t-label">结论</span>
            <div class="answer-text">{{ current.reply.answer || '见下方推导步骤' }}</div>
          </div>

          <div v-if="current.reply.steps.length" class="steps">
            <div v-for="(step, i) in current.reply.steps" :key="i" class="step">
              <span class="step-no">{{ i + 1 }}</span>
              <div class="step-body">
                <div class="step-title">{{ step.title }}</div>
                <div class="step-detail">{{ step.detail }}</div>
              </div>
            </div>
          </div>

          <div v-if="current.reply.keyPoints.length" class="tag-row">
            <span class="t-label">考点</span>
            <el-tag v-for="k in current.reply.keyPoints" :key="k" type="primary" effect="plain">{{ k }}</el-tag>
          </div>
          <div v-if="current.reply.pitfalls.length" class="tag-row">
            <span class="t-label">易错</span>
            <el-tag v-for="p in current.reply.pitfalls" :key="p" type="warning" effect="plain">{{ p }}</el-tag>
          </div>

          <div v-if="current.reply.practice.length" class="practice">
            <div class="panel-title">变式练习 · 先自己做，再展开解析</div>
            <div v-for="(item, i) in current.reply.practice" :key="i" class="practice-card">
              <div class="practice-stem">{{ i + 1 }}. {{ item.stem }}</div>
              <ul v-if="item.options.length" class="options">
                <li v-for="option in item.options" :key="option">{{ option }}</li>
              </ul>
              <el-button text type="primary" size="small" @click="toggle(i)">
                {{ revealed[i] ? '收起解析' : '展开答案与解析' }}
              </el-button>
              <div v-if="revealed[i]" class="practice-answer">
                <div><b>答案：</b>{{ item.answer || '—' }}</div>
                <div v-if="item.explain"><b>解析：</b>{{ item.explain }}</div>
              </div>
            </div>
          </div>
        </template>

        <div v-else-if="asking" class="gen-wait">
          <el-progress :percentage="100" :indeterminate="true" :duration="3" :show-text="false" />
          <span class="t-dim">AI 正在生成讲解，已等待 {{ elapsed }} 秒…</span>
        </div>

        <el-empty v-else description="这条提问还没有讲解" :image-size="80">
          <el-button type="primary" :loading="asking" @click="regenerate">生成讲解</el-button>
        </el-empty>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Star } from '@element-plus/icons-vue';
import { tutorApi, subjectApi, goalApi } from '@/api';
import type { TutorThreadVO, TutorStatsVO, SubjectVO } from '@/types';

const threads = ref<TutorThreadVO[]>([]);
const currentId = ref<number | null>(null);
const current = ref<TutorThreadVO | null>(null);
const stats = ref<TutorStatsVO | null>(null);
const subjects = ref<SubjectVO[]>([]);
const revealed = ref<Record<number, boolean>>({});

const question = ref('');
const mode = ref<'explain' | 'practice'>('explain');
const subjectId = ref<number | null>(null);

const asking = ref(false);
const threadLoading = ref(false);
const listLoading = ref(false);
const elapsed = ref(0);
const lastError = ref('');
let timer: number | undefined;

function startTimer(): void {
  elapsed.value = 0;
  timer = window.setInterval(() => { elapsed.value += 1; }, 1000);
}
function stopTimer(): void {
  if (timer) { window.clearInterval(timer); timer = undefined; }
}

async function loadThreads(): Promise<void> {
  if (!tutorApi) return;
  listLoading.value = true;
  try {
    threads.value = (await tutorApi.threads(1, 50)).list;
  } catch {
    // 拦截器已提示
  } finally {
    listLoading.value = false;
  }
}

async function loadStats(): Promise<void> {
  if (!tutorApi) return;
  try { stats.value = await tutorApi.stats(); } catch { /* 忽略 */ }
}

async function openThread(id: number): Promise<void> {
  if (!tutorApi) return;
  currentId.value = id;
  current.value = null;
  revealed.value = {};
  lastError.value = '';
  threadLoading.value = true;
  try { current.value = await tutorApi.thread(id); } catch { currentId.value = null; } finally { threadLoading.value = false; }
}

function startNew(): void {
  currentId.value = null;
  current.value = null;
  question.value = '';
  subjectId.value = null;
  mode.value = 'explain';
  revealed.value = {};
  lastError.value = '';
}

async function ask(): Promise<void> {
  if (!tutorApi) return;
  const text = question.value.trim();
  if (text.length < 1) { ElMessage.warning('先把题目贴进来'); return; }
  asking.value = true;
  lastError.value = '';
  startTimer();
  try {
    const created = await tutorApi.createThread({ question: text, mode: mode.value, subjectId: subjectId.value });
    currentId.value = created.id;
    current.value = created;
    question.value = '';
    await tutorApi.explain(created.id);
    await openThread(created.id);
    await loadThreads();
    await loadStats();
  } catch (e) {
    lastError.value = e instanceof Error ? e.message : '生成失败，请重试';
    await loadThreads();
  } finally {
    asking.value = false;
    stopTimer();
  }
}

async function regenerate(): Promise<void> {
  if (!tutorApi || !currentId.value) return;
  asking.value = true;
  lastError.value = '';
  startTimer();
  try {
    await tutorApi.explain(currentId.value, true);
    await openThread(currentId.value);
    await loadStats();
  } catch (e) {
    lastError.value = e instanceof Error ? e.message : '生成失败，请重试';
  } finally {
    asking.value = false;
    stopTimer();
  }
}

async function addToCards(): Promise<void> {
  if (!tutorApi || !currentId.value) return;
  try {
    await tutorApi.addCard(currentId.value);
    ElMessage.success('已加入错题本，会按遗忘曲线提醒你复习');
    await loadStats();
  } catch {
    // 拦截器已提示（例如「已经在错题本里了」）
  }
}

async function removeThread(): Promise<void> {
  if (!tutorApi || !currentId.value) return;
  try {
    await ElMessageBox.confirm('删除这条提问？关联的错题卡会一起归档。', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await tutorApi.deleteThread(currentId.value);
    ElMessage.success('已删除');
    startNew();
    await loadThreads();
    await loadStats();
  } catch {
    // 已提示
  }
}

function toggle(index: number): void {
  revealed.value = { ...revealed.value, [index]: !revealed.value[index] };
}

onMounted(async () => {
  await loadThreads();
  await loadStats();
  try {
    const goals = await goalApi.list();
    const groups = await Promise.all(goals.map((g) => subjectApi.list(g.id)));
    subjects.value = groups.flat();
  } catch {
    subjects.value = [];
  }
});

onBeforeUnmount(stopTimer);
</script>

<style scoped>
.tutor-wrap { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 12px; align-items: start; }
.thread-side { display: flex; flex-direction: column; max-height: calc(100vh - 120px); position: sticky; top: 0; }
.side-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 12px 14px; border-bottom: 1px solid var(--hairline); }
.thread-list { flex: 1; overflow: auto; padding: 6px; }
.thread-item { display: flex; flex-direction: column; gap: 6px; width: 100%; text-align: left; padding: 10px; border: 1px solid transparent; border-radius: var(--r-ctl); background: transparent; cursor: pointer; color: var(--t1); }
.thread-item:hover { background: var(--overlay); }
.thread-item.is-active { background: var(--accent-soft); border-color: var(--accent-line); }
.thread-title { font-size: 13px; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.thread-meta { display: flex; align-items: center; gap: 8px; font-size: 11px; }
.side-empty { padding: 24px 12px; text-align: center; color: var(--t3); font-size: 12px; }
.side-foot { padding: 10px 14px; border-top: 1px solid var(--hairline); font-size: 11px; color: var(--t3); font-family: var(--font-mono); }

.tutor-main { min-width: 0; }
.ask-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.ask-actions { display: flex; align-items: center; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.spacer { flex: 1; }
.ask-progress { display: flex; flex-direction: column; gap: 6px; margin-top: 14px; font-size: 12px; }
.ask-error { margin-top: 12px; font-size: 13px; color: var(--danger); }

.ans-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.ans-title { display: flex; flex-direction: column; gap: 4px; }
.ans-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.question-echo { margin: 14px 0; padding: 12px; border-radius: var(--r-ctl); background: var(--overlay); border: 1px solid var(--hairline); display: flex; flex-direction: column; gap: 6px; }
.echo-text { margin: 0; font-family: inherit; font-size: 13px; line-height: 1.7; color: var(--t2); white-space: pre-wrap; word-break: break-word; }
.answer-box { padding: 14px; border-radius: var(--r-ctl); background: var(--accent-soft); border: 1px solid var(--accent-line); display: flex; flex-direction: column; gap: 6px; }
.answer-text { font-size: 15px; line-height: 1.7; color: var(--t1); white-space: pre-wrap; }
.steps { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
.step { display: flex; gap: 12px; }
.step-no { flex: none; width: 22px; height: 22px; border-radius: 50%; display: grid; place-items: center; font-size: 11px; font-family: var(--font-mono); color: var(--accent); background: var(--accent-soft); border: 1px solid var(--accent-line); }
.step-body { display: flex; flex-direction: column; gap: 3px; }
.step-title { font-size: 13px; font-weight: 500; color: var(--t1); }
.step-detail { font-size: 13px; line-height: 1.75; color: var(--t2); white-space: pre-wrap; }
.tag-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.practice { margin-top: 18px; }
.practice-card { margin-top: 10px; padding: 12px; border-radius: var(--r-ctl); border: 1px solid var(--hairline); background: var(--overlay); }
.practice-stem { font-size: 13px; line-height: 1.7; color: var(--t1); }
.options { margin: 8px 0 0; padding-left: 18px; font-size: 13px; line-height: 1.7; color: var(--t2); }
.practice-answer { margin-top: 8px; font-size: 13px; line-height: 1.75; color: var(--t2); border-top: 1px dashed var(--hairline); padding-top: 8px; }
.gen-wait { display: flex; flex-direction: column; gap: 8px; margin-top: 16px; font-size: 12px; }

@media (max-width: 900px) {
  .tutor-wrap { grid-template-columns: minmax(0, 1fr); }
  .thread-side { position: static; max-height: none; }
  .thread-list { display: flex; gap: 6px; overflow-x: auto; }
  .thread-item { flex: none; width: 190px; }
}
</style>
