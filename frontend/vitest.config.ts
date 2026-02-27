import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

const appRoot = fileURLToPath(new URL('./app', import.meta.url))

export default defineConfig({
  resolve: {
    alias: {
      '@': appRoot,
      '~': appRoot
    }
  }
})
