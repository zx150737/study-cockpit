import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

/**
 * 登录与注册为公开页面，其余页面需要认证。
 * 守卫通过当前部署方式的账号服务验证登录身份。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/login/RegisterView.vue'),
      meta: { public: true, title: '注册' },
    },
    {
      path: '/',
      component: () => import('@/layout/index.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '主驾驶舱', requiresAuth: true },
        },
        {
          path: 'tutor',
          name: 'Tutor',
          component: () => import('@/views/tutor/TutorView.vue'),
          meta: { title: 'AI 学习助手', requiresAuth: true },
        },
        {
          path: 'review',
          name: 'Review',
          component: () => import('@/views/tutor/ReviewView.vue'),
          meta: { title: '错题复习', requiresAuth: true },
        },
        {
          path: 'goals',
          name: 'Goals',
          component: () => import('@/views/goals/GoalSubjectView.vue'),
          meta: { title: '目标 & 科目', requiresAuth: true },
        },
        {
          path: 'tasks',
          name: 'Tasks',
          component: () => import('@/views/tasks/TaskView.vue'),
          meta: { title: '任务管理', requiresAuth: true },
        },
        {
          path: 'weekly',
          name: 'Weekly',
          component: () => import('@/views/weekly/WeeklyAnalysisView.vue'),
          meta: { title: '周度分析', requiresAuth: true },
        },
        {
          path: 'archive',
          name: 'Archive',
          component: () => import('@/views/archive/ArchiveView.vue'),
          meta: { title: '历史归档', requiresAuth: true },
        },
        {
          path: 'admin',
          name: 'Admin',
          component: () => import('@/views/admin/AdminView.vue'),
          meta: { title: '后台管理', requiresAuth: true, admin: true },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});

router.beforeEach(async (to) => {
  const store = useUserStore();
  if (to.meta.public) {
    return true;
  }
  if (!store.checked) {
    await store.fetchMe();
  }
  if (!store.user) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } };
  }
  if (to.meta.admin) {
    // 管理员页面：以数据库白名单为准，前端只做入口拦截，真实权限由 RPC 强制校验。
    const { adminApi } = await import('@/api');
    const allowed = adminApi ? await adminApi.isAdmin().catch(() => false) : false;
    if (!allowed) return { path: '/dashboard' };
  }
  return true;
});

export default router;
