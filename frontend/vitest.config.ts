import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

const appRoot = fileURLToPath(new URL('./app', import.meta.url))

export default defineConfig({
  test: {
    setupFiles: ['./test/setup.ts']
  },
  resolve: {
    alias: {
      '#server': fileURLToPath(new URL('./server', import.meta.url)),
      '@': appRoot,
      '~': appRoot
    }
  }
})
