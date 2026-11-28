import { ElMessage } from 'element-plus';
import { cloudClient, userView } from './client';
import { mondayOf, sundayOf } from '@/utils/date';
import type { UserVO, GoalVO, SubjectVO, TaskVO, TaskReq, TaskQuery, PageData, ImageVO, OverviewVO, WeeklyVO, WeekVO, WeekDetailVO, OverdueVO, AdminUserVO, AdminImageVO, AdminEntryVO, TutorReply, TutorThreadVO, TutorThreadPage, TutorCardPage, TutorStatsVO } from '@/types';

function message(error: unknown): string {
  const e = error as { code?: string; message?: string };
  const known: Record<string,string> = {
    invalid_credentials: '邮箱或密码不正确', email_not_confirmed: '请先完成邮箱验证',
    user_already_exists: '该邮箱已注册，请直接登录', signup_disabled: '当前暂未开放注册',
    weak_password: '密码强度不足，请使用至少 8 位密码', same_password: '新密码不能与旧密码相同',
    over_request_rate_limit: '操作过于频繁，请稍后重试', over_email_send_rate_limit: '邮件发送过于频繁，请稍后重试',
    '42501': '登录已失效或没有访问权限，请重新登录', '23503': '目标或科目不存在',
    '23514': '填写内容不符合要求，请检查名称、日期、权重或文件大小', '23502': '请填写所有必填项',
  };
  return known[e.code || ''] || (e.message && /[\u4e00-\u9fff]/.test(e.message) ? e.message : '操作失败，请检查网络后重试');
}
async function checked<T>(operation: () => Promise<T>): Promise<T> {
  try { return await operation(); }
  catch (error) { ElMessage.error(message(error)); throw error; }
}
async function rpc<T>(name: string, args: Record<string,unknown>): Promise<T> {
  return checked(async () => {
    const { data, error } = await cloudClient().rpc(name, args);
    if (error) throw error;
    return data as T;
  });
}
const read = <T>(action: string, params: object = {}) => rpc<T>('sc_read', { action, params });
const write = <T = void>(action: string, payload: object) => rpc<T>('sc_write', { action, payload });
export const authApi = {
  csrf: async () => undefined,
  registration: async () => { cloudClient(); return { enabled: true }; },
  login: (input: { username: string; password: string }): Promise<UserVO> => checked(async () => {
    const { data, error } = await cloudClient().auth.signInWithPassword({ email: input.username.trim(), password: input.password });
    if (error) throw error;
    return userView(data.user);
  }),
  register: (input: { username: string; password: string; nickname?: string }) => checked(async () => {
    const { data, error } = await cloudClient().auth.signUp({ email: input.username.trim(), password: input.password,
      options: { data: { nickname: input.nickname || '' }, emailRedirectTo: `${location.origin}/login` } });
    if (error) throw error;
    const confirmationRequired = !data.session;
    // Confirm email 关闭时注册即带会话，直接登录，不再让用户多走一步。
    return { confirmationRequired, autoLogin: !!data.session };
  }),
  logout: () => checked(async () => { const { error } = await cloudClient().auth.signOut({ scope: 'local' }); if (error) throw error; }),
  me: (): Promise<UserVO> => checked(async () => {
    const { data, error } = await cloudClient().auth.getUser();
    if (error || !data.user) throw error || new Error('请先登录');
    return userView(data.user);
  }),
  changePassword: (input: { oldPassword: string; newPassword: string }) => checked(async () => {
    const { error } = await cloudClient().auth.updateUser({ password: input.newPassword, current_password: input.oldPassword });
    if (error) throw error;
    const result = await cloudClient().auth.signOut({ scope: 'global' });
    if (result.error) throw result.error;
  }),
};
export const goalApi = {
  list: () => read<GoalVO[]>('goals'),
  create: (data: { name: string }) => write<GoalVO>('createGoal', data),
  update: (id: number, data: { name: string }) => write('updateGoal', { ...data, id }),
  remove: (id: number) => write('deleteGoal', { id }),
};
export const subjectApi = {
  list: (goalId: number) => read<SubjectVO[]>('subjects', { goalId }),
  create: (data: { goalId: number; name: string; defaultWeight: number }) => write<SubjectVO>('createSubject', data),
  update: (id: number, data: { goalId: number; name: string; defaultWeight: number }) => write('updateSubject', { ...data, id }),
  remove: (id: number) => write('deleteSubject', { id }),
};
export const taskApi = {
  page: (params: TaskQuery) => read<PageData<TaskVO>>('tasks', params),
  create: (data: TaskReq) => write<TaskVO>('createTask', data),
  update: (id: number, data: TaskReq) => write('updateTask', { ...data, id }),
  remove: (id: number) => write('deleteTask', { id }),
  finish: (id: number, finishStatus: number) => write('finishTask', { id, finishStatus }),
};
export const imageApi = {
  list: (taskId: number): Promise<ImageVO[]> => checked(async () => {
    const { data, error } = await cloudClient().from('sc_images').select('*').eq('task_id', taskId).gt('expire_time', new Date().toISOString()).order('id');
    if (error) throw error;
    return Promise.all((data || []).map(async row => {
      const ttl = Math.max(1, Math.min(900, Math.floor((Date.parse(row.expire_time) - Date.now()) / 1000)));
      const signed = await cloudClient().storage.from('study-images').createSignedUrl(row.path, ttl);
      if (signed.error) throw signed.error;
      return { id: row.id, taskId: row.task_id, originalName: row.original_name, temporaryUrl: signed.data.signedUrl,
        uploadTime: row.upload_time, expireTime: row.expire_time, fileSize: row.file_size, mimeType: row.mime_type };
    }));
  }),
  upload: (taskId: number, file: File) => checked(async () => {
    const body = new FormData(); body.append('taskId', String(taskId)); body.append('file', file);
    await imageFunction(body);
  }),
  remove: (id: number) => checked(async () => { await imageFunction({ operation: 'delete', id }); }),
};
async function imageFunction(body: FormData | { operation: string; id: number }): Promise<void> {
  const { data, error } = await cloudClient().functions.invoke('task-images', { body });
  if (error) {
    let text = '图片操作失败，请稍后重试';
    if (error.context instanceof Response) {
      const response = await error.context.json().catch(() => null);
      text = response?.error || text;
    }
    throw new Error(text);
  }
  if (data?.error) throw new Error(data.error);
}
export const dashboardApi = { overview: () => read<OverviewVO>('overview') };
export const notificationApi = { overdue: () => read<OverdueVO>('overdue') };
export const archiveApi = {
  weeks: (params: { page: number; size: number }) => read<PageData<WeekVO>>('weeks', params),
  weekDetail: (weekStart: string) => read<WeekDetailVO>('weekDetail', { weekStart }),
  tasks: (params: { weekStart?: string; goalId?: number; page: number; size: number }) => read<PageData<TaskVO>>('tasks', params),
};
export function csvCell(input: unknown): string {
  let text = String(input ?? '');
  if (/^[=+@\-\t\r\n]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}
export const reportApi = {
  weekly: (params: { weekStart: string; goalId?: number }) => read<WeeklyVO>('weekly', params),
  exportCsv: async (params: { weekStart: string; goalId?: number }) => {
    const start = mondayOf(params.weekStart), end = sundayOf(start);
    const rows: unknown[][] = [['周起始','周结束','备考目标','科目','任务日期','任务内容','权重','掌握度','截止时间','完成状态','完成时间','复盘备注','图片标记','图片数量']];
    for (let page = 1; ; page++) {
      const result = await taskApi.page({ ...params, weekStart: start, page, size: 100 });
      for (const t of result.list) rows.push([start,end,t.goalName,t.subjectName,t.taskDate,t.title,t.weight,t.mastery,t.deadline,
        t.finishStatus === 1 ? '已完成' : '未完成',t.finishedAt,t.reviewNote,t.imageCount ? '[IMAGE]' : '',t.imageCount]);
      if (page * 100 >= result.total) break;
    }
    const blob = new Blob(['\ufeff' + rows.map(row => row.map(csvCell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob), anchor = document.createElement('a');
    anchor.href = url; anchor.download = `weekly_${start}_${end}.csv`; document.body.appendChild(anchor); anchor.click(); anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};

// ---------------- 后台管理 ----------------
async function adminRpc<T>(name: string, args: Record<string, unknown>): Promise<T> {
  return checked(async () => {
    const { data, error } = await cloudClient().rpc(name, args);
    if (error) throw error;
    return data as T;
  });
}
async function archiveFunction(body: { operation: string; id: number }): Promise<void> {
  const { data, error } = await cloudClient().functions.invoke('image-archive', { body });
  if (error) {
    let text = '图片管理操作失败，请稍后重试';
    if (error.context instanceof Response) {
      const response = await error.context.json().catch(() => null);
      text = response?.error || text;
    }
    throw new Error(text);
  }
  if (data?.error) throw new Error(data.error);
}
const ARCHIVE_BUCKET = 'study-archive';
const HOT_BUCKET = 'study-images';
export const adminApi = {
  /** 当前登录者是否为管理员（页面入口与路由守卫使用） */
  isAdmin: (): Promise<boolean> => adminRpc<boolean>('sc_is_admin', {}),
  users: (): Promise<AdminUserVO[]> => adminRpc<AdminUserVO[]>('sc_admin_users', {}),
  tasks: (userId: string, page = 1, size = 50): Promise<PageData<TaskVO>> =>
    adminRpc<PageData<TaskVO>>('sc_admin_tasks', { p_user: userId, p_page: page, p_size: size }),
  images: (userId: string, includeExpired = false): Promise<AdminImageVO[]> =>
    adminRpc<AdminImageVO[]>('sc_admin_images', { p_user: userId, p_include_expired: includeExpired }),
  /** 为后台图片墙签发临时链接；归档图片从长期桶取 */
  signUrls: (list: AdminImageVO[]): Promise<(string | null)[]> =>
    checked(async () => {
      return Promise.all(
        list.map(async (img) => {
          const { data, error } = await cloudClient()
            .storage.from(img.archived ? ARCHIVE_BUCKET : HOT_BUCKET)
            .createSignedUrl(img.path, 900);
          return error ? null : data.signedUrl;
        }),
      );
    }),
  archive: (id: number) => checked(() => archiveFunction({ operation: 'archive', id })),
  unarchive: (id: number) => checked(() => archiveFunction({ operation: 'unarchive', id })),
  removeImage: (id: number) => checked(() => archiveFunction({ operation: 'delete', id })),
  admins: (): Promise<AdminEntryVO[]> => adminRpc<AdminEntryVO[]>('sc_admin_list', {}),
  addAdmin: (email: string, note?: string): Promise<{ userId: string; email: string }> =>
    adminRpc('sc_admin_add', { p_email: email, p_note: note ?? null }),
  removeAdmin: (userId: string): Promise<void> => adminRpc('sc_admin_remove', { p_user: userId }),
};

// ---------------- AI 学习助手 ----------------
const aiRead = <T>(action: string, params: object = {}) => rpc<T>('sc_ai_read', { action, params });
const aiWrite = <T = void>(action: string, payload: object) => rpc<T>('sc_ai_write', { action, payload });
export const tutorApi = {
  threads: (page = 1, size = 30): Promise<TutorThreadPage> => aiRead('threads', { page, size }),
  thread: (id: number): Promise<TutorThreadVO> => aiRead('thread', { id }),
  cards: (params: { dueOnly?: boolean; page?: number; size?: number } = {}): Promise<TutorCardPage> =>
    aiRead('cards', params),
  stats: (): Promise<TutorStatsVO> => aiRead('stats', {}),
  createThread: (data: { question: string; mode: 'explain' | 'practice'; subjectId?: number | null }) =>
    aiWrite<TutorThreadVO>('createThread', data),
  deleteThread: (id: number) => aiWrite('deleteThread', { id }),
  addCard: (threadId: number) => aiWrite<{ id: number }>('addCard', { threadId }),
  createCard: (data: { front: string; back: string; subjectName?: string }) =>
    aiWrite<{ id: number }>('createCard', data),
  deleteCard: (id: number) => aiWrite('deleteCard', { id }),
  reviewCard: (id: number, grade: 0 | 1 | 2) =>
    aiWrite<{ id: number; box: number; dueDate: string }>('reviewCard', { id, grade }),
  /** 调边缘函数生成讲解；同一提问已有答复时服务端直接返回缓存，不重复计费。 */
  explain: (threadId: number, regenerate = false): Promise<{ reply: TutorReply; cached?: boolean }> =>
    checked(async () => {
      const { data, error } = await cloudClient().functions.invoke('ai-tutor', {
        body: { threadId, regenerate },
      });
      if (error) {
        let text = 'AI 讲解失败，请稍后重试';
        if (error.context instanceof Response) {
          const payload = await error.context.json().catch(() => null);
          text = payload?.error || text;
        }
        throw new Error(text);
      }
      if (data?.error) throw new Error(data.error);
      return data as { reply: TutorReply; cached?: boolean };
    }),
};
