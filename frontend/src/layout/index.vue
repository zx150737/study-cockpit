<template>
  <el-container class="app-shell canvas-glow">
    <!-- PC 侧栏 -->
    <el-aside v-if="!isMobile" width="210px" class="side">
      <div class="brand">
        <BrandMark :size="20" />
        <span class="brand-name">学习监控驾驶舱</span>
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
        <BrandMark :size="20" />
        <span class="brand-name">学习监控驾驶舱</span>
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
        <el-button
          text
          class="theme-btn"
          :title="theme.isDark ? '切换到浅色模式' : '切换到深色模式'"
          @click="theme.toggle()"
        >
          <el-icon><Sunny v-if="theme.isDark" /><Moon v-else /></el-icon>
        </el-button>
        <el-button text @click="pwdDialog = true">修改密码</el-button>
        <el-button text @click="doLogout">退出</el-button>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>

    <!-- 专注计时器：跨页面常驻 -->
    <FocusTimer />

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdDialog" title="修改密码" width="min(380px, 92vw)" :close-on-click-modal="!pwdSubmitting" :close-on-press-escape="!pwdSubmitting" :show-close="!pwdSubmitting" @closed="resetPwdForm">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="110px">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="pwdSubmitting" @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitPwd">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { DataAnalysis, Aim, List, TrendCharts, FolderOpened, Menu, Setting, MagicStick, Collection, Sunny, Moon } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { useThemeStore } from '@/stores/theme';
import { authApi, notificationApi, adminApi } from '@/api';
import BrandMark from '@/components/BrandMark.vue';
import FocusTimer from '@/components/FocusTimer.vue';
import { weakPasswordReason } from '@/utils/password';

const route = useRoute();
const router = useRouter();
const store = useUserStore();
const theme = useThemeStore();

const isAdmin = ref(false);

const menus = computed(() => {
  const base = [
    { path: '/dashboard', title: '主驾驶舱', icon: DataAnalysis },
    { path: '/tutor', title: 'AI 学习助手', icon: MagicStick },
    { path: '/review', title: '错题复习', icon: Collection },
    { path: '/goals', title: '目标 & 科目', icon: Aim },
    { path: '/tasks', title: '任务管理', icon: List },
    { path: '/weekly', title: '周度分析', icon: TrendCharts },
    { path: '/archive', title: '历史归档', icon: FolderOpened },
  ];
  return isAdmin.value ? [...base, { path: '/admin', title: '后台管理', icon: Setting }] : base;
});

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
  try {
    isAdmin.value = adminApi ? await adminApi.isAdmin() : false;
  } catch {
    isAdmin.value = false;
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

// ---------- 修改密码 ----------
const pwdDialog = ref(false);
const pwdSubmitting = ref(false);
const pwdFormRef = ref<FormInstance>();
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

const validateConfirm = (_: unknown, value: string, callback: (err?: string) => void) => {
  if (value !== pwdForm.newPassword) {
    callback('两次输入的新密码不一致');
  } else {
    callback();
  }
};

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '新密码长度需在 8~72 之间', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        const weak = weakPasswordReason(value, store.user?.username);
        callback(weak ? new Error(weak) : undefined);
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
};

async function submitPwd() {
  if (!pwdFormRef.value) return;
  const valid = await pwdFormRef.value.validate().catch(() => false);
  if (!valid) return;
  pwdSubmitting.value = true;
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    });
    ElMessage.success('密码已修改，请重新登录');
    pwdDialog.value = false;
    store.clearUser();
    router.replace('/login');
  } catch {
    // 请求拦截器已显示错误，保留表单供用户修正后重试。
  } finally {
    pwdSubmitting.value = false;
  }
}

function resetPwdForm() {
  pwdFormRef.value?.resetFields();
  pwdForm.oldPassword = '';
  pwdForm.newPassword = '';
  pwdForm.confirmPassword = '';
}
</script>

<style scoped>
.app-shell { height: 100%; background: var(--canvas); }
.side { background: var(--surface); border-right: 1px solid var(--hairline); display: flex; flex-direction: column; }
.brand { height: 56px; display: flex; align-items: center; gap: 9px; padding: 0 16px; border-bottom: 1px solid var(--hairline); white-space: nowrap; }
.brand-name { font-weight: 600; color: var(--t1); letter-spacing: 0.01em; }
.side-menu { flex: 1; border-right: none; background: transparent; padding-top: 8px; }
.side-foot { padding: 12px 16px; font-size: var(--fs-label); letter-spacing: 0.08em; color: var(--t3); border-top: 1px solid var(--hairline); font-family: var(--font-mono); }
.head { display: flex; align-items: center; gap: 12px; background: var(--surface); border-bottom: 1px solid var(--hairline); }
.page-title { font-weight: 500; font-size: var(--fs-h2); letter-spacing: -0.01em; }
.spacer { flex: 1; }
.user-name { color: var(--text-muted); font-size: 13px; }
.main { padding: 16px; overflow: auto; background: var(--bg-base); }
@media (max-width: 767px) {
  .main { padding: 10px; }
  .user-name { display: none; }
}
</style>
