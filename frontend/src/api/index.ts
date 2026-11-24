import { get, post, put, patch, del, uploadFile, download } from '@/utils/request';
import type {
  UserVO, GoalVO, SubjectVO, TaskVO, TaskReq, TaskQuery, PageData,
  ImageVO, OverviewVO, WeeklyVO, WeekVO, WeekDetailVO, OverdueVO,
} from '@/types';

export const authApi = {
  /** 登录前调用一次，让后端下发 XSRF-TOKEN Cookie */
  csrf: () => get<void>('/auth/csrf'),
  login: (data: { username: string; password: string }) => post<UserVO>('/auth/login', data),
  register: (data: { username: string; password: string; nickname?: string }) =>
    post<UserVO>('/auth/register', data),
  logout: () => post<void>('/auth/logout'),
  me: () => get<UserVO>('/auth/me'),
  /** 修改密码：成功后旧会话失效，需重新登录 */
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    post<void>('/auth/change-password', data),
};

export const goalApi = {
  list: () => get<GoalVO[]>('/goals'),
  create: (data: { name: string }) => post<GoalVO>('/goals', data),
  update: (id: number, data: { name: string }) => put<void>(`/goals/${id}`, data),
  remove: (id: number) => del<void>(`/goals/${id}`),
};

export const subjectApi = {
  list: (goalId: number) => get<SubjectVO[]>(`/goals/${goalId}/subjects`),
  create: (data: { goalId: number; name: string; defaultWeight: number }) =>
    post<SubjectVO>('/subjects', data),
  update: (id: number, data: { goalId: number; name: string; defaultWeight: number }) =>
    put<void>(`/subjects/${id}`, data),
  remove: (id: number) => del<void>(`/subjects/${id}`),
};

export const taskApi = {
  page: (params: TaskQuery) => get<PageData<TaskVO>>('/tasks', params),
  create: (data: TaskReq) => post<TaskVO>('/tasks', data),
  update: (id: number, data: TaskReq) => put<void>(`/tasks/${id}`, data),
  remove: (id: number) => del<void>(`/tasks/${id}`),
  finish: (id: number, finishStatus: number) => patch<void>(`/tasks/${id}/finish`, { finishStatus }),
};

export const imageApi = {
  list: (taskId: number) => get<ImageVO[]>(`/tasks/${taskId}/images`),
  upload: (taskId: number, file: File) =>
    uploadFile<ImageVO>(`/tasks/${taskId}/images`, file),
  remove: (id: number) => del<void>(`/images/${id}`),
};

export const dashboardApi = {
  overview: () => get<OverviewVO>('/dashboard/overview'),
};

export const reportApi = {
  weekly: (params: { weekStart: string; goalId?: number }) =>
    get<WeeklyVO>('/reports/weekly', params),
  exportCsv: (params: { weekStart: string; goalId?: number }) =>
    download('/reports/weekly/export', params, `weekly_${params.weekStart}.csv`),
};

export const archiveApi = {
  weeks: (params: { page: number; size: number }) =>
    get<PageData<WeekVO>>('/archive/weeks', params),
  weekDetail: (weekStart: string) => get<WeekDetailVO>(`/archive/weeks/${weekStart}`),
  tasks: (params: { weekStart?: string; goalId?: number; page: number; size: number }) =>
    get<PageData<TaskVO>>('/archive/tasks', params),
};

export const notificationApi = {
  overdue: () => get<OverdueVO>('/notifications/overdue'),
};
