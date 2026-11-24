import pluginVue from 'eslint-plugin-vue';
import eslintConfigPrettier from 'eslint-config-prettier';
import tseslint from 'typescript-eslint';
import vueParser from 'vue-eslint-parser';

/**
 * ESLint 扁平配置（Vue3 + TS）。
 * - *.vue 用 vue-eslint-parser 解析模板，其 <script lang="ts"> 内嵌 @typescript-eslint/parser；
 * - *.ts 直接用 typescript-eslint 解析；
 * - Prettier 关闭格式冲突规则；严格类型检查仍由 vue-tsc（pnpm typecheck）负责。
 */
export default tseslint.config(
  ...pluginVue.configs['flat/recommended'],
  ...tseslint.configs.recommended,
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        sourceType: 'module',
      },
    },
  },
  {
    files: ['**/*.{ts,vue}'],
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-unused-vars': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      // 既有代码中存在 any 与空对象类型，先降级为 warn，不阻塞构建；后续逐步收紧
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-empty-object-type': 'warn',
    },
  },
  eslintConfigPrettier,
);
