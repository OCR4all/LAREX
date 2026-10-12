// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, ref, watch } from 'vue'
import { parse } from 'vue/compiler-sfc'
import ts from 'typescript'
import { ContextMenuContent, ContextMenuItem, ContextMenuRoot, ContextMenuTrigger } from 'reka-ui'
import editorSource from '../index.vue?raw'

// Exercise the production capture handler with the same internally managed
// context menu used by UContextMenu, without mounting the entire editor.
const source = parse(editorSource).descriptor.scriptSetup!.content
const ast = ts.createSourceFile('editor.ts', source, ts.ScriptTarget.Latest, true)
const handler = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'handleEditorKeyDown')!
const compiled = ts.transpileModule(handler.getText(ast), {}).outputText

describe('editor context menu Escape dismissal', () => {
  it('removes the whole menu and preserves selection after an automatic open', async () => {
    const contextMenuOpen = ref(false)
    const items = ref(['Text region'])
    const onKeyDown = vi.fn()
    const handleKeyDown = new Function(
      'contextMenuOpen', 'editorCommands', 'isCanvasInteractionBlocked',
      'regionCommentOpen', 'editorStore', 'props', 'editorFollow', 'editorInteractions',
      `${compiled}; return handleEditorKeyDown`
    )(
      contextMenuOpen, { closeContextMenu: () => { items.value = [] } }, { value: false },
      { value: false }, { activeCanvasId: 'canvas' }, { canvasId: 'canvas' },
      { handleLocalInteraction: vi.fn() }, { onKeyDown }
    )
    const host = document.createElement('div')
    document.body.append(host)
    const app = createApp({
      setup() {
        watch(contextMenuOpen, (open) => {
          if (!open) items.value = []
        })
        return () => h(ContextMenuRoot, {
          'onUpdate:open': (open: boolean) => { contextMenuOpen.value = open }
        }, () => [
          h(ContextMenuTrigger, { asChild: true }, () => h('canvas')),
          h(ContextMenuContent, {}, () => items.value.map(item => h(ContextMenuItem, {}, () => item)))
        ])
      }
    })
    window.addEventListener('keydown', handleKeyDown, true)
    app.mount(host)
    try {
      host.querySelector('canvas')!.dispatchEvent(new MouseEvent('contextmenu', {
        bubbles: true, cancelable: true, clientX: 100, clientY: 100
      }))
      await vi.waitFor(() => expect(document.querySelector('[role="menu"]')).not.toBeNull())
      expect(contextMenuOpen.value).toBe(true)

      document.querySelector('[role="menu"]')!.dispatchEvent(new KeyboardEvent('keydown', {
        key: 'Escape', bubbles: true, cancelable: true
      }))
      await nextTick()
      await vi.waitFor(() => expect(document.querySelector('[role="menu"]')).toBeNull())
      expect(contextMenuOpen.value).toBe(false)
      expect(onKeyDown).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('keydown', handleKeyDown, true)
      app.unmount()
      host.remove()
    }
  })
})
