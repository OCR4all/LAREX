export default defineNuxtConfig({
  modules: [
    '@nuxt/content',
    '@nuxt/ui',
    '@nuxt/icon',
    '@nuxt/fonts',
    '@nuxt/image',
    '@nuxt/a11y'
  ],

  css: ['~/assets/css/main.css'],

  app: {
    head: {
      htmlAttrs: {
        lang: 'en'
      },
      titleTemplate: title => title ? `${title} - LAREX Documentation` : 'LAREX Documentation',
      meta: [
        { name: 'description', content: 'Documentation for LAREX, the Layout Analysis and Recognition application.' }
      ]
    }
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

  compatibilityDate: '2024-07-11',

  nitro: {
    preset: 'node-server'
  },

  a11y: {
    logIssues: false
  }
})
