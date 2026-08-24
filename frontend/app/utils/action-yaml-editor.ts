import { indentWithTab } from '@codemirror/commands'
import type { Extension } from '@codemirror/state'
import { highlightWhitespace, keymap } from '@codemirror/view'

export const actionYamlIndentKeymap = keymap.of([indentWithTab])

export function actionYamlWhitespaceExtension(visible: boolean): Extension {
  return visible ? highlightWhitespace() : []
}
