<template>
  <div class="login-page">
    <div class="login-box panel">
      <div class="login-title">学习监控驾驶舱</div>
      <div class="login-sub">安全认证 · 全链路 UTC+8</div>
      <el-form :model="form" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码"
                    :prefix-icon="Lock" size="large" autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
      <div class="login-foot">个人独立部署 · 数据仅本人可见</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import { authApi } from '@/api';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const store = useUserStore();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

// 先取 CSRF Cookie，Axios 会在随后的 POST 自动带上 X-XSRF-TOKEN
onMounted(async () => {
  try {
    await authApi.csrf();
  } catch {
    // 网络异常时登录会再次给出明确提示
  }
});

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const user = await authApi.login({ username: form.username.trim(), password: form.password });
    store.setUser(user);
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page { min-height: 100%; display: flex; align-items: center; justify-content: center; padding: 20px; background: var(--bg-base); }
.login-box { width: 360px; max-width: 100%; padding: 28px; }
.login-title { font-size: 20px; font-weight: 700; color: var(--accent); letter-spacing: 1px; }
.login-sub { font-size: 12px; color: var(--text-muted); margin: 6px 0 24px; }
.login-btn { width: 100%; }
.login-foot { margin-top: 18px; font-size: 11px; color: var(--text-muted); text-align: center; }
</style>
