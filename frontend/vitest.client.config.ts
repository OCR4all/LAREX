import { defineConfig } from 'vitest/config'
import base from './vitest.config.ts'

export default defineConfig({
  define: { 'import.meta.client': true, 'import.meta.server': false },
  resolve: base.resolve,
  test: {
    name: 'client',
    setupFiles: ['./test/setup.ts'],
    environment: 'node',
    include: ['app/**/*.client.test.ts']
  }
})
