import type { RegionKind } from '@/models/editor/region'
import type {
  BaselineMetadataFormState,
  DocumentMetadataFormState,
  GenericRegionMetadataFormState,
  PageMetadataFormState,
  TextLineMetadataFormState,
  TextRegionMetadataFormState
} from '@/utils/editor/metadata-schema'

export type MetadataApplyPayload
  = | { target: 'document', data: DocumentMetadataFormState }
    | { target: 'page', data: PageMetadataFormState }
    | { target: 'textRegion', elementId: string, data: TextRegionMetadataFormState }
    | { target: 'genericRegion', elementId: string, data: GenericRegionMetadataFormState }
    | { target: 'textLine', elementId: string, data: TextLineMetadataFormState }
    | { target: 'baseline', elementId: string, data: BaselineMetadataFormState }

export type RegionKindChangePayload = {
  regionId: string
  newKind: RegionKind
}
