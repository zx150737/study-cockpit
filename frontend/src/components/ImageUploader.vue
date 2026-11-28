<template>
  <div>
    <el-upload
      :show-file-list="false"
      :http-request="customUpload"
      :before-upload="beforeUpload"
      accept=".jpg,.jpeg,.png,.webp"
    >
      <el-button size="small" type="primary" :loading="uploading">上传图片</el-button>
    </el-upload>

    <div v-if="images.length" class="thumb-list">
      <div v-for="img in images" :key="img.id" class="thumb">
        <!-- 仅展示经过账号鉴权取得的临时图片地址。 -->
        <img :src="img.temporaryUrl" :alt="img.originalName" />
        <div class="thumb-meta" :title="img.originalName">{{ img.originalName }}</div>
        <div class="thumb-meta text-muted">到期 {{ img.expireTime?.slice(0, 10) }}</div>
        <el-button link type="danger" size="small" @click="remove(img.id)">删除</el-button>
      </div>
    </div>
    <div v-else class="text-muted tip">暂无配套图片</div>
    <div class="text-muted tip">仅 jpg / png / webp，单文件 ≤ 5MB，上传起 15 天后自动清理</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { imageApi } from '@/api';
import type { ImageVO } from '@/types';

const props = defineProps<{ taskId: number }>();
const images = ref<ImageVO[]>([]);
const uploading = ref(false);

async function load(): Promise<void> {
  if (!props.taskId) {
    images.value = [];
    return;
  }
  try {
    images.value = await imageApi.list(props.taskId);
  } catch {
    images.value = [];
  }
}

function beforeUpload(file: File): boolean {
  const ok = /\.(jpg|jpeg|png|webp)$/i.test(file.name) && file.size <= 5 * 1024 * 1024;
  if (!ok) ElMessage.error('仅支持 jpg/png/webp，且单文件不超过 5MB');
  return ok;
}

// 两种部署方式各自携带登录凭据，由对应服务验证文件与任务归属。
async function customUpload(options: { file: File; onSuccess: () => void; onError: (e: unknown) => void }): Promise<void> {
  uploading.value = true;
  try {
    await imageApi.upload(props.taskId, options.file);
    options.onSuccess();
    await load();
    ElMessage.success('上传成功');
  } catch (e) {
    options.onError(e);
  } finally {
    uploading.value = false;
  }
}

async function remove(id: number): Promise<void> {
  await imageApi.remove(id);
  await load();
  ElMessage.success('已删除');
}

watch(() => props.taskId, load, { immediate: true });
</script>

<style scoped>
.thumb-list { display: flex; gap: 10px; flex-wrap: wrap; margin: 12px 0; }
.thumb { width: 120px; border: 1px solid var(--border); border-radius: 4px; padding: 6px; background: var(--bg-panel-2); }
.thumb img { width: 100%; height: 80px; object-fit: cover; border-radius: 2px; }
.thumb-meta { font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
.tip { font-size: 11px; margin-top: 6px; }
</style>
