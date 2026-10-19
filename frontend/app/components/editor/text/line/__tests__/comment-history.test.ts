import { describe, expect, it, vi } from 'vitest'
import { parse } from 'vue/compiler-sfc'
import { computed, ref, triggerRef } from 'vue'
import ts from 'typescript'
import { Commander } from '@/commands/editor/commander'
import { UpdateTextlineCommentCommand } from '@/commands/editor/update-textline-comment-command'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { TextLine } from '@/models/editor/text'
import { Polygon } from '@/models/editor/geometry'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { serializeFullTextComment } from '../../shared/full-text-comments'
import source from '../list.vue?raw'
import canvasSource from '../../../index.vue?raw'
import fullTextSource from '../../full-editor.vue?raw'

const ast = ts.createSourceFile('list.ts', parse(source).descriptor.scriptSetup!.content, ts.ScriptTarget.Latest, true)
const handler = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'handleUpdateTextlineComment')!
const compiled = ts.transpileModule(handler.getText(ast), {}).outputText

function compileHandler(source: string, name: string): string {
  const ast = ts.createSourceFile('editor.ts', parse(source).descriptor.scriptSetup!.content, ts.ScriptTarget.Latest, true)
  const handler = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === name)!
  return `${ts.transpileModule(handler.getText(ast), {}).outputText}; return ${name}`
}

function setup() {
  const { session } = createMockSession(createTestDocument({ regions: [createTestTextRegion({
    id: 'region-1', textLines: [new TextLine({ id: 'line-1', coords: new Polygon([]), comments: 'Original comment' })]
  })] }))
  const commander = new Commander()
  const context = createTestContext(session)
  const editable = ref(true)
  const update = new Function(
    'isCanvasEditable', 'effectiveCanvasId', 'getEditorSession', 'findTextLineRecursive', 'triggerRef',
    'getTextViewRuntimeControls', 'editorStore', 'UpdateTextlineCommentCommand', 'createTextViewCommandContext',
    'serializeFullTextComment', `${compiled}; return handleUpdateTextlineComment`
  )(
    editable, ref(session.canvasId), () => session, findTextLineRecursive, triggerRef,
    () => ({ commander }), {}, UpdateTextlineCommentCommand, () => context, serializeFullTextComment
  )
  const comment = () => findTextLineRecursive(session.document.value!.page.regions, 'line-1')!.textLine.comments
  const selected = computed(() => findTextLineRecursive(session.document.value!.page.regions, 'line-1')!.textLine)
  const flushPendingCommit = vi.fn()
  const canvasUpdate = new Function(
    'selectedTextlinePolygon', 'isCanvasWritable', 'flushPendingCorrectionCommit', 'serializeFullTextComment',
    'canvasControls', 'UpdateTextlineCommentCommand', 'getCommandContext',
    compileHandler(canvasSource, 'updateSelectedTextlineComment')
  )(selected, editable, flushPendingCommit, serializeFullTextComment, { commander }, UpdateTextlineCommentCommand, () => context)
  const fullTextUpdate = new Function(
    'isEditable', 'effectiveCanvasId', 'serializeFullTextComment', 'fullTextLineById',
    'getTextViewRuntimeControls', 'editorStore', 'UpdateTextlineCommentCommand', 'createTextViewCommandContext',
    compileHandler(fullTextSource, 'updateTextlineComment')
  )(
    editable, ref(session.canvasId), serializeFullTextComment, computed(() => new Map([['line-1', selected.value]])),
    () => ({ commander }), {}, UpdateTextlineCommentCommand, () => context
  )
  return { update, canvasUpdate, fullTextUpdate, flushPendingCommit, comment, commander, context, editable }
}

describe('List view comment history', () => {
  it('supports undo and redo after saving and clearing a multiline comment', () => {
    const { update, comment, commander, context } = setup()
    update('line-1', '  First line\nSecond line  ')
    expect(comment()).toBe('First line\nSecond line')
    expect(commander.canUndo()).toBe(true)
    commander.undo(context)
    expect(comment()).toBe('Original comment')
    commander.redo(context)
    expect(comment()).toBe('First line\nSecond line')
    update('line-1', ' \n ')
    expect(comment()).toBeUndefined()
    commander.undo(context)
    expect(comment()).toBe('First line\nSecond line')
  })

  it('does not record unchanged comments or edits to missing lines', () => {
    const { update, commander } = setup()
    update('line-1', '  Original comment  ')
    update('missing-line', 'A comment')
    expect(commander.canUndo()).toBe(false)
  })

  it('does not change comments in read-only mode', () => {
    const { update, canvasUpdate, fullTextUpdate, comment, commander, editable } = setup()
    editable.value = false
    update('line-1', 'Changed')
    canvasUpdate('Changed in Canvas')
    fullTextUpdate('line-1', 'Changed in Full Text')
    expect(comment()).toBe('Original comment')
    expect(commander.canUndo()).toBe(false)
  })

  it('shares undo/redo across List, Canvas, and Full Text saves', () => {
    const { update, canvasUpdate, fullTextUpdate, flushPendingCommit, comment, commander, context } = setup()
    update('line-1', 'List comment')
    canvasUpdate('  Canvas\ncomment  ')
    expect(flushPendingCommit).toHaveBeenCalledOnce()
    fullTextUpdate('line-1', 'Full Text comment')
    for (const previous of ['Canvas\ncomment', 'List comment', 'Original comment']) {
      expect(commander.undo(context)).toBe(true)
      expect(comment()).toBe(previous)
    }
    for (const next of ['List comment', 'Canvas\ncomment', 'Full Text comment']) {
      expect(commander.redo(context)).toBe(true)
      expect(comment()).toBe(next)
    }
  })
})
