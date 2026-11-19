export default defineNuxtConfig({
  extends: ['docus'],

  modules: [
    '@nuxt/a11y'
  ],

  site: {
    url: 'https://docs.larex.kallimachos.de',
    name: 'LAREX Documentation'
  },

  content: {
    build: {
      markdown: {
        toc: {
          depth: 3,
          searchDepth: 3
        }
      }
    }
  },

  routeRules: {
    '/actions/processor-sdk': { redirect: { to: '/actions/processor-sdk/overview', statusCode: 301 } }
  },

  compatibilityDate: '2025-07-22',

  vite: {
    optimizeDeps: {
      // Discovered at runtime otherwise, which reloads the page in dev
      include: ['@unhead/schema-org/vue', 'fflate']
    }
  },

  nitro: {
    preset: 'node-server'
  },

  a11y: {
    logIssues: false
  }
})
