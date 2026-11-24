<template>
  <div class="register-page">
    <div class="register-box panel">
      <h1>创建学习账号</h1>
      <p class="intro">规划目标、记录任务，学习数据按账号独立保存。</p>
      <p v-if="loadingConfig" role="status">正在检查注册状态…</p>
      <el-alert v-else-if="configError" title="暂时无法获取注册状态，请稍后重试" type="error" :closable="false" />
      <el-alert v-else-if="!enabled" title="当前暂未开放注册，请联系工作台管理员" type="info" :closable="false" />
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3–64 位字母、数字或 _ . -" autocomplete="username" maxlength="64" />
        </el-form-item>
        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="你希望显示的名字" autocomplete="nickname" maxlength="64" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" autocomplete="new-password" maxlength="72" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" autocomplete="new-password" maxlength="72" />
        </el-form-item>
        <el-button class="submit-btn" type="primary" native-type="submit" :loading="submitting">创建账号</el-button>
      </el-form>
      <p class="foot">已有账号？<router-link to="/login">返回登录</router-link></p>
      <el-button v-if="configError" text @click="loadConfig">重试</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { authApi } from '@/api';

const router = useRouter();
const formRef = ref<FormInstance>();
const enabled = ref(false);
const loadingConfig = ref(true);
const configError = ref(false);
const submitting = ref(false);
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' });
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '用户名需为 3–64 位字母、数字或 _ . -', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少需要 8 位', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
      callback(new TextEncoder().encode(value).length > 72 ? new Error('密码过长，请缩短后重试（中文会占用更多长度）') : undefined);
    }, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => {
      callback(value !== form.password ? new Error('两次输入的密码不一致') : undefined);
    }, trigger: 'blur' },
  ],
};

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
    await authApi.register({ username: form.username, password: form.password, nickname: form.nickname.trim() || undefined });
    form.password = '';
    form.confirmPassword = '';
    ElMessage.success('账号创建成功，请登录');
    await router.replace('/login');
  } catch {
    // 请求拦截器已显示错误；保留表单供修正后重试。
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.register-page { min-height: 100%; display: flex; align-items: center; justify-content: center; padding: 20px; background: var(--bg-base); }
.register-box { width: 420px; max-width: 100%; padding: 28px; }
h1 { margin: 0; font-size: 22px; color: var(--accent); }
.intro { margin: 10px 0 24px; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.submit-btn { width: 100%; margin-top: 8px; }
.foot { margin: 20px 0 0; text-align: center; color: var(--text-muted); font-size: 13px; }
@media (max-width: 767px) { .register-box { padding: 22px; } }
</style>
