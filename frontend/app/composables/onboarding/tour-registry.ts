import type { DriveStep } from 'driver.js'
import {
  clickAndWait,
  dispatchOnboardingEvent,
  ensureDashboardSidebarVisible,
  ensureSidebarSectionExpanded,
  ensureEditorMode,
  waitForVisibleElement,
  withHookAction,
  withNextAction
} from './tour-utils'

export const TOUR_IDS = [
  'global-intro',
  'tasks-index',
  'labels-index',
  'labels-builder',
  'tag-sets-index',
  'tag-sets-builder',
  'virtual-keyboards-index',
  'virtual-keyboards-builder',
  'codecs-index',
  'codecs-builder',
  'workspace-general',
  'workspace-members',
  'workspace-requests',
  'settings-profile',
  'settings-invitations',
  'settings-notifications',
  'settings-security',
  'editor-layout',
  'editor-text'
] as const

export type TourId = typeof TOUR_IDS[number]

export type OnboardingMajor = 'dashboard' | 'editor'

export type OnboardingContext = {
  editorMode?: 'layout' | 'text'
}

export type OnboardingDriveStep = DriveStep & {
  includeIf?: () => boolean
}

export type OnboardingTourDefinition = {
  id: TourId
  major: OnboardingMajor
  autoStart: boolean
  matches: (path: string, ctx?: OnboardingContext) => boolean
  steps: () => OnboardingDriveStep[]
  onFinish?: () => void | Promise<void>
}

const matchers = {
  labelsBuilder: /^\/labels\/(new|[^/]+)\/?$/,
  tagSetBuilder: /^\/tag-sets\/(new|[^/]+)\/?$/,
  keyboardBuilder: /^\/virtual-keyboard\/(new|[^/]+)\/?$/,
  codecBuilder: /^\/codecs\/(new|[^/]+)\/?$/
}

const tours: OnboardingTourDefinition[] = [
  {
    id: 'global-intro',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/',
    steps: () => [
      {
        popover: {
          title: 'Welcome to LAREX',
          description:
            'Start here for orientation. After this, each page shows a short one-time tour when you visit it.',
          side: 'top',
          align: 'center'
        }
      },
      {
        element: '#dashboard',
        onHighlightStarted: withHookAction(async () => {
          await ensureDashboardSidebarVisible()
        }),
        popover: {
          title: 'Navigation Sidebar',
          description: 'Use the sidebar to move between Projects, Tasks, Toolkit, Workspace, and your personal settings.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="nav-library"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureDashboardSidebarVisible()
        }),
        popover: {
          title: 'Projects',
          description: 'Open projects, browse pages, and jump into annotation work.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="nav-tasks"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureDashboardSidebarVisible()
        }),
        popover: {
          title: 'Tasks',
          description: 'Create, assign, and track work across projects.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="nav-toolkit"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureSidebarSectionExpanded('Toolkit')
        }),
        popover: {
          title: 'Toolkit',
          description: 'Manage datasets and shared resources such as labels, tags, dictionaries, and codecs.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="nav-workspace"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureSidebarSectionExpanded('Workspace')
        }),
        popover: {
          title: 'Workspace',
          description: 'Configure workspace defaults, members, and incoming or outgoing requests.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="nav-settings"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureSidebarSectionExpanded('Settings')
        }),
        popover: {
          title: 'User Settings',
          description: 'Manage your profile, invitations, notifications, security, and onboarding reset.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="search-button"]',
        onHighlightStarted: withHookAction(async () => {
          await ensureDashboardSidebarVisible()
        }),
        popover: {
          title: 'Search & Commands',
          description: 'Press Cmd/Ctrl + K to open global search and quick actions, including restarting this page tour.',
          side: 'right',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'tasks-index',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/tasks',
    onFinish: () => {
      const closeButton = document.querySelector<HTMLButtonElement>('[data-tour="task-form-close"]')
      closeButton?.click()
    },
    steps: () => [
      {
        element: '[data-tour="tasks-panel"]',
        popover: {
          title: 'Tasks Overview',
          description: 'View tasks as a table or board, filter results, and run bulk actions.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tasks-search"]',
        popover: {
          title: 'Task Filters',
          description: 'Narrow tasks by text, status, assignee, and other filters.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tasks-new"]',
        popover: {
          title: 'Create a Task',
          description: 'Click Next to open the task form. We will review key fields without saving.',
          side: 'left',
          align: 'center',
          onNextClick: withNextAction(async () => {
            await clickAndWait('[data-tour="tasks-new"]', '[data-tour="task-form-title"]')
          })
        }
      },
      {
        element: '[data-tour="task-form-title"]',
        popover: {
          title: 'Task Basics',
          description: 'Set title, description, status, priority, and due date. No changes are saved during this tour.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="task-form-assignees"]',
        popover: {
          title: 'Assignees',
          description: 'Add assignees so the right people can see and complete the task.',
          side: 'left',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'labels-index',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/labels',
    steps: () => [
      {
        element: '[data-tour="labels-panel"]',
        popover: {
          title: 'Label Sets',
          description: 'Label sets define the annotation categories used in your projects.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="labels-search"]',
        popover: {
          title: 'Search and Filter',
          description: 'Search by name and filter to quickly find the set you need.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="labels-new"]',
        popover: {
          title: 'Create Label Set',
          description: 'Create a new label set, or open an existing one to edit.',
          side: 'left',
          align: 'center'
        }
      }
    ]
  },

  {
    id: 'labels-builder',
    major: 'dashboard',
    autoStart: true,
    matches: path => matchers.labelsBuilder.test(path),
    steps: () => [
      {
        element: '[data-tour="label-builder-header"]',
        popover: {
          title: 'Builder Actions',
          description: 'Use these actions to save, import or export JSON, and edit metadata.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="label-builder-sidebar"]',
        popover: {
          title: 'Label Tree',
          description: 'Build the label hierarchy here: add, reorder, and group labels.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="label-builder-editor"]',
        popover: {
          title: 'Mapping Options',
          description: 'Configure mappings, colors, text behavior, container rules, and constraints for the selected label.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="label-builder-preview"]',
        popover: {
          title: 'Live Preview',
          description: 'Preview how the selected label configuration behaves in the editor.',
          side: 'left',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'tag-sets-index',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/tag-sets',
    steps: () => [
      {
        element: '[data-tour="tag-sets-panel"]',
        popover: {
          title: 'Tag Sets',
          description: 'Tag sets provide reusable tag hierarchies for organization and filtering.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tag-sets-search"]',
        popover: {
          title: 'Filter Tag Sets',
          description: 'Search by name or filter to find a tag set quickly.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tag-sets-new"]',
        popover: {
          title: 'New Tag Set',
          description: 'Create a new tag set, or open one to edit its structure.',
          side: 'left',
          align: 'center'
        }
      }
    ]
  },

  {
    id: 'tag-sets-builder',
    major: 'dashboard',
    autoStart: true,
    matches: path => matchers.tagSetBuilder.test(path),
    steps: () => [
      {
        element: '[data-tour="tag-builder-header"]',
        popover: {
          title: 'Builder Actions',
          description: 'Save, import or export, optimize colors, and manage metadata.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tag-builder-sidebar"]',
        popover: {
          title: 'Tag Tree',
          description: 'Create nested tags and organize the hierarchy.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="tag-builder-editor"]',
        popover: {
          title: 'Tag Configuration',
          description: 'Edit the selected tag title, description, color, and ordering settings.',
          side: 'left',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'virtual-keyboards-index',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/virtual-keyboard',
    steps: () => [
      {
        element: '[data-tour="vk-panel"]',
        popover: {
          title: 'Virtual Keyboards',
          description: 'Virtual keyboards provide custom input layouts for transcription and editing.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="vk-search"]',
        popover: {
          title: 'Find Layouts',
          description: 'Search and filter to locate existing keyboard layouts.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="vk-new"]',
        popover: {
          title: 'New Keyboard',
          description: 'Create a new layout, or open an existing one to edit.',
          side: 'left',
          align: 'center'
        }
      }
    ]
  },

  {
    id: 'virtual-keyboards-builder',
    major: 'dashboard',
    autoStart: true,
    matches: path => matchers.keyboardBuilder.test(path),
    steps: () => [
      {
        element: '[data-tour="vk-builder-header"]',
        popover: {
          title: 'Layout Actions',
          description: 'Save, import or export, and access advanced keyboard actions.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="vk-builder-sidebar"]',
        popover: {
          title: 'Layout Settings',
          description: 'Set name, tags, and grid size for this layout.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="vk-builder-grid"]',
        popover: {
          title: 'Grid Builder',
          description: 'Add keys on the grid, then drag and resize them to shape the layout.',
          side: 'left',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'codecs-index',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/codecs',
    steps: () => [
      {
        element: '[data-tour="codecs-panel"]',
        popover: {
          title: 'Codecs',
          description: 'Codecs define allowed characters and validation rules for text input.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="codecs-search"]',
        popover: {
          title: 'Search and Filter',
          description: 'Search by name and filter by tags to find a codec.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="codecs-new"]',
        popover: {
          title: 'New Codec',
          description: 'Create a new codec, or open one to edit and validate.',
          side: 'left',
          align: 'center'
        }
      }
    ]
  },

  {
    id: 'codecs-builder',
    major: 'dashboard',
    autoStart: true,
    matches: path => matchers.codecBuilder.test(path),
    steps: () => [
      {
        element: '[data-tour="codec-builder-header"]',
        popover: {
          title: 'Codec Actions',
          description: 'Save, generate characters, validate, and open additional codec actions.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="codec-builder-input"]',
        popover: {
          title: 'Character Input',
          description: 'Add characters manually or use helper tools to populate the set.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="codec-builder-table"]',
        popover: {
          title: 'Character Table',
          description: 'Review all characters, code points, and validation results in one place.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'workspace-general',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/workspace/settings',
    steps: () => [
      {
        element: '[data-tour="workspace-general-panel"]',
        popover: {
          title: 'Workspace Settings',
          description: 'Configure workspace-wide settings and defaults.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="workspace-general-presets"]',
        popover: {
          title: 'Default Presets',
          description: 'Set default codec, label set, and tag set for newly created projects.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="workspace-danger-zone"]',
        includeIf: () => !!document.querySelector('[data-tour="workspace-danger-zone"]'),
        popover: {
          title: 'Danger Zone',
          description: 'Sensitive actions such as leaving or deleting the workspace appear here, depending on your role.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'workspace-members',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/workspace/settings/members',
    steps: () => [
      {
        element: '[data-tour="workspace-members-panel"]',
        popover: {
          title: 'Members',
          description: 'See current members and their workspace roles.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="workspace-members-search"]',
        popover: {
          title: 'Member Search',
          description: 'Find members by name, username, or email.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="workspace-members-invite"]',
        includeIf: () => !!document.querySelector('[data-tour="workspace-members-invite"]'),
        popover: {
          title: 'Invite Members',
          description: 'If you have permission, invite new members from here.',
          side: 'left',
          align: 'center'
        }
      }
    ]
  },

  {
    id: 'workspace-requests',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/workspace/settings/requests',
    steps: () => [
      {
        element: '[data-tour="workspace-requests-incoming"]',
        popover: {
          title: 'Incoming Requests',
          description: 'Review incoming transfer requests and approve or reject them.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="workspace-requests-outgoing"]',
        popover: {
          title: 'Outgoing Requests',
          description: 'Track requests you sent and cancel them when needed.',
          side: 'bottom',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'settings-profile',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/settings',
    steps: () => [
      {
        element: '[data-tour="settings-profile-card"]',
        popover: {
          title: 'Profile',
          description: 'View and manage your personal profile information.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="settings-profile-edit"]',
        popover: {
          title: 'Edit Profile',
          description: 'Switch to edit mode to update your profile details.',
          side: 'left',
          align: 'center'
        }
      },
      {
        element: '[data-tour="settings-tour-reset"]',
        popover: {
          title: 'Reset Tours',
          description: 'Reset onboarding progress to replay all tours from the beginning.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'settings-invitations',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/settings/invitations',
    steps: () => [
      {
        element: '[data-tour="settings-invitations-panel"]',
        popover: {
          title: 'Workspace Invitations',
          description: 'All pending workspace invitations are listed here.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="settings-invitations-list"]',
        includeIf: () => !!document.querySelector('[data-tour="settings-invitations-list"]'),
        popover: {
          title: 'Invitation Actions',
          description: 'Accept or decline invitations.',
          side: 'bottom',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'settings-notifications',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/settings/notifications',
    steps: () => [
      {
        element: '[data-tour="settings-notifications-permission"]',
        popover: {
          title: 'Desktop Permission',
          description: 'Enable browser notifications if your browser supports them.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="settings-notifications-matrix"]',
        popover: {
          title: 'Notification Matrix',
          description: 'Choose how each event notifies you: email, desktop, and in-app.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'settings-security',
    major: 'dashboard',
    autoStart: true,
    matches: path => path === '/settings/security',
    steps: () => [
      {
        element: '[data-tour="settings-security-password"]',
        popover: {
          title: 'Password Management',
          description: 'Open account security settings in Keycloak to change your password.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="settings-security-delete"]',
        popover: {
          title: 'Account Deletion',
          description: 'Open Keycloak account deletion. This action is permanent.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'editor-layout',
    major: 'editor',
    autoStart: true,
    matches: (path, ctx) => path === '/editor' && (ctx?.editorMode ?? 'layout') !== 'text',
    onFinish: () => {
      dispatchOnboardingEvent('larex:onboarding:close-editor-filter-popover')
    },
    steps: () => [
      {
        popover: {
          title: 'Editor Layout Mode',
          description: 'This tour covers layout annotation tools and workflows.',
          side: 'top',
          align: 'center'
        }
      },
      {
        element: '[data-tour="editor-left-sidebar"]',
        popover: {
          title: 'Page List',
          description: 'Select pages, switch projects, and see filtered results.',
          side: 'right',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-page-filter-button"]',
        popover: {
          title: 'Page Filter Popover',
          description: 'Click Next to open the page filter builder.',
          side: 'right',
          align: 'start',
          onNextClick: withNextAction(async () => {
            dispatchOnboardingEvent('larex:onboarding:open-editor-filter-popover')
            await waitForVisibleElement('[data-tour="editor-page-filter-add"]')
          })
        }
      },
      {
        element: '[data-tour="editor-page-filter-add"]',
        onHighlightStarted: withHookAction(async () => {
          dispatchOnboardingEvent('larex:onboarding:open-editor-filter-popover')
          await waitForVisibleElement('[data-tour="editor-page-filter-add"]')
        }),
        popover: {
          title: 'Add Filters as Needed',
          description: 'Search the list, add only the criteria you need, and remove rows when you are done. Changes apply automatically.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-toolbar"]',
        popover: {
          title: 'Toolbar',
          description: 'Choose the complete mode and view from one grouped selector, then use the editing tools alongside it.',
          side: 'bottom',
          align: 'center'
        }
      },
      {
        element: '[data-tour="editor-right-sidebar"]',
        onHighlightStarted: withHookAction(() => {
          dispatchOnboardingEvent('larex:onboarding:prepare-editor-right-sidebar')
        }),
        popover: {
          title: 'Layout Sidebar',
          description: 'Click Next to expand core sidebar sections for a quick walkthrough.',
          side: 'left',
          align: 'start',
          onNextClick: withNextAction(async () => {
            dispatchOnboardingEvent('larex:onboarding:prepare-editor-right-sidebar')
            dispatchOnboardingEvent('larex:onboarding:expand-layout-panels')
            await waitForVisibleElement('[data-tour="editor-layout-structure-panel"]')
          })
        }
      },
      {
        element: '[data-tour="editor-layout-structure-panel"]',
        popover: {
          title: 'Structure',
          description: 'Browse regions and lines, manage visibility, and adjust selection.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-layout-reading-order-panel"]',
        popover: {
          title: 'Reading Order',
          description: 'Set the reading sequence for regions and text lines.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-layout-metadata-panel"]',
        popover: {
          title: 'Metadata',
          description: 'Edit metadata for the document, page, or selected element.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-layout-tasks-panel"]',
        popover: {
          title: 'Tasks Panel',
          description: 'View and complete page-related subtasks without leaving the editor.',
          side: 'left',
          align: 'start'
        }
      }
    ]
  },

  {
    id: 'editor-text',
    major: 'editor',
    autoStart: true,
    matches: (path, ctx) => path === '/editor' && (ctx?.editorMode ?? 'layout') === 'text',
    steps: () => [
      {
        element: '[data-tour="editor-mode-tabs"]',
        popover: {
          title: 'Text Mode',
          description: 'The combined selector groups Canvas and List under Text. Click Next to enter Text mode.',
          side: 'top',
          align: 'end',
          onNextClick: withNextAction(async () => {
            await ensureEditorMode('text')
            dispatchOnboardingEvent('larex:onboarding:prepare-editor-right-sidebar')
            await waitForVisibleElement('[data-tour="editor-text-sidebar"]')
          })
        }
      },
      {
        element: '[data-tour="editor-text-sidebar"]',
        onHighlightStarted: withHookAction(() => {
          dispatchOnboardingEvent('larex:onboarding:prepare-editor-right-sidebar')
        }),
        popover: {
          title: 'Text Sidebar',
          description: 'This sidebar contains text settings, diff options, and text filters.',
          side: 'left',
          align: 'start',
          onNextClick: withNextAction(async () => {
            dispatchOnboardingEvent('larex:onboarding:prepare-editor-right-sidebar')
            dispatchOnboardingEvent('larex:onboarding:expand-text-panels')
            await waitForVisibleElement('[data-tour="editor-text-settings-panel"]')
          })
        }
      },
      {
        element: '[data-tour="editor-text-settings-panel"]',
        popover: {
          title: 'Text Settings',
          description: 'Adjust cutout padding, text size, and list layout.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-text-filter-panel"]',
        popover: {
          title: 'Text Filters',
          description: 'Filter text by index and confidence to focus review.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="editor-textline-list-toolbar"]',
        popover: {
          title: 'List View',
          description: 'Search, sort, filter, reorder where allowed, and edit text variants.',
          side: 'top',
          align: 'start'
        }
      }
    ]
  }
]

export const onboardingTours = tours

export function getTourDefinition(tourId: TourId): OnboardingTourDefinition | undefined {
  return tours.find(tour => tour.id === tourId)
}

function normalizeContextPath(path: string): string {
  const stripped = path.split('?')[0]?.split('#')[0] ?? path
  const withLeadingSlash = stripped.startsWith('/') ? stripped : `/${stripped}`
  if (withLeadingSlash.length <= 1) return '/'
  return withLeadingSlash.replace(/\/+$/, '')
}

export function resolveContextTourId(path: string, ctx: OnboardingContext = {}): TourId | null {
  const normalizedPath = normalizeContextPath(path)
  const matched = tours.find(tour => tour.matches(normalizedPath, ctx))
  return matched?.id ?? null
}
