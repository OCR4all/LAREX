// Add a little elevation to outlined fields without changing flat/embedded variants.
const outlinedFieldVariants = {
  outline: 'shadow-2xs ring-neutral-300 hover:not-disabled:ring-neutral-400 dark:ring-neutral-700 dark:hover:not-disabled:ring-neutral-600 dark:shadow-black/40 dark:inset-shadow-xs dark:inset-shadow-white/10 disabled:shadow-none dark:disabled:inset-shadow-none'
}

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
      warning: 'yellow',
      error: 'brick',
      neutral: 'smoke'
    },
    // Keep warning accents yellow while giving warning text a readable foreground in both themes.
    alert: {
      compoundVariants: [{
        color: 'warning',
        variant: ['soft', 'subtle'],
        class: { title: 'text-highlighted', description: 'text-toned opacity-100' }
      }]
    },
    badge: {
      compoundVariants: [{
        color: 'warning',
        variant: ['soft', 'subtle'],
        class: 'text-toned'
      }]
    },
    button: {
      compoundVariants: [{
        variant: ['solid', 'outline', 'soft', 'subtle'],
        class: 'shadow-xs inset-shadow-xs inset-shadow-white/10 dark:shadow-black/40 dark:inset-shadow-white/20 disabled:shadow-none disabled:inset-shadow-none aria-disabled:shadow-none aria-disabled:inset-shadow-none'
      }, {
        color: 'warning',
        variant: 'solid',
        class: 'text-neutral-950'
      }]
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
    dashboardSearchButton: {
      variants: {
        collapsed: {
          false: {
            base: 'bg-default shadow-2xs ring-neutral-300 hover:ring-neutral-400 dark:ring-neutral-700 dark:hover:ring-neutral-600 dark:shadow-black/40 dark:inset-shadow-xs dark:inset-shadow-white/10'
          }
        }
      }
    },
    navigationMenu: {
      compoundVariants: [{
        orientation: 'vertical',
        color: 'primary',
        variant: 'pill',
        active: true,
        class: {
          link: 'text-white before:bg-brand-blue before:shadow-xs before:ring before:ring-inset before:ring-white/10 dark:before:ring-white/20 dark:before:shadow-black/40 hover:text-white hover:before:bg-brand-blue data-[state=open]:text-white',
          linkLeadingIcon: 'text-white group-hover:text-white group-data-[state=open]:text-white',
          linkTrailingIcon: 'text-white'
        }
      }, {
        orientation: 'vertical',
        variant: 'pill',
        active: false,
        class: {
          link: 'text-default hover:before:bg-brand-blue/12 dark:hover:before:bg-brand-blue/20',
          linkLeadingIcon: 'text-toned',
          linkTrailingIcon: 'text-toned'
        }
      }]
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
      },
      variants: {
        variant: outlinedFieldVariants
      }
    },
    inputTags: {
      slots: {
        root: 'w-full'
      },
      variants: {
        variant: outlinedFieldVariants
      }
    },
    // Select content is portaled to the document body, outside slideover and modal stacking contexts.
    select: {
      slots: {
        base: 'w-full',
        content: 'z-[90]'
      },
      variants: {
        variant: outlinedFieldVariants
      }
    },
    selectMenu: {
      slots: {
        base: 'w-full',
        content: 'z-[90]'
      },
      variants: {
        variant: outlinedFieldVariants
      }
    },
    textarea: {
      slots: {
        root: 'w-full'
      },
      variants: {
        variant: outlinedFieldVariants
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
