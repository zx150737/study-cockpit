import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

/**
 * 页面路由（6 个页面）。
 * 除 /login 外全部需要认证；守卫通过 /api/auth/me 判定 HttpOnly Cookie 是否有效。
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
  return true;
});

export default router;
