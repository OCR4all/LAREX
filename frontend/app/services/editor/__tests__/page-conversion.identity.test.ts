import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { snapshotFromPageDto, snapshotToPageDto } from '@/utils/editor/collaboration-page-doc'
import type { PageDto } from '@/types/page-dto'
import { convertPageDtoToPcGts, convertPcGtsToPageDto } from '../page-conversion.service'
import { completePageDto } from './fixtures/complete-page-dto'
import { jsonDifferences, missingFields, walkPageDto, withoutFields } from './fixtures/page-dto-schema'
import {
  BACKEND_GOLDEN_DIFFERENCES,
  BACKEND_ONLY_FIELDS,
  COLLABORATION_DIFFERENCES,
  COMPLETE_FIXTURE_DIFFERENCES
} from './fixtures/page-conversion-known-differences'

const BACKEND_GOLDENS = fileURLToPath(new URL('../../../../../backend/src/test/resources/page-corpus-goldens', import.meta.url))

function roundTrip(dto: unknown): PageDto {
  return convertPcGtsToPageDto(convertPageDtoToPcGts(structuredClone(dto) as PageDto))
}

function collaborationRoundTrip(dto: unknown): PageDto {
  return snapshotToPageDto(JSON.parse(JSON.stringify(snapshotFromPageDto(structuredClone(dto) as PageDto))))
}

function goldenDtos(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry)
    if (statSync(path).isDirectory()) return goldenDtos(path)
    return entry === 'dto.json' ? [path] : []
  }).sort()
}

const goldens = goldenDtos(BACKEND_GOLDENS).map(path => ({
  document: relative(BACKEND_GOLDENS, path).replace(/\/dto\.json$/, ''),
  dto: JSON.parse(readFileSync(path, 'utf8')) as unknown
}))

describe('PageDto → editor model → PageDto', () => {
  it('uses a fixture that populates every PageDto field', () => {
    const walk = walkPageDto(completePageDto())

    expect(missingFields(walk)).toEqual([])
    expect([...walk.unknown]).toEqual([])
  })

  it('round-trips the complete fixture with only the known differences', () => {
    const dto = completePageDto()

    expect(jsonDifferences(dto, roundTrip(dto))).toEqual(COMPLETE_FIXTURE_DIFFERENCES)
  })

  it('finds backend golden DTOs to round-trip', () => {
    expect(goldens.length).toBeGreaterThan(0)
  })

  it('knows every field the backend sends', () => {
    const unknown = new Set(goldens.flatMap(({ dto }) => [...walkPageDto(dto).unknown]))

    expect([...unknown].sort()).toEqual([...BACKEND_ONLY_FIELDS].sort())
  })

  it.each(goldens)('round-trips the backend golden DTO of $document with only the known differences', ({ document, dto }) => {
    const sent = withoutFields(dto, new Set(BACKEND_ONLY_FIELDS))

    expect(jsonDifferences(sent, roundTrip(dto))).toEqual(BACKEND_GOLDEN_DIFFERENCES[document] ?? [])
  })

  it.each([{ document: 'complete fixture', dto: completePageDto() as unknown }, ...goldens])(
    'keeps $document unchanged through the collaboration snapshot',
    ({ document, dto }) => {
      expect(jsonDifferences(dto, collaborationRoundTrip(dto))).toEqual(COLLABORATION_DIFFERENCES[document] ?? [])
    }
  )
})
