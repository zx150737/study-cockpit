<template>
  <transition name="ft-slide">
    <div v-if="store.isActive" class="focus-timer" :class="{ urgent: isUrgent }">
      <div class="ft-top">
        <span class="ft-badge">
          <span class="dot is-accent"></span>{{ store.paused ? '已暂停' : '专注中' }}
        </span>
        <span class="ft-title" :title="store.taskTitle">{{ store.taskTitle }}</span>
      </div>

      <div class="ft-time num">{{ mmss }}</div>

      <div class="hairline-bar"><i :style="{ width: percent + '%' }"></i></div>

      <div class="ft-actions">
        <el-button size="small" @click="togglePause">{{ store.paused ? '继续' : '暂停' }}</el-button>
        <el-button size="small" @click="store.extend(5)">+5 分</el-button>
        <el-button size="small" text @click="store.stop()">结束</el-button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { ElMessageBox } from 'element-plus';
import { useTimerStore } from '@/stores/timer';
import { useUserStore } from '@/stores/user';

const store = useTimerStore();
const userStore = useUserStore();

const now = ref(Date.now());
let ticker: number | undefined;
let titleTimer: number | undefined;
let audioCtx: AudioContext | null = null;
let fired = false;

const remainingMs = computed(() => store.remainingMs(now.value));
const percent = computed(() => {
  if (!store.totalMs) return 0;
  return Math.max(0, Math.min(100, (remainingMs.value / store.totalMs) * 100));
});
const isUrgent = computed(() => !store.paused && remainingMs.value > 0 && remainingMs.value <= 60_000);
const mmss = computed(() => {
  const totalSec = Math.ceil(remainingMs.value / 1000);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
});

function togglePause(): void {
  if (store.paused) store.resume();
  else store.pause();
  now.value = Date.now();
}

/** 浏览器要求音频必须由用户手势激活，这里在首次点击时预热 */
function primeAudio(): void {
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    if (!audioCtx) audioCtx = new Ctor();
    if (audioCtx.state === 'suspended') void audioCtx.resume();
  } catch {
    // 无音频权限时静默降级
  }
}

/** 三声短提示音，不依赖音频文件 */
function beep(): void {
  primeAudio();
  if (!audioCtx) return;
  const start = audioCtx.currentTime;
  [0, 0.28, 0.56].forEach((offset, i) => {
    const osc = audioCtx!.createOscillator();
    const gain = audioCtx!.createGain();
    osc.type = 'sine';
    osc.frequency.value = i === 2 ? 1046 : 880;
    gain.gain.setValueAtTime(0.0001, start + offset);
    gain.gain.exponentialRampToValueAtTime(0.22, start + offset + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.2);
    osc.connect(gain).connect(audioCtx!.destination);
    osc.start(start + offset);
    osc.stop(start + offset + 0.22);
  });
}

function flashTitle(): void {
  const original = document.title;
  let on = false;
  window.clearInterval(titleTimer);
  titleTimer = window.setInterval(() => {
    on = !on;
    document.title = on ? '⏰ 时间到 · 学习监控驾驶舱' : original;
  }, 800);
  window.setTimeout(() => {
    window.clearInterval(titleTimer);
    document.title = original;
  }, 20_000);
}

function sendNotification(title: string): void {
  try {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const n = new Notification('限时训练结束', {
      body: `「${title}」的计时已结束，休息一下再继续。`,
      tag: 'focus-timer',
      requireInteraction: true,
    });
    n.onclick = () => window.focus();
  } catch {
    // 通知失败不影响页面内提醒
  }
}

function fire(): void {
  if (fired) return;
  fired = true;
  const title = store.taskTitle;
  beep();
  sendNotification(title);
  flashTitle();
  ElMessageBox.alert(`「${title}」的限时训练时间到。`, '时间到', {
    type: 'success',
    confirmButtonText: '知道了',
    callback: () => {
      window.clearInterval(titleTimer);
      document.title = '学习监控驾驶舱 · Study Cockpit';
      store.stop();
      fired = false;
    },
  }).catch(() => {
    store.stop();
    fired = false;
  });
}

// 每秒刷新一次，并检查是否到点
function tick(): void {
  now.value = Date.now();
  if (store.isActive && !store.paused && remainingMs.value <= 0) fire();
}

function handleVisibility(): void {
  now.value = Date.now();
  if (store.isActive && !store.paused && remainingMs.value <= 0) fire();
}

onMounted(() => {
  store.init(String(userStore.user?.id ?? ''));
  tick();
  ticker = window.setInterval(tick, 1000);
  document.addEventListener('visibilitychange', handleVisibility);
  document.addEventListener('click', primeAudio, { once: true });
  // 离开期间已经结束的计时，进来立刻提醒
  if (store.isActive && store.expired) fire();
});

onBeforeUnmount(() => {
  window.clearInterval(ticker);
  window.clearInterval(titleTimer);
  document.removeEventListener('visibilitychange', handleVisibility);
  document.removeEventListener('click', primeAudio);
});

// 换账号时重置计时，避免串到别人身上
watch(
  () => userStore.user?.id,
  (id) => store.init(String(id ?? '')),
);
</script>

<style scoped>
.focus-timer {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 2000;
  width: 258px;
  padding: 14px 16px 12px;
  background: var(--raised);
  border: 1px solid var(--hairline-strong);
  border-radius: var(--r-panel);
  box-shadow: var(--sh-float), var(--sh-inset);
  backdrop-filter: blur(8px);
}
.ft-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.ft-badge {
  display: inline-flex;
  align-items: center;
  font-size: var(--fs-label);
  letter-spacing: 0.08em;
  color: var(--t3);
  font-family: var(--font-mono);
  white-space: nowrap;
}
.ft-title {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  color: var(--t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ft-time {
  font-size: 30px;
  font-weight: 500;
  line-height: 1.1;
  color: var(--accent);
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}
.urgent .ft-time {
  color: var(--danger);
}
.urgent {
  border-color: var(--danger-line);
}
.ft-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}
.ft-slide-enter-active,
.ft-slide-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}
.ft-slide-enter-from,
.ft-slide-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
