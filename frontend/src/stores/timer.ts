import { defineStore } from 'pinia';

/**
 * 专注计时器。
 *
 * 设计要点：
 * - 以「结束时间戳」为准，而不是每秒递减计数：后台标签页节流、系统休眠都不会让时间走偏。
 * - 状态写入 localStorage，按用户隔离，刷新/切页不丢；换账号不会串。
 * - 只保留一个进行中的计时（一次专注一件事），与任务一一对应。
 */

const STORAGE_KEY = 'sc-focus-timer';

export interface FocusTimerSnapshot {
  userId: string;
  taskId: number;
  taskTitle: string;
  totalMs: number;
  endsAt: number;
  paused: boolean;
  remainingWhenPaused: number;
  startedAt: number;
}

function readSnapshot(): FocusTimerSnapshot | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const v = JSON.parse(raw) as FocusTimerSnapshot;
    if (typeof v?.taskId !== 'number' || typeof v?.endsAt !== 'number') return null;
    return v;
  } catch {
    return null;
  }
}

export const useTimerStore = defineStore('focusTimer', {
  state: () => ({
    userId: '',
    taskId: null as number | null,
    taskTitle: '',
    totalMs: 0,
    endsAt: 0,
    paused: false,
    remainingWhenPaused: 0,
    startedAt: 0,
    /** 到点后置位，由 UI 弹提醒并处理 */
    expired: false,
  }),

  getters: {
    isActive: (s) => s.taskId !== null,
  },

  actions: {
    /** 载入某一个用户的计时状态；用户变化时调用 */
    init(userId: string): void {
      this.userId = userId;
      const snap = readSnapshot();
      if (!snap || !userId || snap.userId !== userId) {
        this.reset();
        return;
      }
      this.taskId = snap.taskId;
      this.taskTitle = snap.taskTitle;
      this.totalMs = snap.totalMs;
      this.endsAt = snap.endsAt;
      this.paused = snap.paused;
      this.remainingWhenPaused = snap.remainingWhenPaused;
      this.startedAt = snap.startedAt;
      this.expired = !snap.paused && Date.now() >= snap.endsAt;
    },

    start(input: { taskId: number; taskTitle: string; minutes: number }): void {
      const now = Date.now();
      this.taskId = input.taskId;
      this.taskTitle = input.taskTitle;
      this.totalMs = Math.round(input.minutes * 60_000);
      this.endsAt = now + this.totalMs;
      this.startedAt = now;
      this.paused = false;
      this.remainingWhenPaused = 0;
      this.expired = false;
      this.persist();
    },

    pause(): void {
      if (!this.isActive || this.paused) return;
      this.remainingWhenPaused = Math.max(0, this.endsAt - Date.now());
      this.paused = true;
      this.persist();
    },

    resume(): void {
      if (!this.isActive || !this.paused) return;
      this.endsAt = Date.now() + this.remainingWhenPaused;
      this.paused = false;
      this.remainingWhenPaused = 0;
      this.persist();
    },

    /** 延长专注时间（分钟） */
    extend(minutes: number): void {
      if (!this.isActive) return;
      const add = Math.round(minutes * 60_000);
      if (this.paused) this.remainingWhenPaused += add;
      else this.endsAt += add;
      this.totalMs += add;
      this.expired = false;
      this.persist();
    },

    stop(): void {
      this.reset();
      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch {
        // 存储不可用时忽略
      }
    },

    reset(): void {
      this.taskId = null;
      this.taskTitle = '';
      this.totalMs = 0;
      this.endsAt = 0;
      this.paused = false;
      this.remainingWhenPaused = 0;
      this.startedAt = 0;
      this.expired = false;
    },

    persist(): void {
      if (!this.isActive || !this.userId) return;
      const snap: FocusTimerSnapshot = {
        userId: this.userId,
        taskId: this.taskId as number,
        taskTitle: this.taskTitle,
        totalMs: this.totalMs,
        endsAt: this.endsAt,
        paused: this.paused,
        remainingWhenPaused: this.remainingWhenPaused,
        startedAt: this.startedAt,
      };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(snap));
      } catch {
        // 隐私模式等场景下写入失败不影响计时本身
      }
    },

    /** 剩余毫秒（暂停时取暂停快照） */
    remainingMs(now = Date.now()): number {
      if (!this.isActive) return 0;
      if (this.paused) return Math.max(0, this.remainingWhenPaused);
      return Math.max(0, this.endsAt - now);
    },
  },
});
