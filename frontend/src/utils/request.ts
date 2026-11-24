import axios from 'axios';
import { ElMessage } from 'element-plus';
import type { Result } from '@/types';

/**
 * 统一请求封装。
 * - JWT 在 HttpOnly Cookie 中，前端不接触 token；withCredentials 自动携带
 * - CSRF 双提交：Axios 默认读取 XSRF-TOKEN Cookie 并回传 X-XSRF-TOKEN 头
 * - 401 统一跳登录；业务错误映射为用户友好提示，不展示原始堆栈
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

http.interceptors.response.use(
  (response) => {
    // 文件下载直接返回原始响应
    if (response.config.responseType === 'blob') {
      return response;
    }
    const body = response.data as Result<unknown>;
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败');
      if (body.code === 40101 && !location.pathname.startsWith('/login')) {
        location.assign('/login?redirect=' + encodeURIComponent(location.pathname));
      }
      return Promise.reject(new Error(body.message));
    }
    return response;
  },
  (error) => {
    const status = error?.response?.status;
    const message = error?.response?.data?.message
      || (status === 429 ? '操作过于频繁，请稍后再试' : '网络异常，请稍后重试');
    ElMessage.error(message);
    if (status === 401 && !location.pathname.startsWith('/login')) {
      location.assign('/login?redirect=' + encodeURIComponent(location.pathname));
    }
    return Promise.reject(error);
  },
);

export async function get<T>(url: string, params?: unknown): Promise<T> {
  const res = await http.get<Result<T>>(url, { params });
  return res.data.data;
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.post<Result<T>>(url, data);
  return res.data.data;
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.put<Result<T>>(url, data);
  return res.data.data;
}

export async function patch<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.patch<Result<T>>(url, data);
  return res.data.data;
}

export async function del<T>(url: string): Promise<T> {
  const res = await http.delete<Result<T>>(url);
  return res.data.data;
}

/** multipart 上传（自定义 el-upload 的 http-request 复用此实例以携带 CSRF 头） */
export async function uploadFile<T>(url: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  const res = await http.post<Result<T>>(url, form);
  return res.data.data;
}

/** CSV 流式下载，浏览器直接保存 */
export async function download(url: string, params: unknown, filename: string): Promise<void> {
  const res = await http.get<Blob>(url, { params, responseType: 'blob' });
  const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' });
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(href);
}

export default http;
