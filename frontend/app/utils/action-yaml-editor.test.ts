// @vitest-environment happy-dom

import { EditorState, type Extension } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { basicSetup } from 'codemirror'
import { describe, expect, it } from 'vitest'
import { actionYamlIndentKeymap, actionYamlWhitespaceExtension } from './action-yaml-editor'

function createEditor(doc: string, extensions: Extension = [basicSetup, actionYamlIndentKeymap]) {
  const parent = document.createElement('div')
  document.body.append(parent)
  return new EditorView({
    state: EditorState.create({ doc, extensions }),
    parent
  })
}

describe('Action YAML editor behavior', () => {
  it('keeps focus in the editor and indents with Tab', () => {
    const view = createEditor('root:\nchild: value')
    view.dispatch({ selection: { anchor: 6 } })
    view.focus()

    const event = new KeyboardEvent('keydown', {
      key: 'Tab',
      code: 'Tab',
      bubbles: true,
      cancelable: true
    })
    view.contentDOM.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(view.hasFocus).toBe(true)
    expect(view.state.doc.toString()).toBe('root:\n  child: value')
    view.destroy()
  })

  it('outdents with Shift+Tab', () => {
    const view = createEditor('root:\n  child: value')
    view.dispatch({ selection: { anchor: 8 } })

    view.contentDOM.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Tab',
      code: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true
    }))

    expect(view.state.doc.toString()).toBe('root:\nchild: value')
    view.destroy()
  })

  it('renders distinct markings for spaces and tabs only when enabled', () => {
    const hiddenView = createEditor('a b\tc', actionYamlWhitespaceExtension(false))
    expect(hiddenView.dom.querySelector('.cm-highlightSpace')).toBeNull()
    expect(hiddenView.dom.querySelector('.cm-highlightTab')).toBeNull()
    hiddenView.destroy()

    const visibleView = createEditor('a b\tc', actionYamlWhitespaceExtension(true))
    expect(visibleView.dom.querySelector('.cm-highlightSpace')).not.toBeNull()
    expect(visibleView.dom.querySelector('.cm-highlightTab')).not.toBeNull()
    visibleView.destroy()
  })
})
