import type { DriveStep } from 'driver.js'

/**
 * Editor onboarding tour steps.
 * Bump EDITOR_TOUR_VERSION in use-onboarding.ts when modifying these steps.
 */
export const editorTourSteps: DriveStep[] = [
  {
    popover: {
      title: 'Welcome to the Editor 🖊️',
      description:
        'The editor is where you annotate document pages — drawing regions, textlines, '
        + 'and baselines, and transcribing text. Let\'s walk through the key areas.',
      side: 'top',
      align: 'center'
    }
  },

  {
    element: '[data-tour="editor-left-sidebar"]',
    popover: {
      title: 'Page Navigation',
      description:
        'Browse and filter document pages in the left sidebar. Select a page to load it '
        + 'onto the canvas for annotation. You can also switch between image variants here.',
      side: 'right',
      align: 'start'
    }
  },

  {
    element: '[data-tour="editor-toolbar"]',
    popover: {
      title: 'Toolbar',
      description:
        'The toolbar contains all your annotation tools. Use it to switch between drawing modes, '
        + 'undo/redo changes, adjust the view, and access more options.',
      side: 'bottom',
      align: 'center'
    }
  },

  {
    element: '[data-tour="region-tools"]',
    popover: {
      title: 'Region Tools',
      description:
        'Draw <b>regions</b> to define layout areas on the page — text blocks, images, '
        + 'marginalia, and more. Choose between polygon (freeform) or rectangle mode.',
      side: 'bottom',
      align: 'start'
    }
  },

  {
    element: '[data-tour="textline-tools"]',
    popover: {
      title: 'Textline Tools',
      description:
        'Draw <b>textlines</b> within regions for line-level annotation. '
        + 'In Textline view mode, a helper region is created automatically if needed.',
      side: 'bottom',
      align: 'start'
    }
  },

  {
    element: '[data-tour="cut-tools"]',
    popover: {
      title: 'Cut Tools',
      description:
        'Split regions or textlines using line, polygon, or rectangle cuts. '
        + 'Useful for correcting segmentation errors or refining layout boundaries.',
      side: 'bottom',
      align: 'start'
    }
  },

  {
    element: '[data-tour="view-mode-tabs"]',
    popover: {
      title: 'View Modes',
      description:
        'Switch between <b>Default</b>, <b>Textline</b>, and <b>Baseline</b> views '
        + 'to focus on different annotation layers. Each view mode enables specialized drawing tools.',
      side: 'bottom',
      align: 'center'
    }
  },

  {
    element: '[data-tour="undo-redo"]',
    popover: {
      title: 'Undo & Redo',
      description:
        'Revert or reapply changes with full history support. Click the history icon '
        + 'to jump to any point in your editing session.',
      side: 'bottom',
      align: 'center'
    }
  },

  {
    element: '[data-tour="editor-mode-tabs"]',
    popover: {
      title: 'Layout & Text Modes',
      description:
        '<b>Layout mode</b> — Draw and edit spatial annotations (regions, textlines, baselines).<br>'
        + '<b>Text mode</b> — Transcribe and edit text content for each textline.<br><br>'
        + 'Use the lock icon to toggle per-panel or global mode switching.',
      side: 'top',
      align: 'end'
    }
  },

  {
    element: '.dockview-vue',
    popover: {
      title: 'Canvas',
      description:
        'The main workspace where annotation happens. Open multiple pages in separate tabs '
        + 'and arrange them in docked panels. Zoom, pan, and interact with annotations directly.',
      side: 'top',
      align: 'center'
    }
  },

  {
    element: '[data-tour="editor-right-sidebar"]',
    popover: {
      title: 'Inspector Panel',
      description:
        'View and edit properties of selected elements:<br><br>'
        + '<b>Structure</b> — Region/textline tree view<br>'
        + '<b>Reading Order</b> — Set element ordering<br>'
        + '<b>Metadata</b> — View element properties<br>'
        + '<b>Tasks</b> — Manage associated tasks<br>'
        + '<b>Settings</b> — Adjust editor behavior',
      side: 'left',
      align: 'start'
    }
  },

  {
    element: '[aria-label="Save"]',
    popover: {
      title: 'Save',
      description:
        'Save your current annotations. Use the dropdown for additional actions like '
        + 'exporting or viewing version history.',
      side: 'left',
      align: 'center'
    }
  },

  {
    element: '[data-tour="editor-help"]',
    popover: {
      title: 'Keyboard Shortcuts',
      description:
        'Click here to view all available keyboard shortcuts for faster editing. '
        + 'Master the shortcuts to significantly speed up your annotation workflow.',
      side: 'bottom',
      align: 'end'
    }
  },

  {
    popover: {
      title: 'Ready to annotate! ✏️',
      description:
        'You now know the key areas of the editor. Start by selecting a page from the '
        + 'left sidebar, then use the toolbar to draw regions and textlines.<br><br>'
        + 'You can restart this tour anytime from the toolbar tour button or the command palette.',
      side: 'top',
      align: 'center'
    }
  }
]
