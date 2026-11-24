import { defineStore } from 'pinia';
import axios from 'axios';
import { post } from '@/utils/request';
import type { Result, UserVO } from '@/types';

/**
 * 用户状态：只保存展示信息，绝不保存 JWT（token 在 HttpOnly Cookie 中）。
 * 登录态通过 GET /api/auth/me 判定；路由守卫据此拦截。
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
        await post<void>('/auth/logout');
      } catch {
        // 注销失败也清理前端状态
      }
      this.user = null;
      this.checked = false;
    },
    setUser(user: UserVO): void {
      this.user = user;
      this.checked = true;
    },
  },
});
