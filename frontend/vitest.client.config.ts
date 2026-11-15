import { defineConfig } from 'vitest/config'
import { resolve } from './vitest.config.ts'

export default defineConfig({
  define: { 'import.meta.client': true, 'import.meta.server': false },
  resolve,
  test: {
    name: 'client',
    setupFiles: ['./test/setup.ts'],
    environment: 'node',
    include: ['app/**/*.client.test.ts']
  }
})
