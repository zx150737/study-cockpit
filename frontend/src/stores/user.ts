import { defineStore } from 'pinia';
import axios from 'axios';
import { post } from '@/utils/request';
import type { Result, UserVO } from '@/types';
import { isCloud, cloudCurrentUser } from '@/cloud/client';
import { authApi as cloudAuth } from '@/cloud/api';

/**
 * Java 版使用 HttpOnly Cookie；免费云版由 Supabase SDK 管理浏览器会话。
 * 此 store 只保存展示信息，登录身份由服务端验证。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    user: null as UserVO | null,
    checked: false,
  }),
  actions: {
    /** 用裸 axios 探测登录态，避免触发请求拦截器的自动跳转造成循环导航 */
    async fetchMe(): Promise<void> {
      try {
        if (isCloud) {
          this.user = await cloudCurrentUser();
          return;
        }
        const base = import.meta.env.VITE_API_BASE_URL || '/api';
        const res = await axios.get<Result<UserVO>>(`${base}/auth/me`, { withCredentials: true });
        this.user = res.data?.code === 0 ? res.data.data : null;
      } catch {
        this.user = null;
      } finally {
        this.checked = true;
      }
    },
    async logout(): Promise<void> {
      try {
        if (isCloud) await cloudAuth.logout();
        else await post<void>('/auth/logout');
      } catch {
        // 注销失败也清理前端状态
      }
      this.clearUser();
    },
    clearUser(): void {
      this.user = null;
      this.checked = true;
    },
    setUser(user: UserVO): void {
      this.user = user;
      this.checked = true;
    },
  },
});
