import { fileURLToPath } from 'node:url'
import { defineVitestProject } from '@nuxt/test-utils/config'
import { configDefaults, defineConfig } from 'vitest/config'

const appRoot = fileURLToPath(new URL('./app', import.meta.url))

export const resolve = {
  alias: {
    '#server': fileURLToPath(new URL('./server', import.meta.url)),
    '@': appRoot,
    '~': appRoot
  }
}

export default defineConfig(async () => ({
  test: {
    setupFiles: ['./test/setup.ts'],
    coverage: {
      provider: 'v8',
      include: ['app/**/*.{ts,vue}', 'server/**/*.ts', 'shared/**/*.ts'],
      exclude: ['**/__tests__/**', '**/*.test.ts', '**/*.d.ts'],
      reporter: ['text-summary', 'json-summary', 'html']
    },
    projects: [
      {
        extends: true,
        test: {
          name: 'unit',
          exclude: [...configDefaults.exclude, 'e2e/**', '**/*.client.test.ts', '**/*.nuxt.test.ts']
        }
      },
      './vitest.client.config.ts',
      await defineVitestProject({
        test: {
          name: 'nuxt',
          environment: 'nuxt',
          include: ['app/**/*.nuxt.test.ts']
        }
      })
    ]
  },
  resolve
}))
