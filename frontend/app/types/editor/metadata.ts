import type { RegionKind } from '@/models/editor/region'
import type {
  BaselineMetadataFormState,
  DocumentMetadataFormState,
  GenericRegionMetadataFormState,
  GlyphMetadataFormState,
  PageMetadataFormState,
  TextLineMetadataFormState,
  TextRegionMetadataFormState,
  WordMetadataFormState
} from '@/utils/editor/metadata-schema'

export type MetadataApplyPayload
  = | { target: 'document', data: DocumentMetadataFormState }
    | { target: 'page', data: PageMetadataFormState }
    | { target: 'textRegion', elementId: string, data: TextRegionMetadataFormState }
    | { target: 'genericRegion', elementId: string, data: GenericRegionMetadataFormState }
    | { target: 'textLine', elementId: string, data: TextLineMetadataFormState }
    | { target: 'word', elementId: string, data: WordMetadataFormState }
    | { target: 'glyph', elementId: string, data: GlyphMetadataFormState }
    | { target: 'baseline', elementId: string, data: BaselineMetadataFormState }

export type RegionKindChangePayload = {
  regionId: string
  newKind: RegionKind
}
