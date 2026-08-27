export default defineAppConfig({
  icon: {
    mode: 'css',
    cssLayer: 'base'
  },
  ui: {
    colors: {
      primary: 'navy',
      secondary: 'copper',
      success: 'leaf',
      info: 'river',
      warning: 'ochre',
      error: 'brick',
      neutral: 'smoke'
    },
    slideover: {
      slots: {
        overlay: 'z-[60]',
        content: 'z-[60]',
        footer: 'justify-end'
      }
    },
    modal: {
      slots: {
        overlay: 'z-[70]',
        content: 'z-[70]'
      }
    },
    dashboardSearch: {
      slots: {
        modal: 'z-[80]'
      }
    },
    dropdownMenu: {
      slots: {
        content: 'z-[90]'
      }
    },
    table: {
      slots: {
        td: 'text-default [&_.text-dimmed]:text-default [&_.text-muted]:text-default [&_.text-toned]:text-default',
        empty: 'text-default'
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
    // Select content is portaled to the document body, outside slideover and modal stacking contexts.
    select: {
      slots: {
        base: 'w-full',
        content: 'z-[90]'
      }
    },
    selectMenu: {
      slots: {
        base: 'w-full',
        content: 'z-[90]'
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
