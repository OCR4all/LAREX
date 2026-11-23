import { describe, expect, it } from 'vitest'
import type { TagNode } from '@/types/tag-set'
import { createTagSetBuilder } from '@/composables/use-tag-set-builder'

function tag(id: string, children: TagNode[] = []): TagNode {
  return { id, title: id, description: '', color: '#123456', children }
}

function ids(nodes: TagNode[] | undefined): string[] {
  return (nodes ?? []).map(node => node.id)
}

describe('tag deletion undo', () => {
  it('restores a root tag with its children at its position', () => {
    const builder = createTagSetBuilder()
    builder.tags.value = [tag('a'), tag('b', [tag('b1'), tag('b2')]), tag('c')]
    builder.selectTag('b')

    const snapshot = builder.deleteTag('b')
    expect(snapshot?.descendantCount).toBe(2)
    expect(ids(builder.tags.value)).toEqual(['a', 'c'])
    expect(builder.activeTag.value).toBeNull()

    builder.restoreTag(snapshot!)
    expect(ids(builder.tags.value)).toEqual(['a', 'b', 'c'])
    expect(ids(builder.tags.value[1]?.children)).toEqual(['b1', 'b2'])
    expect(builder.activeTag.value?.id).toBe('b')
  })

  it('restores a nested tag under its parent once', () => {
    const builder = createTagSetBuilder()
    builder.tags.value = [tag('p', [tag('x'), tag('y'), tag('z')])]

    const snapshot = builder.deleteTag('y')
    expect(ids(builder.tags.value[0]?.children)).toEqual(['x', 'z'])

    builder.restoreTag(snapshot!)
    builder.restoreTag(snapshot!)
    expect(ids(builder.tags.value[0]?.children)).toEqual(['x', 'y', 'z'])
    expect(builder.expandedIds.value.has('p')).toBe(true)
  })

  it('falls back to the root level when the parent is gone', () => {
    const builder = createTagSetBuilder()
    builder.tags.value = [tag('a'), tag('p', [tag('child')])]

    const childSnapshot = builder.deleteTag('child')
    builder.deleteTag('p')
    builder.restoreTag(childSnapshot!)

    expect(ids(builder.tags.value)).toEqual(['a', 'child'])
  })

  it('returns null for an unknown tag', () => {
    const builder = createTagSetBuilder()
    expect(builder.deleteTag('missing')).toBeNull()
  })
})
