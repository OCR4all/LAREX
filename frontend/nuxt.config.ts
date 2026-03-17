export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui',
    '@vueuse/nuxt',
    'nuxt-auth-utils',
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    'pinia-plugin-persistedstate/nuxt',
    '@nuxt/fonts',
    '@nuxt/a11y',
    '@nuxt/image'
  ],
  ssr: true,

  devtools: {
    enabled: process.env.NODE_ENV === 'development'
  },

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    oauth: {
      keycloak: {
        serverUrl: 'http://keycloak.localhost',
        serverUrlInternal: 'http://keycloak.localhost',
        realm: 'larex-dev',
        clientId: 'larex-frontend',
        clientSecret: 'PLEASE_CHANGE_IN_PRODUCTION',
        redirectURL: 'http://larex.localhost/auth/keycloak'
      }
    },
    apiBaseInternal: process.env.NUXT_API_BASE_INTERNAL || 'http://app:8080/api/v1'
  },

  routeRules: {
    '/api/**': {
      cors: true
    }
  },

  compatibilityDate: '2024-07-11',

  nitro: {
    experimental: {
      websocket: true
    }
  },

  vite: {
    server: {
      hmr: {
        port: 3000
      }
    },
    optimizeDeps: {
      include: [
        '@vue/devtools-core',
        '@vue/devtools-kit',
        'driver.js',
        'dockview-vue',
        'earcut',
        'vue-draggable-plus',
        'rbush',
        'martinez-polygon-clipping',
        'diff-match-patch',
        '@tanstack/vue-virtual',
        'date-fns',
        'zod',
        '@codemirror/state',
        '@codemirror/view',
        '@codemirror/lang-xml',
        'codemirror',
        '@codemirror/lint',
        '@codemirror/search',
        '@codemirror/language',
        '@lezer/highlight',
      ]
    }
  },

  a11y: {
    logIssues: false
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
