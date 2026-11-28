<template>
  <div class="admin-page">
    <!-- 概览 -->
    <div class="stats">
      <div class="stat">
        <div class="stat-label">注册用户</div>
        <div class="stat-value">{{ users.length }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">任务总数</div>
        <div class="stat-value">{{ totals.taskTotal }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">已完成</div>
        <div class="stat-value accent">{{ totals.taskFinished }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">图片 / 已归档</div>
        <div class="stat-value">{{ totals.imageCount }} <span class="dim">/ {{ totals.archivedCount }}</span></div>
      </div>
    </div>

    <!-- 用户列表 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="block-head">
          <span>用户列表</span>
          <span class="hint">点击任意一行查看该用户的任务与图片</span>
          <div class="spacer"></div>
          <el-button size="small" :loading="loadingUsers" @click="refreshAll">刷新</el-button>
          <el-button size="small" @click="openAdminDialog">管理员白名单</el-button>
        </div>
      </template>
      <el-table
        v-loading="loadingUsers"
        :data="users"
        highlight-current-row
        size="small"
        empty-text="暂无注册用户"
        @row-click="selectUser"
      >
        <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
        <el-table-column label="昵称" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="130" />
        <el-table-column prop="lastSignIn" label="最后登录" width="130">
          <template #default="{ row }">{{ row.lastSignIn || '从未' }}</template>
        </el-table-column>
        <el-table-column label="任务完成" width="110">
          <template #default="{ row }">
            <span :class="{ accent: row.taskFinished > 0 }">{{ row.taskFinished }}</span> / {{ row.taskTotal }}
          </template>
        </el-table-column>
        <el-table-column label="完成率" width="90">
          <template #default="{ row }">{{ row.completionRate }}%</template>
        </el-table-column>
        <el-table-column label="逾期" width="70">
          <template #default="{ row }">
            <span :class="{ danger: row.overdueCount > 0 }">{{ row.overdueCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            {{ row.imageCount }} <span class="dim">/ 归档 {{ row.archivedCount }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 选中用户详情 -->
    <el-card v-if="selected" shadow="never" class="block">
      <template #header>
        <div class="block-head">
          <span>{{ selected.email }}</span>
          <span class="hint">{{ selected.nickname || '未设置昵称' }}</span>
          <div class="spacer"></div>
          <el-button size="small" :loading="loadingDetail" @click="loadDetail(selected)">刷新</el-button>
          <el-button size="small" text @click="selected = null">收起</el-button>
        </div>
      </template>

      <el-tabs v-model="tab">
        <!-- 任务明细 -->
        <el-tab-pane :label="`任务明细 (${tasks.length})`" name="tasks">
          <el-table v-loading="loadingDetail" :data="tasks" size="small" empty-text="该用户还没有任务">
            <el-table-column prop="taskDate" label="日期" width="105" />
            <el-table-column prop="title" label="任务内容" min-width="160" show-overflow-tooltip />
            <el-table-column prop="goalName" label="目标" width="130" show-overflow-tooltip />
            <el-table-column prop="subjectName" label="科目" width="110" show-overflow-tooltip />
            <el-table-column prop="weight" label="权重" width="70" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.finishStatus === 1 ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.finishStatus === 1 ? '已完成' : '未完成' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="掌握度" width="85">
              <template #default="{ row }">{{ row.mastery ? row.mastery + '/5' : '—' }}</template>
            </el-table-column>
            <el-table-column label="逾期" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.overdue" type="danger" size="small" effect="plain">逾期</el-tag>
                <span v-else class="dim">—</span>
              </template>
            </el-table-column>
            <el-table-column label="图片" width="70">
              <template #default="{ row }">{{ row.imageCount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="finishedAt" label="完成时间" width="130">
              <template #default="{ row }">{{ row.finishedAt || '—' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 图片墙 -->
        <el-tab-pane :label="`提交图片 (${images.length})`" name="images">
          <div class="toolbar">
            <el-checkbox v-model="includeExpired" @change="selected && loadDetail(selected)">
              包含已过期（未归档）的图片
            </el-checkbox>
            <span class="hint">未归档图片在上传 15 天后由清理任务删除；归档后长期保留。</span>
          </div>
          <div v-loading="loadingDetail" class="wall">
            <el-empty v-if="!images.length" description="该用户还没有上传过图片" />
            <div v-for="img in images" :key="img.id" class="thumb-card">
              <div class="thumb-box">
                <el-image
                  v-if="urls[img.id]"
                  :src="urls[img.id]"
                  :preview-src-list="previewList"
                  :initial-index="Math.max(0, images.findIndex((i) => i.id === img.id))"
                  fit="cover"
                  preview-teleported
                  class="thumb"
                />
                <div v-else class="thumb-missing">链接已失效</div>
                <el-tag v-if="img.archived" class="badge" type="warning" size="small" effect="dark">已归档</el-tag>
              </div>
              <div class="thumb-meta">
                <div class="thumb-title" :title="img.originalName">{{ img.originalName }}</div>
                <div class="thumb-sub">{{ img.taskTitle || '任务已删除' }}</div>
                <div class="thumb-sub">
                  上传 {{ img.uploadTime }}
                  <template v-if="!img.archived">· 剩余 {{ remainDays(img.expireTime) }} 天</template>
                  <template v-else>· 长期保留</template>
                </div>
              </div>
              <div class="thumb-actions">
                <el-button
                  v-if="!img.archived"
                  size="small"
                  type="primary"
                  plain
                  :loading="busyId === img.id"
                  @click="doArchive(img)"
                >
                  归档
                </el-button>
                <el-button
                  v-else
                  size="small"
                  plain
                  :loading="busyId === img.id"
                  @click="doUnarchive(img)"
                >
                  取消归档
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :loading="busyId === img.id"
                  @click="doRemove(img)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 管理员白名单 -->
    <el-dialog v-model="adminDialog" title="管理员白名单" width="min(640px, 94vw)">
      <p class="dialog-tip">
        只有名单内的账号能进入后台。添加时对方必须已经用该邮箱注册过。
      </p>
      <div class="add-row">
        <el-input v-model="newAdminEmail" placeholder="输入已注册的邮箱" clearable @keyup.enter="doAddAdmin" />
        <el-button type="primary" :loading="addingAdmin" @click="doAddAdmin">添加</el-button>
      </div>
      <el-table :data="admins" size="small" empty-text="暂无管理员">
        <el-table-column prop="email" label="邮箱" min-width="190" />
        <el-table-column prop="note" label="备注" width="120">
          <template #default="{ row }">{{ row.note || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="加入时间" width="140" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="doRemoveAdmin(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '@/api';
import type { AdminUserVO, AdminImageVO, AdminEntryVO, TaskVO } from '@/types';

// 路由守卫已确认管理员身份，此处断言非空；Java 部署模式下 adminApi 为 null，页面不会进入。
const api = adminApi as NonNullable<typeof adminApi>;

const users = ref<AdminUserVO[]>([]);
const loadingUsers = ref(false);
const selected = ref<AdminUserVO | null>(null);
const tasks = ref<TaskVO[]>([]);
const images = ref<AdminImageVO[]>([]);
const urls = reactive<Record<number, string>>({});
const loadingDetail = ref(false);
const tab = ref('tasks');
const includeExpired = ref(false);
const busyId = ref<number | null>(null);

const adminDialog = ref(false);
const admins = ref<AdminEntryVO[]>([]);
const newAdminEmail = ref('');
const addingAdmin = ref(false);

const totals = computed(() =>
  users.value.reduce(
    (acc, u) => ({
      taskTotal: acc.taskTotal + u.taskTotal,
      taskFinished: acc.taskFinished + u.taskFinished,
      imageCount: acc.imageCount + u.imageCount,
      archivedCount: acc.archivedCount + u.archivedCount,
    }),
    { taskTotal: 0, taskFinished: 0, imageCount: 0, archivedCount: 0 },
  ),
);

const previewList = computed(() =>
  images.value.map((i) => urls[i.id]).filter((u): u is string => Boolean(u)),
);

function remainDays(expire: string): number {
  const diff = Date.parse(expire) - Date.now();
  return Math.max(0, Math.ceil(diff / 86400000));
}

async function loadUsers() {
  loadingUsers.value = true;
  try {
    users.value = await api.users();
  } finally {
    loadingUsers.value = false;
  }
}

async function loadDetail(user: AdminUserVO) {
  loadingDetail.value = true;
  try {
    const [taskPage, imageList] = await Promise.all([
      api.tasks(user.userId, 1, 200),
      api.images(user.userId, includeExpired.value),
    ]);
    tasks.value = taskPage.list;
    images.value = imageList;
    Object.keys(urls).forEach((k) => delete urls[Number(k)]);
    const signed = await api.signUrls(imageList);
    imageList.forEach((img, i) => {
      if (signed[i]) urls[img.id] = signed[i] as string;
    });
  } finally {
    loadingDetail.value = false;
  }
}

async function selectUser(row: AdminUserVO) {
  selected.value = row;
  tab.value = 'tasks';
  await loadDetail(row);
}

async function refreshAll() {
  await loadUsers();
  if (selected.value) {
    const fresh = users.value.find((u) => u.userId === selected.value?.userId);
    if (fresh) {
      selected.value = fresh;
      await loadDetail(fresh);
    }
  }
}

async function doArchive(img: AdminImageVO) {
  busyId.value = img.id;
  try {
    await api.archive(img.id);
    ElMessage.success('已归档，这张图片将长期保留');
    if (selected.value) await loadDetail(selected.value);
    await loadUsers();
  } catch {
    // 错误已由 api 层提示
  } finally {
    busyId.value = null;
  }
}

async function doUnarchive(img: AdminImageVO) {
  busyId.value = img.id;
  try {
    await api.unarchive(img.id);
    ElMessage.success('已取消归档，重新开始 15 天倒计时');
    if (selected.value) await loadDetail(selected.value);
    await loadUsers();
  } catch {
    // 错误已由 api 层提示
  } finally {
    busyId.value = null;
  }
}

async function doRemove(img: AdminImageVO) {
  try {
    await ElMessageBox.confirm(`确定彻底删除「${img.originalName}」？此操作不可恢复。`, '删除图片', {
      type: 'warning',
    });
  } catch {
    return;
  }
  busyId.value = img.id;
  try {
    await api.removeImage(img.id);
    ElMessage.success('已删除');
    if (selected.value) await loadDetail(selected.value);
    await loadUsers();
  } catch {
    // 错误已由 api 层提示
  } finally {
    busyId.value = null;
  }
}

async function openAdminDialog() {
  adminDialog.value = true;
  admins.value = await api.admins();
}

async function doAddAdmin() {
  const email = newAdminEmail.value.trim();
  if (!email) return;
  addingAdmin.value = true;
  try {
    await api.addAdmin(email);
    ElMessage.success('已加入管理员白名单');
    newAdminEmail.value = '';
    admins.value = await api.admins();
  } catch {
    // 错误已由 api 层提示
  } finally {
    addingAdmin.value = false;
  }
}

async function doRemoveAdmin(row: AdminEntryVO) {
  try {
    await ElMessageBox.confirm(`确定移除管理员「${row.email}」？`, '移除管理员', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await api.removeAdmin(row.userId);
    ElMessage.success('已移除');
    admins.value = await api.admins();
  } catch {
    // 错误已由 api 层提示
  }
}

onMounted(async () => {
  await loadUsers();
  // 只有一名用户时直接展开，省一次点击
  if (users.value.length === 1) await selectUser(users.value[0]);
});
</script>

<style scoped>
.admin-page { display: flex; flex-direction: column; gap: 12px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat { background: var(--bg-panel); border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; }
.stat-label { font-size: 12px; color: var(--text-muted); margin-bottom: 6px; }
.stat-value { font-size: 22px; font-weight: 700; }
.accent { color: var(--accent); }
.danger { color: var(--danger); }
.dim { color: var(--text-muted); font-size: 12px; font-weight: 400; }
.block { background: var(--bg-panel); border: 1px solid var(--border); }
.block-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.block-head .hint { font-size: 12px; color: var(--text-muted); }
.spacer { flex: 1; }
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.toolbar .hint { font-size: 12px; color: var(--text-muted); }
.wall { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; }
.thumb-card { border: 1px solid var(--border); border-radius: 8px; overflow: hidden; background: var(--bg-base); }
.thumb-box { position: relative; height: 140px; }
.thumb { width: 100%; height: 140px; display: block; cursor: zoom-in; }
.thumb-missing { height: 100%; display: flex; align-items: center; justify-content: center; color: var(--text-muted); font-size: 12px; }
.badge { position: absolute; top: 6px; right: 6px; }
.thumb-meta { padding: 8px 10px 4px; }
.thumb-title { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.thumb-sub { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.thumb-actions { display: flex; gap: 6px; padding: 6px 10px 10px; }
.dialog-tip { font-size: 12px; color: var(--text-muted); margin: 0 0 10px; }
.add-row { display: flex; gap: 8px; margin-bottom: 12px; }
@media (max-width: 767px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .wall { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .thumb-box, .thumb { height: 110px; }
}
</style>
