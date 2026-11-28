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
          创建属于自己的<br /><em>学习空间</em>。
        </h1>
        <p class="brand-sub">规划目标、记录任务，数据按账号独立保存。</p>
        <div class="brand-metrics">
          <div class="bm">
            <div class="bm-value"><span class="num">01</span></div>
            <div class="bm-label">定目标</div>
          </div>
          <div class="bm">
            <div class="bm-value"><span class="num">02</span></div>
            <div class="bm-label">记任务</div>
          </div>
          <div class="bm">
            <div class="bm-value"><span class="num">03</span></div>
            <div class="bm-label">看复盘</div>
          </div>
        </div>
      </section>

      <!-- 表单侧 -->
      <section class="auth-form">
        <h2 class="t-h2 form-title">创建账号</h2>
        <p class="form-sub">一个邮箱即可开始</p>

        <p v-if="loadingConfig" class="dim" role="status">正在检查注册状态…</p>
        <el-alert v-else-if="configError" title="暂时无法获取注册状态，请稍后重试" type="error" :closable="false" />
        <el-alert v-else-if="!enabled" title="当前暂未开放注册，请联系工作台管理员" type="info" :closable="false" />

        <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item :label="isCloud ? '邮箱 · 必填' : '用户名 · 必填'" prop="username">
            <el-input v-model="form.username" :placeholder="isCloud ? 'you@example.com' : '3–64 位字母、数字或 _ . -'" :type="isCloud ? 'email' : 'text'" size="large" autocomplete="username" :maxlength="isCloud ? 254 : 64" />
          </el-form-item>
          <el-form-item label="昵称 · 选填" prop="nickname">
            <el-input v-model="form.nickname" placeholder="你希望显示的名字" size="large" autocomplete="nickname" maxlength="64" />
          </el-form-item>
          <el-form-item label="密码 · 必填" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" size="large" autocomplete="new-password" maxlength="72" />
            <div class="strength" aria-hidden="true">
              <i v-for="n in 3" :key="n" :class="{ on: n <= strength }"></i>
              <span>{{ strengthText }}</span>
            </div>
          </el-form-item>
          <el-form-item label="确认密码 · 必填" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" size="large" autocomplete="new-password" maxlength="72" />
          </el-form-item>
          <el-button class="submit-btn" type="primary" size="large" native-type="submit" :loading="submitting">创建账号</el-button>
        </el-form>

        <div v-if="!loadingConfig && !configError" class="form-foot">
          已有账号？<router-link to="/login">返回登录</router-link>
        </div>
        <el-button v-if="configError" text class="retry" @click="loadConfig">重试</el-button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { authApi } from '@/api';
import { useUserStore } from '@/stores/user';
import { isCloud } from '@/cloud/client';
import BrandMark from '@/components/BrandMark.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';
import { weakPasswordReason, passwordStrength } from '@/utils/password';

const router = useRouter();
const store = useUserStore();
const formRef = ref<FormInstance>();
const enabled = ref(false);
const loadingConfig = ref(true);
const configError = ref(false);
const submitting = ref(false);
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' });

const rules: FormRules = {
  username: isCloud ? [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ] : [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '用户名需为 3–64 位字母、数字或 _ . -', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少需要 8 位', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
      if (new TextEncoder().encode(value).length > 72) {
        return callback(new Error('密码过长，请缩短后重试（中文会占用更多长度）'));
      }
      const weak = weakPasswordReason(value, form.username);
      return callback(weak ? new Error(weak) : undefined);
    }, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
      callback(value !== form.password ? new Error('两次输入的密码不一致') : undefined);
    }, trigger: 'blur' },
  ],
};

// 密码强度：三段细条，只做视觉提示，不改变校验逻辑
const strength = computed(() => passwordStrength(form.password));
const strengthText = computed(() => ['', '偏弱', '尚可', '较强'][strength.value]);

async function loadConfig() {
  loadingConfig.value = true;
  configError.value = false;
  try {
    await authApi.csrf();
    enabled.value = (await authApi.registration()).enabled;
  } catch {
    configError.value = true;
  } finally {
    loadingConfig.value = false;
  }
}
onMounted(loadConfig);

async function submit() {
  if (submitting.value || !enabled.value || !formRef.value) return;
  if (!await formRef.value.validate().catch(() => false)) return;
  submitting.value = true;
  try {
    const result = await authApi.register({ username: form.username, password: form.password, nickname: form.nickname.trim() || undefined });
    form.password = '';
    form.confirmPassword = '';
    if ('autoLogin' in result && result.autoLogin) {
      await store.fetchMe();
      ElMessage.success('账号创建成功，已为你登录');
      await router.replace('/dashboard');
    } else {
      ElMessage.success('confirmationRequired' in result && result.confirmationRequired ? '请查看邮箱完成验证，然后登录' : '账号创建成功，请登录');
      await router.replace('/login');
    }
  } catch {
    // 请求拦截器已显示错误；保留表单供修正后重试。
  } finally {
    submitting.value = false;
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
.brand-sub {
  margin: 18px 0 0;
  color: var(--t2);
  font-size: var(--fs-body);
  max-width: 30em;
}
.brand-metrics {
  display: flex;
  gap: 28px;
  margin-top: auto;
  padding-top: 40px;
}
.bm-value .num {
  font-size: 28px;
  font-weight: 500;
  color: var(--t1);
  line-height: 1;
}
.bm-label {
  font-size: var(--fs-label);
  color: var(--t3);
  margin-top: 6px;
  letter-spacing: 0.04em;
}

.auth-form {
  border-left: 1px solid var(--hairline);
  background: linear-gradient(180deg, var(--overlay), transparent);
  padding: 44px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.form-title {
  letter-spacing: -0.01em;
}
.form-sub {
  margin: 6px 0 24px;
  color: var(--t3);
  font-size: var(--fs-body);
}
.strength {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
}
.strength i {
  flex: 1;
  height: 2px;
  border-radius: var(--r-pill);
  background: var(--track);
}
.strength i.on {
  background: var(--accent);
}
.strength span {
  width: 34px;
  text-align: right;
  font-size: var(--fs-label);
  color: var(--t3);
  letter-spacing: 0.04em;
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
.retry {
  margin-top: 8px;
  align-self: center;
}
.dim {
  color: var(--t3);
  font-size: var(--fs-body);
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
