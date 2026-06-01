import { describe, expect, it } from 'vitest'
import type { PageData } from '@/stores/editor/types'
import { resolveAdjacentPageId } from './page-navigation'

function createPage(id: string): PageData {
  return {
    id,
    projectId: 'project-1',
    label: id,
    imageVariants: [],
    xmlFiles: []
  }
}

describe('resolveAdjacentPageId', () => {
  it('navigates to adjacent pages within the available subset', () => {
    const allPages = [createPage('p1'), createPage('p2'), createPage('p3')]

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: [allPages[0], allPages[2]],
      currentPageId: 'p1',
      direction: 'next'
    })).toBe('p3')

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: [allPages[0], allPages[2]],
      currentPageId: 'p3',
      direction: 'prev'
    })).toBe('p1')
  })

  it('finds the next available page when the current page is outside the subset', () => {
    const allPages = [createPage('p1'), createPage('p2'), createPage('p3'), createPage('p4')]

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: [allPages[0], allPages[3]],
      currentPageId: 'p2',
      direction: 'next'
    })).toBe('p4')

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: [allPages[0], allPages[3]],
      currentPageId: 'p3',
      direction: 'prev'
    })).toBe('p1')
  })

  it('stops at subset boundaries', () => {
    const allPages = [createPage('p1'), createPage('p2')]

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: allPages,
      currentPageId: 'p2',
      direction: 'next'
    })).toBeNull()

    expect(resolveAdjacentPageId({
      allPages,
      availablePages: allPages,
      currentPageId: 'p1',
      direction: 'prev'
    })).toBeNull()
  })
})
