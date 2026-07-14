import type { LabelSet } from '@/models/editor/labels'
import type { RegionLabelConflictRegion } from '@/utils/editor/region-label-conflicts'
import { findRegionLabelConflicts } from '@/utils/editor/region-label-conflicts'

export function useRegionLabelConflicts(
  regions: MaybeRefOrGetter<RegionLabelConflictRegion[] | null | undefined>,
  labelSet: MaybeRefOrGetter<LabelSet | null | undefined>
) {
  const summary = computed(() => findRegionLabelConflicts(toValue(regions), toValue(labelSet)))
  const groups = computed(() => summary.value.groups)
  const regionIds = computed(() => summary.value.regionIds)
  const totalRegions = computed(() => summary.value.totalRegions)

  return {
    summary,
    groups,
    regionIds,
    totalRegions
  }
}
