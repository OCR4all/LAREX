import { fileURLToPath } from 'node:url'
import { configDefaults, defineConfig } from 'vitest/config'

const appRoot = fileURLToPath(new URL('./app', import.meta.url))

export default defineConfig({
  test: {
    setupFiles: ['./test/setup.ts'],
    projects: [
      {
        extends: true,
        test: { name: 'unit', exclude: [...configDefaults.exclude, '**/*.client.test.ts'] }
      },
      './vitest.client.config.ts'
    ]
  },
  resolve: {
    alias: {
      '#server': fileURLToPath(new URL('./server', import.meta.url)),
      '@': appRoot,
      '~': appRoot
    }
  }
})
