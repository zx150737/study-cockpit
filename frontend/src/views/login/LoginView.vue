<template>
  <div class="auth-page canvas-glow">
    <ThemeToggle floating />
    <div class="auth-card">
      <!-- 品牌主张侧 -->
      <section class="auth-brand">
        <div class="brand-lockup">
          <BrandMark :size="24" />
          <span class="t-label">Study Cockpit</span>
        </div>
        <h1 class="t-hero brand-title">
          把每天的学习<br />变成<em>可读的数据</em>。
        </h1>
        <div class="brand-metrics">
          <div class="bm">
            <div class="bm-value"><span class="num">3</span><span class="bm-unit">条主线</span></div>
            <div class="bm-label">目标 · 科目 · 任务</div>
          </div>
          <div class="bm">
            <div class="bm-value"><span class="num">7</span><span class="bm-unit">天</span></div>
            <div class="bm-label">周度结算节奏</div>
          </div>
          <div class="bm">
            <div class="bm-value"><span class="num">15</span><span class="bm-unit">天</span></div>
            <div class="bm-label">图片留存窗口</div>
          </div>
        </div>
      </section>

      <!-- 表单侧 -->
      <section class="auth-form">
        <h2 class="t-h2 form-title">登录</h2>
        <p class="form-sub">继续你的学习计划</p>

        <el-form :model="form" @keyup.enter="submit">
          <el-form-item :label="isCloud ? '邮箱' : '用户名'">
            <el-input
              v-model="form.username"
              :placeholder="isCloud ? 'you@example.com' : '你的用户名'"
              :type="isCloud ? 'email' : 'text'"
              size="large"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="你的密码"
              size="large"
              autocomplete="current-password"
            />
          </el-form-item>
          <el-button class="submit-btn" type="primary" size="large" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <div v-if="registrationEnabled" class="form-foot">
          还没有账号？<router-link to="/register">创建账号</router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '@/api';
import { useUserStore } from '@/stores/user';
import { isCloud } from '@/cloud/client';
import BrandMark from '@/components/BrandMark.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';

const route = useRoute();
const router = useRouter();
const store = useUserStore();
const loading = ref(false);
const registrationEnabled = ref(false);
const form = reactive({ username: '', password: '' });

onMounted(async () => {
  try {
    await authApi.csrf();
    registrationEnabled.value = (await authApi.registration()).enabled;
  } catch {
    // 网络异常时登录会再次给出明确提示
  }
});

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning(isCloud ? '请输入邮箱和密码' : '请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const user = await authApi.login({ username: form.username.trim(), password: form.password });
    store.setUser(user);
    router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard');
  } catch {
    // 请求拦截器已显示错误，留在登录页允许再次输入。
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--canvas);
}

.auth-card {
  width: 1040px;
  max-width: 100%;
  display: grid;
  grid-template-columns: 1.12fr 0.88fr;
  min-height: 460px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-panel);
  overflow: hidden;
  box-shadow: var(--sh-float), var(--sh-inset);
}

/* 品牌侧 */
.auth-brand {
  padding: 44px 40px;
  display: flex;
  flex-direction: column;
  position: relative;
}
.brand-lockup {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-title {
  margin-top: 28px;
}
.brand-title em {
  font-style: normal;
  color: var(--accent);
}
.brand-metrics {
  display: flex;
  gap: 28px;
  margin-top: auto;
  padding-top: 40px;
}
.bm-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.bm-value .num {
  font-size: 28px;
  font-weight: 500;
  color: var(--t1);
  line-height: 1;
}
.bm-unit {
  font-size: var(--fs-label);
  color: var(--t3);
  letter-spacing: 0.06em;
}
.bm-label {
  font-size: var(--fs-label);
  color: var(--t3);
  margin-top: 6px;
  letter-spacing: 0.04em;
}

/* 表单侧 */
.auth-form {
  border-left: 1px solid var(--hairline);
  background: linear-gradient(180deg, var(--overlay), transparent);
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.form-title {
  letter-spacing: -0.01em;
}
.form-sub {
  margin: 6px 0 28px;
  color: var(--t3);
  font-size: var(--fs-body);
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
  letter-spacing: 0.14em;
}
.form-foot {
  margin: 22px 0 0;
  text-align: center;
  color: var(--t3);
  font-size: var(--fs-body);
}
.form-foot a {
  color: var(--accent);
  border-bottom: 1px solid var(--accent-line);
  padding-bottom: 1px;
}

@media (max-width: 767px) {
  .auth-card {
    grid-template-columns: 1fr;
    min-height: 0;
  }
  .auth-brand {
    padding: 28px 24px 0;
  }
  .brand-title {
    font-size: var(--fs-h1);
  }
  .brand-metrics {
    display: none;
  }
  .auth-form {
    border-left: none;
    border-top: 1px solid var(--hairline);
    margin-top: 24px;
    padding: 28px 24px;
  }
}
</style>
