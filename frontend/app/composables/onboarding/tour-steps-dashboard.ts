import type { DriveStep } from 'driver.js'

/**
 * Dashboard onboarding tour steps.
 * Bump DASHBOARD_TOUR_VERSION in use-onboarding.ts when modifying these steps.
 */
export const dashboardTourSteps: DriveStep[] = [
  {
    popover: {
      title: 'Welcome to LAREX 👋',
      description:
        'LAREX is a tool for layout analysis and region extraction on historical documents. '
        + 'This quick tour will walk you through the main areas of the dashboard.',
      side: 'top',
      align: 'center'
    }
  },

  {
    element: '#dashboard',
    popover: {
      title: 'Navigation Sidebar',
      description:
        'This is your central navigation hub. From here you can access all major sections: '
        + 'the Library, Tasks, Utilities, Workspace settings, and your personal settings.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="nav-library"]',
    popover: {
      title: 'Library',
      description:
        'The Library is your home base. Here you manage projects, upload page images, '
        + 'search and filter by tags, star your favorites, and share with collaborators.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="nav-tasks"]',
    popover: {
      title: 'Tasks',
      description:
        'Track and manage annotation tasks. Create tasks, assign them to team members, '
        + 'and monitor progress across your projects.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="nav-utilities"]',
    popover: {
      title: 'Utilities',
      description:
        'Configure reusable resources for your annotation workflow:<br><br>'
        + '<b>Labels</b> — Define region and line types for layout annotation<br>'
        + '<b>Tags</b> — Organize and categorize projects<br>'
        + '<b>Virtual Keyboards</b> — Custom character input for transcription<br>'
        + '<b>Codecs</b> — Define allowed character sets for text validation',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="nav-workspace"]',
    popover: {
      title: 'Workspace',
      description:
        'Manage your workspace settings, invite team members, and handle join requests. '
        + 'Workspaces let you collaborate with your team on shared projects.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="nav-settings"]',
    popover: {
      title: 'Settings',
      description:
        'Customize your profile, configure notification preferences, manage invitations, '
        + 'and update your security settings.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="search-button"]',
    popover: {
      title: 'Search & Commands',
      description:
        'Quickly search for projects, pages, and actions. You can also open this '
        + 'with the keyboard shortcut <b>⌘ /</b> (or <b>Ctrl /</b>).<br><br>'
        + 'The command palette also lets you create new projects, navigate between sections, '
        + 'and perform quick actions.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '#library',
    popover: {
      title: 'Project Management',
      description:
        'This is where your projects live. Create new projects, upload document page images, '
        + 'search and filter the list, and click on a project to view its pages.',
      side: 'left',
      align: 'start'
    }
  },

  {
    popover: {
      title: 'You\'re all set! 🎉',
      description:
        'That covers the main areas of the dashboard. You can restart this tour anytime '
        + 'from the search / command palette (<b>⌘ /</b>).<br><br>'
        + 'When you open the editor for the first time, you\'ll get a separate tour for the annotation tools.',
      side: 'top',
      align: 'center'
    }
  }
]
