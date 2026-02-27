export default defineAppConfig({
  icon: {
    mode: 'css',
    cssLayer: 'base'
  },
  ui: {
    colors: {
      primary: 'burnt-sienna',
      neutral: 'cararra'
    },
    slideover: {
      slots: {
        footer: 'justify-end'
      }
    },
    formField: {
      slots: {
        container: 'mt-1 relative w-full'
      }
    },
    input: {
      slots: {
        root: 'w-full'
      }
    },
    inputTags: {
      slots: {
        root: 'w-full'
      }
    },
    select: {
      slots: {
        base: 'w-full'
      }
    },
    selectMenu: {
      slots: {
        base: 'w-full'
      }
    },
    textarea: {
      slots: {
        root: 'w-full'
      }
    },
    error: {
      slots: {
        root: 'min-h-[calc(100vh-var(--ui-header-height))] flex flex-col items-center justify-center text-center',
        statusCode: 'text-base font-semibold text-white',
        statusMessage: 'mt-2 text-4xl sm:text-5xl font-bold text-white',
        message: 'mt-4 text-lg text-white text-balance',
        links: 'mt-8 flex items-center justify-center gap-6'
      }
    }
  }
})
