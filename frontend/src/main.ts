import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import * as ElementPlusIcons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';
import './styles/index.css';
import App from './App.vue';
import router from './router';
import { isCloud, cloudClient, earlyPurgeIfNeeded } from '@/cloud/client';
import { useUserStore } from '@/stores/user';
import { useThemeStore } from '@/stores/theme';

// 必须在创建任何客户端之前执行：清掉上一页遗留的失效会话
if (isCloud) earlyPurgeIfNeeded();

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus, { locale: zhCn });
// 主题：先应用，再挂载，避免首屏闪一下别的颜色
useThemeStore(pinia).init();
// 全量注册图标，页面可直接使用 <el-icon><Edit /></el-icon>
Object.entries(ElementPlusIcons).forEach(([name, component]) => {
  app.component(name, component as never);
});
app.mount('#app');
if (isCloud) {
  cloudClient().auth.onAuthStateChange((event) => {
    if (event === 'SIGNED_OUT') {
      useUserStore(pinia).clearUser();
      if (!router.currentRoute.value.meta.public) void router.replace('/login');
    }
  });
}
