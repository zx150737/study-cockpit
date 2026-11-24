<template>
  <el-container class="app-shell">
    <!-- PC 侧栏 -->
    <el-aside v-if="!isMobile" width="210px" class="side">
      <div class="brand">
        <span class="brand-mark">▣</span>
        <span>学习监控驾驶舱</span>
      </div>
      <el-menu class="side-menu" router :default-active="route.path" :collapse="false">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
      <div class="side-foot">UTC+8 · 周一起始</div>
    </el-aside>

    <!-- 移动端抽屉 -->
    <el-drawer v-else v-model="drawer" direction="ltr" size="220px" :with-header="false">
      <div class="brand">
        <span class="brand-mark">▣</span>
        <span>学习监控驾驶舱</span>
      </div>
      <el-menu class="side-menu" router :default-active="route.path" @select="drawer = false">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="head" height="56px">
        <el-button v-if="isMobile" text class="menu-btn" @click="drawer = true">
          <el-icon><Menu /></el-icon>
        </el-button>
        <span class="page-title">{{ route.meta.title || '学习监控驾驶舱' }}</span>
        <div class="spacer"></div>
        <el-tag v-if="overdueTotal > 0" type="danger" effect="dark" size="small">
          积压 {{ overdueTotal }}
        </el-tag>
        <span class="user-name">{{ store.user?.nickname || store.user?.username }}</span>
        <el-button text @click="doLogout">退出</el-button>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { DataAnalysis, Aim, List, TrendCharts, FolderOpened, Menu } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { notificationApi } from '@/api';

const route = useRoute();
const router = useRouter();
const store = useUserStore();

const menus = [
  { path: '/dashboard', title: '主驾驶舱', icon: DataAnalysis },
  { path: '/goals', title: '目标 & 科目', icon: Aim },
  { path: '/tasks', title: '任务管理', icon: List },
  { path: '/weekly', title: '周度分析', icon: TrendCharts },
  { path: '/archive', title: '历史归档', icon: FolderOpened },
];

const width = ref(window.innerWidth);
const isMobile = computed(() => width.value < 768);
const drawer = ref(false);
const overdueTotal = ref(0);

const onResize = () => { width.value = window.innerWidth; };

onMounted(async () => {
  window.addEventListener('resize', onResize);
  try {
    const vo = await notificationApi.overdue();
    overdueTotal.value = vo.total;
  } catch {
    // 预警拉取失败不阻塞页面
  }
});

onBeforeUnmount(() => window.removeEventListener('resize', onResize));

async function doLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await store.logout();
  router.replace('/login');
}
</script>

<style scoped>
.app-shell { height: 100%; background: var(--bg-base); }
.side { background: var(--bg-panel); border-right: 1px solid var(--border); display: flex; flex-direction: column; }
.brand { height: 56px; display: flex; align-items: center; gap: 8px; padding: 0 16px; font-weight: 700; color: var(--accent); border-bottom: 1px solid var(--border); white-space: nowrap; }
.brand-mark { font-size: 18px; }
.side-menu { flex: 1; border-right: none; background: transparent; }
.side-foot { padding: 12px 16px; font-size: 11px; color: var(--text-muted); border-top: 1px solid var(--border); }
.head { display: flex; align-items: center; gap: 12px; background: var(--bg-panel); border-bottom: 1px solid var(--border); }
.page-title { font-weight: 600; }
.spacer { flex: 1; }
.user-name { color: var(--text-muted); font-size: 13px; }
.main { padding: 16px; overflow: auto; background: var(--bg-base); }
@media (max-width: 767px) {
  .main { padding: 10px; }
  .user-name { display: none; }
}
</style>
