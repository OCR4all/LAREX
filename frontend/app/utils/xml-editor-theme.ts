import type { Extension } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags } from '@lezer/highlight'

const editorSelectionStyles = {
  // CodeMirror paints its selection behind the line, so the line fill must let it show through.
  '.cm-activeLine, .cm-activeLineGutter': {
    backgroundColor: 'color-mix(in srgb, var(--ui-primary) 5%, transparent)'
  },
  '.cm-selectionBackground, &.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'var(--editor-selection-bg)'
  }
}

const xmlEditorLightTheme = EditorView.theme({
  '&': {
    '--editor-selection-bg': 'color-mix(in srgb, var(--ui-bg), var(--color-brand-blue) 20%)',
    'color': 'var(--ui-text)',
    'background': 'var(--ui-bg)'
  },
  '.cm-content': {
    caretColor: 'var(--ui-text)'
  },
  '.cm-cursor, .cm-dropCursor': {
    borderLeftColor: 'var(--ui-text)'
  },
  '.cm-gutters': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text-muted)',
    borderRight: '1px solid var(--ui-border)'
  },
  ...editorSelectionStyles,
  '.cm-searchMatch': {
    background: 'color-mix(in srgb, var(--ui-warning) 25%, transparent)',
    outline: '1px solid color-mix(in srgb, var(--ui-warning) 70%, var(--ui-border))'
  },
  '.cm-searchMatch.cm-searchMatch-selected': {
    background: 'var(--ui-warning)',
    color: 'var(--ui-bg)'
  },
  '.cm-panels': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text)',
    borderTop: '1px solid var(--ui-border)'
  },
  '.cm-panel': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text)'
  },
  '.cm-panel.cm-search': {
    padding: '8px',
    borderBottom: '1px solid var(--ui-border)'
  },
  '.cm-search': {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.4rem',
    alignItems: 'center'
  },
  '.cm-search label': {
    color: 'var(--ui-text)'
  },
  '.cm-search input[type="checkbox"]': {
    accentColor: 'var(--ui-primary)'
  },
  '.cm-search .cm-textfield, .cm-search input[type="text"]': {
    background: 'var(--ui-bg)',
    color: 'var(--ui-text)',
    border: '1px solid var(--ui-border)',
    borderRadius: '4px',
    padding: '4px 8px'
  },
  '.cm-search .cm-button, .cm-search button, .cm-panel.cm-search button': {
    background: 'var(--ui-bg)',
    color: 'var(--ui-text)',
    border: '1px solid var(--ui-border)',
    borderRadius: '4px',
    padding: '4px 10px',
    fontWeight: '600'
  },
  '.cm-search .cm-button:hover, .cm-search button:hover, .cm-panel.cm-search button:hover': {
    background: 'color-mix(in srgb, var(--ui-bg) 74%, var(--ui-primary) 26%)',
    color: 'var(--ui-text)'
  },
  '.cm-search .cm-button:disabled, .cm-search button:disabled, .cm-panel.cm-search button:disabled': {
    background: 'var(--ui-bg-elevated)',
    borderColor: 'var(--ui-border)',
    color: 'var(--ui-text-muted)',
    opacity: 1
  }
}, { dark: false })

const xmlEditorDarkTheme = EditorView.theme({
  '&': {
    '--editor-selection-bg': 'color-mix(in srgb, var(--ui-bg), var(--color-brand-blue) 36%)',
    'color': 'var(--ui-text)',
    'background': 'var(--ui-bg)'
  },
  '.cm-content': {
    caretColor: 'var(--ui-text)'
  },
  '.cm-cursor, .cm-dropCursor': {
    borderLeftColor: 'var(--ui-text)'
  },
  '.cm-gutters': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text-muted)',
    borderRight: '1px solid var(--ui-border)'
  },
  ...editorSelectionStyles,
  '.cm-searchMatch': {
    background: 'color-mix(in srgb, var(--ui-warning) 25%, transparent)',
    outline: '1px solid color-mix(in srgb, var(--ui-warning) 70%, var(--ui-border))'
  },
  '.cm-searchMatch.cm-searchMatch-selected': {
    background: 'var(--ui-warning)',
    color: 'var(--ui-bg)'
  },
  '.cm-panels': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text)',
    borderTop: '1px solid var(--ui-border)'
  },
  '.cm-panel': {
    background: 'var(--ui-bg-elevated)',
    color: 'var(--ui-text)'
  },
  '.cm-panel.cm-search': {
    padding: '8px',
    borderBottom: '1px solid var(--ui-border)'
  },
  '.cm-search': {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.4rem',
    alignItems: 'center'
  },
  '.cm-search label': {
    color: 'var(--ui-text)'
  },
  '.cm-search input[type="checkbox"]': {
    accentColor: 'var(--ui-primary)'
  },
  '.cm-search .cm-textfield, .cm-search input[type="text"]': {
    background: 'var(--ui-bg)',
    color: 'var(--ui-text)',
    border: '1px solid var(--ui-border)',
    borderRadius: '4px',
    padding: '4px 8px'
  },
  '.cm-search .cm-button, .cm-search button, .cm-panel.cm-search button': {
    background: 'var(--ui-bg)',
    color: 'var(--ui-text)',
    border: '1px solid var(--ui-border)',
    borderRadius: '4px',
    padding: '4px 10px',
    fontWeight: '600'
  },
  '.cm-search .cm-button:hover, .cm-search button:hover, .cm-panel.cm-search button:hover': {
    background: 'color-mix(in srgb, var(--ui-bg) 70%, var(--ui-primary) 30%)',
    color: 'var(--ui-text)'
  },
  '.cm-search .cm-button:disabled, .cm-search button:disabled, .cm-panel.cm-search button:disabled': {
    background: 'var(--ui-bg-elevated)',
    borderColor: 'var(--ui-border)',
    color: 'var(--ui-text-muted)',
    opacity: 1
  }
}, { dark: true })

const xmlEditorLightHighlight = HighlightStyle.define([
  { tag: tags.tagName, color: '#0c4a6e' },
  { tag: tags.attributeName, color: '#7c2d12' },
  { tag: [tags.string, tags.attributeValue], color: '#166534' },
  { tag: tags.comment, color: '#4b5563', fontStyle: 'italic' },
  { tag: tags.processingInstruction, color: '#6d28d9' },
  { tag: [tags.angleBracket, tags.bracket], color: '#334155' }
])

const xmlEditorDarkHighlight = HighlightStyle.define([
  { tag: tags.tagName, color: '#7ad0ff' },
  { tag: tags.attributeName, color: '#ffb88d' },
  { tag: [tags.string, tags.attributeValue], color: '#9fe58c' },
  { tag: tags.comment, color: '#aaa7a2', fontStyle: 'italic' },
  { tag: tags.processingInstruction, color: '#c8a7ff' },
  { tag: [tags.angleBracket, tags.bracket], color: '#c2c0bb' }
])

export function buildXmlEditorTheme(isDark: boolean): Extension {
  return [
    isDark ? xmlEditorDarkTheme : xmlEditorLightTheme,
    syntaxHighlighting(isDark ? xmlEditorDarkHighlight : xmlEditorLightHighlight)
  ]
}
