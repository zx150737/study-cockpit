import { isCloud } from '@/cloud/client';
import * as java from './java';
import * as cloud from '@/cloud/api';

export const authApi = isCloud ? cloud.authApi : java.authApi;
export const goalApi = isCloud ? cloud.goalApi : java.goalApi;
export const subjectApi = isCloud ? cloud.subjectApi : java.subjectApi;
export const taskApi = isCloud ? cloud.taskApi : java.taskApi;
export const imageApi = isCloud ? cloud.imageApi : java.imageApi;
export const dashboardApi = isCloud ? cloud.dashboardApi : java.dashboardApi;
export const notificationApi = isCloud ? cloud.notificationApi : java.notificationApi;
export const archiveApi = isCloud ? cloud.archiveApi : java.archiveApi;
export const reportApi = isCloud ? cloud.reportApi : java.reportApi;
// 后台管理只在 Supabase 云端版提供；Java 版返回 null，页面据此隐藏入口。
export const adminApi = isCloud ? cloud.adminApi : null;
// AI 学习助手只在 Supabase 云端版提供（模型调用走边缘函数，密钥不下发前端）。
export const tutorApi = isCloud ? cloud.tutorApi : null;
